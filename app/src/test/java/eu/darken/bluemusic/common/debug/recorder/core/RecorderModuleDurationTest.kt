package eu.darken.bluemusic.common.debug.recorder.core

import android.content.Context
import eu.darken.bluemusic.common.BlueMusicId
import eu.darken.bluemusic.common.debug.logging.FileLogger
import eu.darken.bluemusic.common.debug.logging.Logging
import eu.darken.bluemusic.common.upgrade.UpgradeDiagnostics
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import javax.inject.Provider

/**
 * The "that recording looks too short" prompt is a duration heuristic, and duration was measured
 * against the wall clock. A clock adjustment mid-recording (NTP sync, the user changing the time)
 * therefore either invented a long recording out of a short one or trapped a long recording in the
 * warning. A live session now measures monotonically; only a session resumed after process death
 * has to fall back to the session directory's wall-clock age, because monotonic time does not
 * survive a reboot.
 */
class RecorderModuleDurationTest : BaseTest() {

    @TempDir
    lateinit var externalDir: File

    @TempDir
    lateinit var cacheDir: File

    private val context: Context = mockk(relaxed = true)
    private val blueMusicId: BlueMusicId = mockk()
    private val upgradeDiagnostics: UpgradeDiagnostics = mockk()
    private val recorderProvider: Provider<Recorder> = mockk()
    private val mockRecorder: Recorder = mockk()

    private lateinit var appScope: CoroutineScope

    // Set by [withModule] when a resumable session was seeded: the tests read the mtime BACK from
    // it, because the filesystem is free to store something other than what setLastModified asked
    // for and the module measures against what is actually on disk.
    private var resumeSessionDir: File? = null

