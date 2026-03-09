package eu.darken.bluemusic.common.debug.recorder.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RecorderModuleTest {

    @Nested
    inner class DefaultState {
        @Test
        fun `shouldRecord is false`() {
            RecorderModule.State().shouldRecord shouldBe false
        }

        @Test
        fun `isRecording is false`() {
            RecorderModule.State().isRecording shouldBe false
        }

        @Test
        fun `currentLogDir is null`() {
            RecorderModule.State().currentLogDir shouldBe null
        }

        @Test
        fun `recordingStartedAt is zero`() {
            RecorderModule.State().recordingStartedAt shouldBe 0L
        }

        @Test
        fun `currentLogPath is null`() {
            RecorderModule.State().currentLogPath shouldBe null
        }
    }

    @Nested
    inner class FindExistingSessionDir {
        @Test
        fun `returns null when no directories exist`(@TempDir tempDir: File) {
            val logDir = File(tempDir, "debug/logs")
            RecorderModule.findExistingSessionDir(listOf(logDir)) shouldBe null
        }

        @Test
        fun `returns null when log directory is empty`(@TempDir tempDir: File) {
            val logDir = File(tempDir, "debug/logs").also { it.mkdirs() }
            RecorderModule.findExistingSessionDir(listOf(logDir)) shouldBe null
        }

        @Test
        fun `returns null when session dir has no core log`(@TempDir tempDir: File) {
            val logDir = File(tempDir, "debug/logs").also { it.mkdirs() }
            File(logDir, "bluemusic_1.0_20260309T120000Z_abc12345").mkdirs()
            RecorderModule.findExistingSessionDir(listOf(logDir)) shouldBe null
        }

        @Test
        fun `returns null for non-bluemusic directories`(@TempDir tempDir: File) {
            val logDir = File(tempDir, "debug/logs").also { it.mkdirs() }
            val dir = File(logDir, "some_other_dir").also { it.mkdirs() }
            File(dir, "core.log").createNewFile()
            RecorderModule.findExistingSessionDir(listOf(logDir)) shouldBe null
        }

        @Test
        fun `finds existing session with core log`(@TempDir tempDir: File) {
            val logDir = File(tempDir, "debug/logs").also { it.mkdirs() }
            val sessionDir = File(logDir, "bluemusic_1.0_20260309T120000Z_abc12345").also { it.mkdirs() }
            File(sessionDir, "core.log").createNewFile()

            RecorderModule.findExistingSessionDir(listOf(logDir)) shouldBe sessionDir
        }

        @Test
        fun `returns most recent session when multiple exist`(@TempDir tempDir: File) {
            val logDir = File(tempDir, "debug/logs").also { it.mkdirs() }

            val older = File(logDir, "bluemusic_1.0_20260308T100000Z_abc12345").also { it.mkdirs() }
            File(older, "core.log").createNewFile()
            older.setLastModified(1000L)

            val newer = File(logDir, "bluemusic_1.0_20260309T120000Z_abc12345").also { it.mkdirs() }
            File(newer, "core.log").createNewFile()
            newer.setLastModified(2000L)

            RecorderModule.findExistingSessionDir(listOf(logDir)) shouldBe newer
        }

        @Test
        fun `returns null when most recent session has no core log`(@TempDir tempDir: File) {
            val logDir = File(tempDir, "debug/logs").also { it.mkdirs() }

            val withLog = File(logDir, "bluemusic_1.0_20260308T100000Z_abc12345").also { it.mkdirs() }
            File(withLog, "core.log").createNewFile()
            withLog.setLastModified(1000L)

            val withoutLog = File(logDir, "bluemusic_1.0_20260309T120000Z_abc12345").also { it.mkdirs() }
            withoutLog.setLastModified(2000L)

            // Only checks the most recent dir - if it has no core.log, returns null
            RecorderModule.findExistingSessionDir(listOf(logDir)) shouldBe null
        }

        @Test
        fun `searches across multiple log directories`(@TempDir tempDir: File) {
            val extDir = File(tempDir, "ext/debug/logs").also { it.mkdirs() }
            val cacheDir = File(tempDir, "cache/debug/logs").also { it.mkdirs() }

            val sessionDir = File(cacheDir, "bluemusic_1.0_20260309T120000Z_abc12345").also { it.mkdirs() }
            File(sessionDir, "core.log").createNewFile()

            RecorderModule.findExistingSessionDir(listOf(extDir, cacheDir)) shouldBe sessionDir
        }

        @Test
        fun `prefers first directory with a match`(@TempDir tempDir: File) {
            val extDir = File(tempDir, "ext/debug/logs").also { it.mkdirs() }
            val cacheDir = File(tempDir, "cache/debug/logs").also { it.mkdirs() }

            val extSession = File(extDir, "bluemusic_1.0_20260309T120000Z_abc12345").also { it.mkdirs() }
            File(extSession, "core.log").createNewFile()
            extSession.setLastModified(1000L)

            val cacheSession = File(cacheDir, "bluemusic_1.0_20260309T130000Z_abc12345").also { it.mkdirs() }
            File(cacheSession, "core.log").createNewFile()
            cacheSession.setLastModified(2000L)

            // Returns from first directory that has a match (ext), not the globally most recent
            RecorderModule.findExistingSessionDir(listOf(extDir, cacheDir)) shouldBe extSession
        }
    }
}
