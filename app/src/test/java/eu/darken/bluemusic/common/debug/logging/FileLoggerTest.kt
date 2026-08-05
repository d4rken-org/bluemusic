package eu.darken.bluemusic.common.debug.logging

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
import java.io.OutputStream

/**
 * A start that cannot open its writer used to be swallowed: the recorder then reported a successful
 * start for a recording that could never produce a log file, and it removed the log file on the way
 * out — which included the log of a session being RESUMED, so a restart that failed threw away the
 * recording the user had already collected.
 *
 * The failures are injected through [FileLogger.streamFactory] rather than through permission bits:
 * [FileLogger.start] repairs the file permissions itself before opening the writer, and a root test
 * runner ignores them entirely — an injected fault that the code under test can undo is no fault.
 *
 * Robolectric because the logger writes through android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class FileLoggerTest : BaseTest() {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Fails in the window that decides the delete: the file exists by the time the open is attempted. */
    private val unopenableStream: (File) -> OutputStream = { throw IOException("writer cannot be opened") }

    /** The other window: the writer opens, and the header it writes never reaches the file. */
    private val unwritableStream: (File) -> OutputStream = {
        object : OutputStream() {
            override fun write(b: Int) = throw IOException("header cannot be written")
        }
    }

    @Test
    fun `a failed start keeps a log it did not create`() {
        val logFile = File(tempFolder.newFolder("resumed"), "core.log")
        logFile.writeText("=== BEGIN ===\nthe recording the user already made\n")

        shouldThrow<IOException> {
            FileLogger(logFile).apply { streamFactory = unopenableStream }.start()
        }

        logFile.exists() shouldBe true
        logFile.readText() shouldContain "the recording the user already made"
    }

    @Test
    fun `a failed start removes the log file it created itself`() {
        val logFile = File(tempFolder.newFolder("fresh"), "core.log")

        shouldThrow<IOException> {
            FileLogger(logFile).apply { streamFactory = unopenableStream }.start()
        }

        // Nothing was recorded into it and nothing else owns it: leaving the empty file behind makes
        // the session look like a recording that produced an empty log.
        logFile.exists() shouldBe false
    }

    @Test
    fun `a header write that fails removes the log file this start created`() {
        val logFile = File(tempFolder.newFolder("header"), "core.log")

        shouldThrow<IOException> {
            FileLogger(logFile).apply { streamFactory = unwritableStream }.start()
        }

        logFile.exists() shouldBe false
    }

    @Test
    fun `a start that cannot open its writer surfaces the failure`() {
        // The production open, no seam: swallowed, this installs a logger that writes nowhere while
        // the recorder reports success.
        val logFile = File(tempFolder.newFolder("blocked"), "core.log")
        logFile.mkdirs()

        shouldThrow<IOException> { FileLogger(logFile).start() }

        // Not created by this start, so it is not removed either.
        logFile.isDirectory shouldBe true
    }
}
