package eu.darken.bluemusic.common.serialization

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File
import java.time.Duration
import java.util.UUID

class SerializerTest : BaseTest() {

    private val json = Json

    // region DurationSerializer

    @Serializable
    private data class DurationWrapper(@Serializable(with = DurationSerializer::class) val value: Duration)

    @Test
    fun `Duration - round trip PT1H30M`() {
        val original = DurationWrapper(Duration.parse("PT1H30M"))
        val encoded = json.encodeToString(DurationWrapper.serializer(), original)
        json.decodeFromString(DurationWrapper.serializer(), encoded) shouldBe original
    }

    @Test
    fun `Duration - round trip PT0S`() {
        val original = DurationWrapper(Duration.ZERO)
        val encoded = json.encodeToString(DurationWrapper.serializer(), original)
        json.decodeFromString(DurationWrapper.serializer(), encoded) shouldBe original
    }

    @Test
    fun `Duration - round trip P1DT2H3M4S`() {
        val original = DurationWrapper(Duration.parse("P1DT2H3M4S"))
        val encoded = json.encodeToString(DurationWrapper.serializer(), original)
        json.decodeFromString(DurationWrapper.serializer(), encoded) shouldBe original
    }

    // endregion

    // region UUIDSerializer

    @Serializable
    private data class UUIDWrapper(@Serializable(with = UUIDSerializer::class) val value: UUID)

    @Test
    fun `UUID - round trip random`() {
        val original = UUIDWrapper(UUID.randomUUID())
        val encoded = json.encodeToString(UUIDWrapper.serializer(), original)
        json.decodeFromString(UUIDWrapper.serializer(), encoded) shouldBe original
    }

    @Test
    fun `UUID - round trip min value`() {
        val original = UUIDWrapper(UUID(0L, 0L))
        val encoded = json.encodeToString(UUIDWrapper.serializer(), original)
        json.decodeFromString(UUIDWrapper.serializer(), encoded) shouldBe original
    }

    @Test
    fun `UUID - round trip max value`() {
        val original = UUIDWrapper(UUID(-1L, -1L))
        val encoded = json.encodeToString(UUIDWrapper.serializer(), original)
        json.decodeFromString(UUIDWrapper.serializer(), encoded) shouldBe original
    }

    // endregion

    // region FileSerializer

    @Serializable
    private data class FileWrapper(@Serializable(with = FileSerializer::class) val value: File)

    @Test
    fun `File - round trip absolute path`() {
        val original = FileWrapper(File("/tmp/test/file.txt"))
        val encoded = json.encodeToString(FileWrapper.serializer(), original)
        json.decodeFromString(FileWrapper.serializer(), encoded) shouldBe original
    }

    @Test
    fun `File - round trip relative path`() {
        val original = FileWrapper(File("relative/path.txt"))
        val encoded = json.encodeToString(FileWrapper.serializer(), original)
        json.decodeFromString(FileWrapper.serializer(), encoded) shouldBe original
    }

    @Test
    fun `File - round trip path with spaces`() {
        val original = FileWrapper(File("/tmp/path with spaces/file name.txt"))
        val encoded = json.encodeToString(FileWrapper.serializer(), original)
        json.decodeFromString(FileWrapper.serializer(), encoded) shouldBe original
    }

    // endregion
}
