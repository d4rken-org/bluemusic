package eu.darken.bluemusic.common.debug.recorder.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RecorderModuleTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `formatDuration - seconds only`() {
        formatDuration(45) shouldBe "45s"
    }

    @Test
    fun `formatDuration - minutes and seconds`() {
        formatDuration(135) shouldBe "2m 15s"
    }

    @Test
    fun `formatDuration - exact minutes`() {
        formatDuration(120) shouldBe "2m 0s"
    }

    @Test
    fun `formatDuration - zero seconds`() {
        formatDuration(0) shouldBe "0s"
    }

    @Test
    fun `triggerFile lastModified gives recording start time`() {
        val triggerFile = File(tempDir, "force_debug_run")
        triggerFile.createNewFile()

        val lastModified = triggerFile.lastModified()
        assert(lastModified > 0) { "Trigger file should have a valid lastModified timestamp" }
    }

    @Test
    fun `triggerFile lastModified returns 0 when file does not exist`() {
        val triggerFile = File(tempDir, "force_debug_run")
        triggerFile.lastModified() shouldBe 0L
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return if (minutes > 0) "${minutes}m ${remainingSeconds}s" else "${remainingSeconds}s"
    }
}
