package eu.darken.bluemusic.upgrade.core

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.datastore.createValue
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.serialization.SerializationModule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.io.File
import java.time.Instant

/**
 * The FOSS supporter record is create-only-if-absent: the sponsor-return heuristic can fire again
 * for someone who is already a supporter (the recurring-donation button, or a stale entitlement
 * replay), and a rewrite would move their "supporter since" date — the one the status screen shows.
 *
 * Driven through a real DataStore on a temp file via [FossCache]'s test seam, because the guarantee
 * is the store transaction's, not the caller's.
 */
class UpgradeRepoFossPersistTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    // One store scope per test: the DataStore keeps its own actor alive on it.
    private var storeScope: CoroutineScope? = null

    @AfterEach
    fun teardown() {
        storeScope?.cancel()
        storeScope = null
    }

    private class Harness(val cache: FossCache, val repo: UpgradeRepoFoss)

    // Unique file name per test method: DataStore forbids two active instances on the same file.
    private fun newStoreScope(): CoroutineScope =
        CoroutineScope(Dispatchers.IO + SupervisorJob()).also { storeScope = it }

    private fun TestScope.buildHarness(storeName: String): Harness {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = newStoreScope(),
            produceFile = { File(tempDir, "$storeName.preferences_pb") },
        )
        val cache = FossCache(dataStore, SerializationModule().json())
        val repo = UpgradeRepoFoss(
            // backgroundScope: the repo's shareIn keeps a collector alive for the scope's lifetime.
            scope = backgroundScope,
            fossCache = cache,
            webpageTool = mockk<WebpageTool>(relaxed = true),
        )
        return Harness(cache, repo)
    }

    @Test
    fun `persistUpgrade keeps an existing record`() = runTest {
        val harness = buildHarness("existing_record")
        // The EPOCH date is the regression payload: a rewrite would move the "supporter since" date
        // an existing supporter is being shown.
        harness.cache.upgrade.value(
            FossUpgrade(
                upgradedAt = Instant.EPOCH,
                upgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
            )
        )

        harness.repo.persistUpgrade() shouldBe false

        harness.cache.upgrade.value() shouldBe FossUpgrade(
            upgradedAt = Instant.EPOCH,
            upgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
        )
        harness.repo.upgradeInfo.first().apply {
            isPro shouldBe true
            upgradedAt shouldBe Instant.EPOCH
        }
    }

    @Test
    fun `persistUpgrade creates on an empty store`() = runTest {
        val harness = buildHarness("empty_store")
        harness.cache.upgrade.value() shouldBe null

        // Plain, untruncated: the InstantSerializer is ISO-8601 and preserves nanoseconds, so the
        // stored value can be compared against a nanosecond-precision bracket.
        val before = Instant.now()
        harness.repo.persistUpgrade() shouldBe true
        val after = Instant.now()

        val created = harness.cache.upgrade.value()
        created shouldNotBe null
        created!!.upgradeType shouldBe FossUpgrade.Type.GITHUB_SPONSORS
        (created.upgradedAt >= before) shouldBe true
        (created.upgradedAt <= after) shouldBe true

        // Boolean-proven keep: immune to a timestamp collision between the two writes.
        harness.repo.persistUpgrade() shouldBe false
        harness.cache.upgrade.value() shouldBe created
    }

    @Test
    fun `concurrent persists elect exactly one creator`() = runTest {
        val harness = buildHarness("concurrent")
        harness.cache.upgrade.value() shouldBe null

        val before = Instant.now()
        val gate = CompletableDeferred<Unit>()
        val racers = List(2) {
            async(Dispatchers.IO) {
                gate.await()
                harness.repo.persistUpgrade()
            }
        }
        gate.complete(Unit)
        val results = racers.awaitAll()
        val after = Instant.now()

        // Exactly one creator: the loser must report the record it found, not a second creation.
        results.sorted() shouldBe listOf(false, true)

        val record = harness.cache.upgrade.value()
        record shouldNotBe null
        record!!.upgradeType shouldBe FossUpgrade.Type.GITHUB_SPONSORS
        (record.upgradedAt >= before) shouldBe true
        (record.upgradedAt <= after) shouldBe true
    }

    @Test
    fun `an undecodable record counts as absent when fallback is enabled`() = runTest {
        // FossCache enables onErrorFallbackToDefault only on RELEASE builds, so persistUpgrade's
        // "an undecodable record counts as absent" caveat is release-only. This validates the
        // library behaviour that caveat depends on, without touching the build-type-conditional
        // production config.
        val dataStore = PreferenceDataStoreFactory.create(
            scope = newStoreScope(),
            produceFile = { File(tempDir, "undecodable.preferences_pb") },
        )
        val keyName = "foss.upgrade"
        dataStore.edit { prefs -> prefs[stringPreferencesKey(keyName)] = "{ not valid json }" }

        val stored = dataStore.createValue<FossUpgrade?>(
            key = keyName,
            defaultValue = null,
            json = SerializationModule().json(),
            onErrorFallbackToDefault = true,
        )
        stored.value() shouldBe null

        val before = Instant.now()
        val updated = stored.update { existing ->
            existing ?: FossUpgrade(
                upgradedAt = Instant.now(),
                upgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
            )
        }
        val after = Instant.now()

        updated.old shouldBe null

        val record = stored.value()
        record shouldNotBe null
        record!!.upgradeType shouldBe FossUpgrade.Type.GITHUB_SPONSORS
        (record.upgradedAt >= before) shouldBe true
        (record.upgradedAt <= after) shouldBe true
    }
}
