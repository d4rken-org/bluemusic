package eu.darken.bluemusic.upgrade.core

import eu.darken.bluemusic.common.datastore.DataStoreValue
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class UpgradeRepoFossTest : BaseTest() {

    private val record = FossUpgrade(
        upgradedAt = Instant.EPOCH,
        upgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
    )

    private fun createUpgradeValue(cacheFlow: Flow<FossUpgrade?>) = mockk<DataStoreValue<FossUpgrade?>>().apply {
        every { flow } returns cacheFlow
        coEvery { update(any()) } returns DataStoreValue.Updated(old = null, new = record)
    }

    // Real dispatchers, not a test scheduler: the shareIn sharing coroutine and the collectors have
    // to actually interleave here, and the whole point is that a failure settles instead of hanging.
    private fun createRepo(appScope: CoroutineScope, upgradeValue: DataStoreValue<FossUpgrade?>) = UpgradeRepoFoss(
        scope = appScope,
        fossCache = mockk<FossCache>().apply { every { upgrade } returns upgradeValue },
        webpageTool = mockk(),
    )

    @Test
    fun `upgrade info maps the pro status and the foss type`() {
        UpgradeRepoFoss.Info(
            isPro = false,
            upgradedAt = null,
        ).apply {
            type shouldBe UpgradeRepo.Type.FOSS
            isPro shouldBe false
            // A local cache read is always conclusive: nothing is pending confirmation.
            isSettled shouldBe true
        }

        UpgradeRepoFoss.Info(
            isPro = true,
            upgradedAt = Instant.EPOCH,
            fossUpgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
        ).apply {
            isPro shouldBe true
            upgradedAt shouldBe Instant.EPOCH
            fossUpgradeType shouldBe FossUpgrade.Type.GITHUB_SPONSORS
        }
    }

    @Test
    fun `a failing cache read surfaces as a settled error Info instead of hanging`(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = createRepo(scope, createUpgradeValue(flow { throw IOException("cache broken") }))

            withTimeout(10_000) {
                repo.upgradeInfo.first().apply {
                    // Type and message: a bare non-null check would also pass on a swallow-and-wrap.
                    error.shouldBeInstanceOf<IOException>().message shouldBe "cache broken"
                    isPro shouldBe false
                    // The UI must be able to render this: an unsettled error is an endless spinner.
                    isSettled shouldBe true
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a late cache failure keeps the last known entitlement`(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = createRepo(scope, createUpgradeValue(flow {
                emit(record)
                throw IOException("cache broken later")
            }))

            withTimeout(10_000) {
                val infos = repo.upgradeInfo.take(2).toList()

                infos[0].apply {
                    isPro shouldBe true
                    error shouldBe null
                }
                // The entitlement we already saw must survive the read failure - a revoked Pro
                // status would kick a paying supporter back to the pitch.
                infos[1].apply {
                    isPro shouldBe true
                    error.shouldBeInstanceOf<IOException>()
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a successful persist revives an error-stuck upgradeInfo`(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            // First subscription fails, later ones read fine: the store recovered, but the shared
            // flow is still replaying the error Info to everyone.
            val subscriptions = AtomicInteger(0)
            val upgradeValue = createUpgradeValue(flow {
                if (subscriptions.getAndIncrement() == 0) throw IOException("cache broken")
                emit(record)
            })
            val repo = createRepo(scope, upgradeValue)
            // Default backoff (30s and up): the revival must NOT be the retry loop waking up.

            val received = Channel<UpgradeRepo.Info>(Channel.UNLIMITED)
            scope.launch { repo.upgradeInfo.collect { received.send(it) } }

            withTimeout(10_000) {
                received.receive().error.shouldBeInstanceOf<IOException>()

                // No explicit refresh() from the test: persist has to do the reviving itself,
                // otherwise the user's unlock never reaches the screen they are looking at.
                repo.persistUpgrade() shouldBe true

                received.receive().apply {
                    isPro shouldBe true
                    error shouldBe null
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `the automatic retry recovers once the store heals`(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val subscriptions = AtomicInteger(0)
            val upgradeValue = createUpgradeValue(flow {
                if (subscriptions.getAndIncrement() == 0) throw IOException("cache broken")
                emit(record)
            })
            val repo = createRepo(scope, upgradeValue)
            // Nobody is going to tap anything: the backoff loop itself has to bring the entitlement
            // back. Shrunk so the test doesn't sit out the shipped 30s first delay.
            repo.retryDelayMs = { 10L }

            withTimeout(10_000) {
                val infos = repo.upgradeInfo.take(2).toList()

                infos[0].error.shouldBeInstanceOf<IOException>()
                // No refresh(), no persist() - the automatic attempt after the backoff did this.
                infos[1].apply {
                    isPro shouldBe true
                    error shouldBe null
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `refresh recovers immediately instead of waiting out the backoff`(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val subscriptions = AtomicInteger(0)
            val upgradeValue = createUpgradeValue(flow {
                if (subscriptions.getAndIncrement() == 0) throw IOException("cache broken")
                emit(record)
            })
            val repo = createRepo(scope, upgradeValue)
            // Longer than the test could ever wait: if refresh() did not cancel the in-flight
            // backoff, this test would time out - which is exactly the old shape's dead window.
            repo.retryDelayMs = { 10 * 60_000L }

            val received = Channel<UpgradeRepo.Info>(Channel.UNLIMITED)
            scope.launch { repo.upgradeInfo.collect { received.send(it) } }

            withTimeout(10_000) {
                received.receive().error.shouldBeInstanceOf<IOException>()

                repo.refresh()

                received.receive().apply {
                    isPro shouldBe true
                    error shouldBe null
                }
            }
        } finally {
            scope.cancel()
        }
    }
}
