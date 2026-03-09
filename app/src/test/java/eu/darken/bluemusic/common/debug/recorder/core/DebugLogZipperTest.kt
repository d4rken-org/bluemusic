package eu.darken.bluemusic.common.debug.recorder.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile

class DebugLogZipperTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var zipper: DebugLogZipper

    @BeforeEach
    fun setup() {
        zipper = DebugLogZipper(mockk(relaxed = true))
    }

    private fun createLogDir(name: String = "session1", vararg files: Pair<String, String>): File {
        val logDir = File(tempDir, name).also { it.mkdirs() }
        for ((fileName, content) in files) {
            File(logDir, fileName).writeText(content)
        }
        return logDir
    }

    @Nested
    inner class ZipCreation {
        @Test
        fun `zip creates sibling zip file`() {
            val logDir = createLogDir(files = arrayOf("core.log" to "log content"))

            val result = zipper.zip(logDir)

            result.exists() shouldBe true
            result.name shouldBe "session1.zip"
            result.parentFile shouldBe tempDir
        }

        @Test
        fun `zip removes temp file on success`() {
            val logDir = createLogDir(files = arrayOf("core.log" to "log content"))

            zipper.zip(logDir)

            File(tempDir, "session1.zip.tmp").exists() shouldBe false
        }

        @Test
        fun `zip result contains correct entries`() {
            val logDir = createLogDir(
                files = arrayOf(
                    "core.log" to "core log content",
                    "extra.log" to "extra content",
                )
            )

            val result = zipper.zip(logDir)

            ZipFile(result).use { zf ->
                zf.entries().toList().map { it.name } shouldContainExactlyInAnyOrder listOf("core.log", "extra.log")
            }
        }

        @Test
        fun `zip overwrites existing zip`() {
            val logDir = createLogDir(files = arrayOf("core.log" to "new content"))
            val existingZip = File(tempDir, "session1.zip")
            existingZip.writeText("old zip data")

            val result = zipper.zip(logDir)

            result.exists() shouldBe true
            ZipFile(result).use { zf ->
                zf.entries().toList().map { it.name } shouldBe listOf("core.log")
            }
        }
    }

    @Nested
    inner class ErrorCases {
        @Test
        fun `zip throws on empty logDir`() {
            val logDir = File(tempDir, "empty_session").also { it.mkdirs() }

            shouldThrow<IllegalArgumentException> {
                zipper.zip(logDir)
            }
        }

        @Test
        fun `zip throws when logDir cannot list files`() {
            val logDir = File(tempDir, "nonexistent")

            shouldThrow<IllegalStateException> {
                zipper.zip(logDir)
            }
        }

        @Test
        fun `zip cleans temp file on failure`() {
            val logDir = File(tempDir, "empty_session").also { it.mkdirs() }

            try {
                zipper.zip(logDir)
            } catch (_: IllegalArgumentException) {
                // expected
            }

            File(tempDir, "empty_session.zip.tmp").exists() shouldBe false
        }
    }
}
