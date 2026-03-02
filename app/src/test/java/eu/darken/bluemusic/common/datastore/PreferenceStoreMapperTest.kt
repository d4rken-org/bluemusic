package eu.darken.bluemusic.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PreferenceStoreMapperTest {

    // Use a real dispatcher scope — PreferenceStoreMapper uses runBlocking internally
    // which deadlocks with TestScope's test dispatcher.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterEach
    fun cleanup() {
        scope.cancel()
    }

    private fun createDataStore(tempDir: File): DataStore<Preferences> {
        val testFile = File(
            tempDir,
            "${PreferenceStoreMapperTest::class.java.simpleName}_${System.nanoTime()}.preferences_pb"
        )
        return PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { testFile },
        )
    }

    @Test
    fun `unknown key throws NotImplementedError`(@TempDir tempDir: File) {
        val testStore = createDataStore(tempDir)
        val value = testStore.createValue("known", true)
        val mapper = PreferenceStoreMapper(value)

        shouldThrow<NotImplementedError> {
            mapper.getBoolean("unknown", false)
        }
    }

    @Test
    fun `duplicate key registration causes lookup failure`(@TempDir tempDir: File) {
        val testStore = createDataStore(tempDir)
        val value1 = testStore.createValue("duplicate", true)
        val value2 = testStore.createValue("duplicate", false)
        val mapper = PreferenceStoreMapper(value1, value2)

        // singleOrNull returns null when multiple matches exist
        shouldThrow<NotImplementedError> {
            mapper.getBoolean("duplicate", false)
        }
    }

    @Test
    fun `getBoolean and putBoolean round-trip`(@TempDir tempDir: File) {
        val testStore = createDataStore(tempDir)
        val value = testStore.createValue("flag", true)
        val mapper = PreferenceStoreMapper(value)

        mapper.getBoolean("flag", false) shouldBe true

        mapper.putBoolean("flag", false)
        mapper.getBoolean("flag", true) shouldBe false

        mapper.putBoolean("flag", true)
        mapper.getBoolean("flag", false) shouldBe true
    }

    @Test
    fun `getString and putString round-trip`(@TempDir tempDir: File) {
        val testStore = createDataStore(tempDir)
        val value = testStore.createValue<String?>("name", "default")
        val mapper = PreferenceStoreMapper(value)

        mapper.getString("name", null) shouldBe "default"

        mapper.putString("name", "updated")
        mapper.getString("name", null) shouldBe "updated"

        mapper.putString("name", null)
        mapper.getString("name", "fallback") shouldBe "default"
    }

    @Test
    fun `getStringSet throws NotImplementedError`() {
        val mapper = PreferenceStoreMapper()

        shouldThrow<NotImplementedError> {
            mapper.getStringSet("any", null)
        }
    }

    @Test
    fun `putStringSet throws NotImplementedError`() {
        val mapper = PreferenceStoreMapper()

        shouldThrow<NotImplementedError> {
            mapper.putStringSet("any", mutableSetOf("a"))
        }
    }

    @Test
    fun `getInt and putInt round-trip`(@TempDir tempDir: File) {
        val testStore = createDataStore(tempDir)
        val value = testStore.createValue("count", 42)
        val mapper = PreferenceStoreMapper(value)

        mapper.getInt("count", 0) shouldBe 42

        mapper.putInt("count", 99)
        mapper.getInt("count", 0) shouldBe 99
    }

    @Test
    fun `getLong and putLong round-trip`(@TempDir tempDir: File) {
        val testStore = createDataStore(tempDir)
        val value = testStore.createValue("timestamp", 1000L)
        val mapper = PreferenceStoreMapper(value)

        mapper.getLong("timestamp", 0L) shouldBe 1000L

        mapper.putLong("timestamp", 2000L)
        mapper.getLong("timestamp", 0L) shouldBe 2000L
    }

    @Test
    fun `getFloat and putFloat round-trip`(@TempDir tempDir: File) {
        val testStore = createDataStore(tempDir)
        val value = testStore.createValue("ratio", 1.5f)
        val mapper = PreferenceStoreMapper(value)

        mapper.getFloat("ratio", 0f) shouldBe 1.5f

        mapper.putFloat("ratio", 3.14f)
        mapper.getFloat("ratio", 0f) shouldBe 3.14f
    }
}
