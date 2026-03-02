package eu.darken.bluemusic.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.bluemusic.common.serialization.InstantSerializer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException

class DataStoreValueSerializationTest {

    private fun createDataStore(scope: TestScope, tempDir: File): DataStore<Preferences> {
        val testFile = File(
            tempDir,
            "${DataStoreValueSerializationTest::class.java.simpleName}_${System.nanoTime()}.preferences_pb"
        )
        return PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { testFile },
        )
    }

    private val json = Json { encodeDefaults = true }

    private val jsonWithInstant = Json {
        encodeDefaults = true
        serializersModule = SerializersModule {
            contextual(InstantSerializer)
        }
    }

    @Serializable
    data class TestJson(
        val list: List<String> = listOf("1", "2"),
        val string: String = "",
        val boolean: Boolean = true,
        val float: Float = 1.0f,
        val int: Int = 1,
        val long: Long = 1L,
    )

    @Serializable
    enum class TestEnum {
        @SerialName("a") A,
        @SerialName("b") B,
    }

    @Test
    fun `reading and writing using manual reader and writer`(@TempDir tempDir: File) = runTest {
        val testStore = createDataStore(this, tempDir)

        val testData1 = TestJson(string = "teststring")
        val testData2 = TestJson(string = "update")

        testStore.createValue<TestJson?>(
            key = stringPreferencesKey("testKey"),
            reader = kotlinxSerializationReader(json, testData1),
            writer = kotlinxSerializationWriter(json),
        ).apply {
            flow.first() shouldBe testData1
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe testData1
                it!!.copy(string = "update")
            }

            flow.first() shouldBe testData2

            update {
                it shouldBe testData2
                null
            }

            flow.first() shouldBe testData1
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null
        }
    }

    @Test
    fun `reading and writing using autocreated reader and writer`(@TempDir tempDir: File) = runTest {
        val testStore = createDataStore(this, tempDir)

        val testData1 = TestJson(string = "teststring")
        val testData2 = TestJson(string = "update")

        testStore.createValue<TestJson?>(
            key = "testKey",
            defaultValue = testData1,
            json = json,
        ).apply {
            flow.first() shouldBe testData1
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null

            update {
                it shouldBe testData1
                it!!.copy(string = "update")
            }

            flow.first() shouldBe testData2

            update {
                it shouldBe testData2
                null
            }

            flow.first() shouldBe testData1
            testStore.data.first()[stringPreferencesKey(keyName)] shouldBe null
        }
    }

    @Test
    fun `enum serialization`(@TempDir tempDir: File) = runTest {
        val testStore = createDataStore(this, tempDir)

        val value = testStore.createValue("test.enum", TestEnum.A, json)

        value.flow.first() shouldBe TestEnum.A
        value.update { TestEnum.B }
        value.flow.first() shouldBe TestEnum.B
    }

    @Test
    fun `malformed JSON throws exception by default`(@TempDir tempDir: File) = runTest {
        val testStore = createDataStore(this, tempDir)
        val key = stringPreferencesKey("testKey")
        val defaultValue = TestJson(string = "default")

        testStore.edit { prefs -> prefs[key] = "{ invalid json }" }

        val dataStoreValue = testStore.createValue<TestJson?>(
            key = "testKey",
            defaultValue = defaultValue,
            json = json,
            onErrorFallbackToDefault = false,
        )

        shouldThrow<SerializationException> {
            dataStoreValue.flow.first()
        }
    }

    @Test
    fun `malformed JSON returns default when onErrorFallbackToDefault is true`(@TempDir tempDir: File) = runTest {
        val testStore = createDataStore(this, tempDir)
        val key = stringPreferencesKey("testKey")
        val defaultValue = TestJson(string = "default")

        testStore.edit { prefs -> prefs[key] = "{ invalid json }" }

        val dataStoreValue = testStore.createValue<TestJson?>(
            key = "testKey",
            defaultValue = defaultValue,
            json = json,
            onErrorFallbackToDefault = true,
        )

        dataStoreValue.flow.first() shouldBe defaultValue
    }

    @Test
    fun `invalid Instant payload returns default when onErrorFallbackToDefault is true`(@TempDir tempDir: File) =
        runTest {
            val testStore = createDataStore(this, tempDir)
            val key = stringPreferencesKey("testKey")

            testStore.edit { prefs -> prefs[key] = "\"not-a-valid-instant\"" }

            val dataStoreValue = testStore.createValue<Instant?>(
                key = "testKey",
                defaultValue = null,
                json = jsonWithInstant,
                onErrorFallbackToDefault = true,
            )

            dataStoreValue.flow.first() shouldBe null
        }

    @Test
    fun `invalid Instant payload throws when onErrorFallbackToDefault is false`(@TempDir tempDir: File) = runTest {
        val testStore = createDataStore(this, tempDir)
        val key = stringPreferencesKey("testKey")

        testStore.edit { prefs -> prefs[key] = "\"not-a-valid-instant\"" }

        val dataStoreValue = testStore.createValue<Instant?>(
            key = "testKey",
            defaultValue = null,
            json = jsonWithInstant,
            onErrorFallbackToDefault = false,
        )

        shouldThrow<DateTimeParseException> {
            dataStoreValue.flow.first()
        }
    }

    @Test
    fun `nullable serialized type write and read null`(@TempDir tempDir: File) = runTest {
        val testStore = createDataStore(this, tempDir)

        val value = testStore.createValue<TestJson?>(
            key = "testKey",
            defaultValue = null,
            json = json,
        )

        value.flow.first() shouldBe null

        val testData = TestJson(string = "written")
        value.value(testData)
        value.flow.first() shouldBe testData

        value.value(null)
        value.flow.first() shouldBe null
    }

    @Test
    fun `collection round-trip - set of strings`(@TempDir tempDir: File) = runTest {
        val testStore = createDataStore(this, tempDir)

        val value = testStore.createValue("test.set", emptySet<String>(), json)

        value.flow.first() shouldBe emptySet()

        val data = setOf("alpha", "beta", "gamma")
        value.value(data)
        value.flow.first() shouldBe data

        value.value(emptySet())
        value.flow.first() shouldBe emptySet()
    }

    @Test
    fun `collection round-trip - list of strings`(@TempDir tempDir: File) = runTest {
        val testStore = createDataStore(this, tempDir)

        val value = testStore.createValue("test.list", emptyList<String>(), json)

        value.flow.first() shouldBe emptyList()

        val data = listOf("one", "two", "three")
        value.value(data)
        value.flow.first() shouldBe data

        value.value(emptyList())
        value.flow.first() shouldBe emptyList()
    }

    @Test
    fun `Instant contextual serializer round-trip`(@TempDir tempDir: File) = runTest {
        val testStore = createDataStore(this, tempDir)

        val value = testStore.createValue<Instant?>("test.instant", null, jsonWithInstant)

        value.flow.first() shouldBe null

        val now = Instant.parse("2025-01-15T10:30:00Z")
        value.value(now)
        value.flow.first() shouldBe now

        value.value(null)
        value.flow.first() shouldBe null
    }
}