    @BeforeEach
    fun setup() {
        File(externalDir, "debug/logs").mkdirs()
        File(cacheDir, "debug/logs").mkdirs()

        every { context.getExternalFilesDir(null) } returns externalDir
        every { context.cacheDir } returns cacheDir

        every { blueMusicId.id } returns "abcdefgh12345678"
        // Inert diagnostics: the header reads are covered by RecorderModuleTest, these fixtures only
        // need them to not touch storage.
        coEvery { upgradeDiagnostics.debugInfo() } returns null

        every { recorderProvider.get() } returns mockRecorder
        coEvery { mockRecorder.start(any()) } returns Unit
        coEvery { mockRecorder.stop() } returns Unit

        appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    @AfterEach
    fun teardown() {
        appScope.cancel()
    }

    // Test-controlled clocks, handed to the module's two seams. The durations under test are
    // wall-clock/monotonic, so virtual time cannot drive them.
    private class TestClocks(var wall: Long, var monotonic: Long)

    /**
     * The recorder is stopped in a nested finally, before the scope goes: cancelling the scope alone
     * does NOT uninstall a running recorder's globally installed [FileLogger], and the forward-jump
     * case deliberately ends still recording. This harness injects a mocked [Recorder], so nothing
     * should install one at all — the accounting is what catches a change that starts doing so.
     *
     * [resumeDirModifiedAt] seeds a resumable session: a `bluemusic_`-prefixed directory with a
     * `core.log` in it and a controlled mtime, plus the (empty) trigger file that marks a recording
     * as interrupted by process death. Both are written BEFORE the module is constructed — seeding
     * afterwards races the init collector, which resumes the session during construction. The
     * resume path reads neither clock seam, so setting the seams right after construction is safe.
     */
    private fun withModule(
        clocks: TestClocks,
        resumeDirModifiedAt: Long? = null,
        block: suspend (RecorderModule) -> Unit,
    ) {
        val fileLoggersBefore = Logging.loggers.filterIsInstance<FileLogger>()
        val triggerFile = File(externalDir, TRIGGER_FILE)
        if (resumeDirModifiedAt != null) {
            val sessionDir = File(externalDir, "debug/logs/bluemusic_test_resume").also { it.mkdirs() }
            // findExistingSessionDir() only accepts a session dir that has a core.log.
            check(File(sessionDir, "core.log").createNewFile()) { "Failed to seed core.log" }
            // Last: creating the file inside the directory bumps the directory's own mtime.
            check(sessionDir.setLastModified(resumeDirModifiedAt)) { "Failed to set the session dir mtime" }
            resumeSessionDir = sessionDir
            check(triggerFile.createNewFile()) { "Failed to seed the trigger file" }
        } else if (triggerFile.exists()) {
            triggerFile.delete()
        }

        var module: RecorderModule? = null
        try {
            try {
                val created = RecorderModule(
                    context = context,
                    appScope = appScope,
                    dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
                    blueMusicId = blueMusicId,
                    upgradeDiagnostics = upgradeDiagnostics,
                    recorderProvider = recorderProvider,
                ).apply {
                    wallClock = { clocks.wall }
                    monotonicClock = { clocks.monotonic }
                }
                module = created
                // Envelope: a wedged start or stop must fail in seconds, not hold the gradle worker.
                runBlocking {
                    withTimeout(TEST_ENVELOPE_MS) {
                        if (resumeDirModifiedAt != null) created.state.first { it.isRecording }
                        block(created)
                    }
                }
            } finally {
                module?.let { runBlocking { withTimeoutOrNull(TEST_ENVELOPE_MS) { it.stopRecorder() } } }
            }
        } finally {
            appScope.cancel()
            if (triggerFile.exists()) triggerFile.delete()
            // Remove stragglers after asserting so one failure can't cascade into later tests.
            val leaked = Logging.loggers.filterIsInstance<FileLogger>() - fileLoggersBefore.toSet()
            leaked.forEach { Logging.remove(it) }
            leaked shouldBe emptyList<FileLogger>()
        }
    }

    @Test
    fun `an eight second recording warns`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)
        withModule(clocks) { module ->
            module.startRecorder()

            clocks.monotonic += 8_000L
            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort
            module.state.first().isRecording shouldBe true

            // "Stop anyway" is the user's own next step, and past the threshold it stops cleanly.
            clocks.monotonic += 3_000L
            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
            module.state.first().isRecording shouldBe false
        }
    }

    @Test
    fun `a ten second recording stops`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)
        withModule(clocks) { module ->
            module.startRecorder()

            clocks.monotonic += 10_000L

            val result = module.requestStopRecorder()
            result.shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
            result.logDir.exists() shouldBe true
            result.sessionId.isNotEmpty() shouldBe true
            module.state.first().isRecording shouldBe false
        }
    }

    @Test
    fun `a backward wall-clock jump does not warn on a long recording`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)
        withModule(clocks) { module ->
            module.startRecorder()

            // Twelve real seconds of recording, and an NTP sync that moves the wall clock an hour
            // back. Wall-clock measurement would report a negative duration here.
            clocks.monotonic += 12_000L
            clocks.wall -= 3_600_000L

            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
        }
    }

    @Test
    fun `a forward wall-clock jump does not skip the warning`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)
        withModule(clocks) { module ->
            module.startRecorder()

            // Three real seconds of recording, and a clock correction an hour forward. Wall-clock
            // measurement would call this a one-hour recording and skip the prompt.
            clocks.monotonic += 3_000L
            clocks.wall += 3_600_000L

            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort
            module.state.first().isRecording shouldBe true
        }
    }

    @Test
    fun `a resumed session measures from the session directory age`() {
        // Resumed after a process death: there is no monotonic base to measure against, so the
        // session directory's age is all the module has. Anchored near real time so the seeded
        // mtime stays a plausible file timestamp.
        val base = System.currentTimeMillis()
        val clocks = TestClocks(wall = base, monotonic = 100_000L)

        withModule(clocks, resumeDirModifiedAt = base) { module ->
            val startedAt = resumeSessionDir!!.lastModified()

            clocks.wall = startedAt + 8_000L
            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort

            clocks.wall = startedAt + 10_000L
            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
        }
    }

    @Test
    fun `a resumed session with a future directory age fails open`() {
        // The session directory's age lies in the future (the wall clock moved backward across the
        // resume). A negative duration must not trap the user in the warning. The wall clock is
        // anchored below whatever the filesystem actually stored, so the delta is negative even if
        // the future mtime was not honoured.
        val base = System.currentTimeMillis()
        val clocks = TestClocks(wall = base, monotonic = 100_000L)

        withModule(clocks, resumeDirModifiedAt = base + 60_000L) { module ->
            clocks.wall = resumeSessionDir!!.lastModified() - 60_000L

            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
        }
    }

    @Test
    fun `a repeat recording with a stale old session measures monotonically`() {
        // A completed session directory stays on disk, so findExistingSessionDir() matches it again
        // on the NEXT ordinary recording. Keying the "no monotonic base" case on directory reuse
        // instead of on the trigger file would route that repeat recording to the stale directory's
        // mtime — a wall-clock measurement, and the wrong one at that.
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)
        withModule(clocks) { module ->
            val firstDir = module.startRecorder()

            clocks.monotonic += 11_000L
            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
            module.state.first().isRecording shouldBe false

            // Production leaves the finished directory in place for the zipper, with the core.log
            // the recorder wrote. The mocked recorder writes nothing, so stand it in by hand.
            check(File(firstDir, "core.log").createNewFile()) { "Failed to seed the stale core.log" }
            // Stopping deleted the trigger file, and a fresh recording must not look like a resume.
            File(externalDir, TRIGGER_FILE).exists() shouldBe false

            module.startRecorder()
            // Non-vacuity: the stale directory really is the one being recorded into again, so the
            // assertion below is about which start time wins, not about a fresh directory.
            module.state.first().currentLogDir shouldBe firstDir

            clocks.monotonic += 3_000L
            clocks.wall += 3_600_000L

            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort
            module.state.first().isRecording shouldBe true
        }
    }

    companion object {
        // Independent of any production bound: a wedged wait has to fail the test, not hang the
        // gradle worker.
        private const val TEST_ENVELOPE_MS = 10_000L
        private const val WALL_BASE = 1_800_000_000_000L
        private const val TRIGGER_FILE = "bluemusic_force_debug_run"
    }
}
