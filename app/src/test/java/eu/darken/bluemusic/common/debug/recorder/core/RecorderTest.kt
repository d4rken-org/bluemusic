package eu.darken.bluemusic.common.debug.recorder.core

import eu.darken.bluemusic.common.debug.logging.Logging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.io.File
import java.io.IOException

/**
 * The REAL recorder, with the faults injected into the file it records to and into the logging its
 * own teardown emits — no double, no manual cleanup. Both directions have to hold: a start that
 * cannot open its log must leave nothing behind that claims to be recording, and a stop must run
 * every teardown step regardless of what the ones before it did, because the module commits
 * "stopped" state around it and a logger left installed keeps writing into a session everybody else
 * considers finished.
 *
 * Robolectric because the file logger writes through android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RecorderTest : BaseTest() {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Fails on every line it receives while armed — the loggers being torn down are receivers. */
    private class Saboteur : Logging.Logger {
        @Volatile
        var armed = false

        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            if (armed) throw IllegalStateException("Simulated logger failure")
        }
    }

    /**
     * The registry is global: a recorder or saboteur left installed by a FAILING assertion here keeps
     * receiving every line every later test in this process emits — the regression under test would
     * then cascade into unrelated failures instead of being the one thing that went red. Run
     * unconditionally, and after the assertions, so it can never substitute for them.
     */
    private fun restoreRegistry(recorder: Recorder, loggersBefore: List<Logging.Logger>) {
        runCatching { runBlocking { recorder.stop() } }
        (Logging.loggers - loggersBefore.toSet()).forEach { Logging.remove(it) }
    }

    @Test
    fun `a start that cannot open its log leaves nothing behind`() {
        val loggersBefore = Logging.loggers
        // A directory where the log file belongs: the writer cannot be opened for it.
        val logFile = File(tempFolder.newFolder("session"), "core.log").also { it.mkdirs() }
        val recorder = Recorder()

        try {
            shouldThrow<IOException> { runBlocking { recorder.start(logFile) } }

            // Nothing is published until the writer is live, or the recorder claims to record into a
            // file that receives nothing — and stopRecorder() would report a session with no log.
            recorder.isRecording shouldBe false
            recorder.path.shouldBeNull()
            Logging.loggers shouldBe loggersBefore
        } finally {
            // A start that unexpectedly succeeded installed a FileLogger that nothing else removes.
            restoreRegistry(recorder, loggersBefore)
        }
    }

    @Test
    fun `a stop whose logging fails still tears the recorder down`() {
        val loggersBefore = Logging.loggers
        val logFile = File(tempFolder.newFolder("session"), "core.log")
        val recorder = Recorder()
        val saboteur = Saboteur()

        try {
            runBlocking { recorder.start(logFile) }
            Logging.install(saboteur)
            saboteur.armed = true

            // The first failure is reported, not swallowed — but only after everything ran.
            shouldThrow<IllegalStateException> { runBlocking { recorder.stop() } }

            saboteur.armed = false
            Logging.remove(saboteur)

            // Nothing was uninstalled by hand in between: this is stop()'s own guarantee.
            Logging.loggers shouldBe loggersBefore
            recorder.isRecording shouldBe false
            recorder.path.shouldBeNull()
            // The end marker is written by the file logger's own stop, so the writer was closed too
            // instead of being left open on a session that is reported as finished.
            logFile.readText() shouldContain "=== END ==="
        } finally {
            // Disarmed first: a saboteur still armed would throw out of the very cleanup below.
            saboteur.armed = false
            restoreRegistry(recorder, loggersBefore)
        }
    }
}
