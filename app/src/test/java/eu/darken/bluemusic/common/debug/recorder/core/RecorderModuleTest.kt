package eu.darken.bluemusic.common.debug.recorder.core

import android.content.Context
import eu.darken.bluemusic.common.BlueMusicId
import eu.darken.bluemusic.common.debug.logging.Logging
import eu.darken.bluemusic.common.upgrade.UpgradeDiagnostics
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Provider
import kotlin.system.measureTimeMillis

class RecorderModuleTest : BaseTest() {

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

    @Nested
    inner class StartGuard {

        @TempDir lateinit var tempDir: File

        private val context: Context = mockk(relaxed = true)
        private val blueMusicId: BlueMusicId = mockk()
        private val upgradeDiagnostics: UpgradeDiagnostics = mockk()
        private val recorderProvider: Provider<Recorder> = mockk()
        private val mockRecorder: Recorder = mockk()

        private lateinit var externalDir: File
        private lateinit var cacheDir: File

        @BeforeEach
        fun setup() {
            externalDir = File(tempDir, "external").apply { mkdirs() }
            cacheDir = File(tempDir, "cache").apply { mkdirs() }

            every { context.getExternalFilesDir(null) } returns externalDir
            every { context.cacheDir } returns cacheDir

            every { blueMusicId.id } returns "abcdefgh12345678"
            coEvery { upgradeDiagnostics.debugInfo() } returns "BillingCache(test)"

            every { recorderProvider.get() } returns mockRecorder
            coEvery { mockRecorder.start(any()) } returns Unit
            coEvery { mockRecorder.stop() } returns Unit
        }

        private fun createModule(scope: CoroutineScope, dispatcher: CoroutineDispatcher) = RecorderModule(
            context = context,
            appScope = scope,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            blueMusicId = blueMusicId,
            upgradeDiagnostics = upgradeDiagnostics,
            recorderProvider = recorderProvider,
        ).apply {
            // The monotonic seam defaults to SystemClock.elapsedRealtime, an Android framework stub
            // that throws in plain JVM tests. Nothing here measures durations — RecorderModuleDurationTest
            // does — these starts just must not trip over the default.
            monotonicClock = { 0L }
        }

        private val logLines = CopyOnWriteArrayList<String>()
        private val logCapture = object : Logging.Logger {
            override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
                logLines.add(message)
            }
        }

        /**
         * Real dispatchers on purpose: the header's read deadline is wall-clock, so a virtual-time
         * test would skip past it instead of exercising it — an ignored seam has to fail this, not
         * pass after the full production budget. The seam is set before [RecorderModule.startRecorder]
         * so no header read can run against the production bound.
         *
         * The block runs inside its own deadline: a missing or mis-wired production bound must fail
         * this test rather than wedge the gradle worker on a read that never answers.
         */
        private fun withRealtimeModule(
            headerTimeoutMs: Long = 300L,
            block: suspend (RecorderModule) -> Unit,
        ) {
            val moduleScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            Logging.install(logCapture)
            try {
                val module = createModule(moduleScope, Dispatchers.IO)
                module.headerReadTimeoutMs = headerTimeoutMs
                runBlocking { withTimeout(TEST_ENVELOPE_MS) { block(module) } }
            } finally {
                Logging.remove(logCapture)
                moduleScope.cancel()
            }
        }

        /** A recorder started from the debug settings has no trigger file yet — and an unwritable
         * external files dir makes creating it fail right after the recorder went live. */
        private fun blockTriggerFileCreation() {
            val blockedParent = File(tempDir, "blocked").apply { createNewFile() }
            every { context.getExternalFilesDir(null) } returns blockedParent
        }

        @Test
        fun `a cancellation during the log header stops the uncommitted recorder`() = runTest {
            // The recorder is started before the header is written but only committed to the state
            // afterwards: a cancellation in between would orphan a running recorder that
            // stopRecorder() can never reach.
            File(externalDir, "bluemusic_force_debug_run").createNewFile()
            coEvery { upgradeDiagnostics.debugInfo() } coAnswers { awaitCancellation() }

            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val moduleScope = CoroutineScope(dispatcher + Job())
            val module = createModule(moduleScope, dispatcher)
            // runCurrent, not advanceUntilIdle: the header read is bounded, and advancing virtual
            // time would jump that deadline so the header completes before the cancellation lands.
            runCurrent()

            // Cancelling the module's scope cancels the in-flight header read.
            moduleScope.cancel()
            advanceUntilIdle()

            coVerify { mockRecorder.start(any()) }
            coVerify { mockRecorder.stop() }
            module.state.first().isRecording shouldBe false
            // DebugSessionManager reads this field directly — a stale value would advertise a
            // session that no recorder is writing to.
            module.currentLogDir shouldBe null
        }

        @Test
        fun `a cancellation during the state commit stops the uncommitted recorder`() = runTest {
            // currentLogDir is published before the state commit lands: a cancellation while the
            // commit is in flight would leave a running recorder behind a log dir that the state
            // never learns about.
            File(externalDir, "bluemusic_force_debug_run").createNewFile()

            val dispatcher = StandardTestDispatcher(testScheduler)
            val moduleScope = CoroutineScope(dispatcher + Job())
            val module = createModule(moduleScope, dispatcher)

            // Queued alongside the module's own coroutines: the first turn where currentLogDir is
            // set is the one where reconcileState() is parked in the state commit.
            val canceller = launch {
                var turns = 0
                while (module.currentLogDir == null && turns++ < 1000) yield()
                moduleScope.cancel()
            }
            advanceUntilIdle()
            canceller.isCompleted shouldBe true

            coVerify { mockRecorder.start(any()) }
            coVerify { mockRecorder.stop() }
            // DebugSessionManager reads this field directly — a stale value would advertise a
            // session that no recorder is writing to.
            module.currentLogDir shouldBe null
        }

        @Test
        fun `a failing log header stops the uncommitted recorder`() = runTest {
            // Same window as above, but for ordinary failures of the header writes.
            blockTriggerFileCreation()

            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            var caught: Throwable? = null
            val handler = CoroutineExceptionHandler { _, error -> caught = error }
            val moduleScope = CoroutineScope(dispatcher + Job() + handler)
            val module = createModule(moduleScope, dispatcher)
            // The recorder is enabled from the debug settings, so there is no trigger file yet.
            val starter = launch { module.startRecorder() }
            advanceUntilIdle()

            caught.shouldBeInstanceOf<IOException>()
            coVerify { mockRecorder.start(any()) }
            coVerify { mockRecorder.stop() }
            module.state.first().isRecording shouldBe false
            module.currentLogDir shouldBe null

            starter.cancel()
            moduleScope.cancel()
        }

        @Test
        fun `a failing stop is reported with the original failure`() = runTest {
            // The stop is a best-effort cleanup: losing the reason the start failed would leave the
            // debug log with a mystery instead of the disk error that caused it.
            blockTriggerFileCreation()
            val stopError = IllegalStateException("recorder is wedged")
            coEvery { mockRecorder.stop() } throws stopError

            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            var caught: Throwable? = null
            val handler = CoroutineExceptionHandler { _, error -> caught = error }
            val moduleScope = CoroutineScope(dispatcher + Job() + handler)
            val module = createModule(moduleScope, dispatcher)
            val starter = launch { module.startRecorder() }
            advanceUntilIdle()

            caught.shouldBeInstanceOf<IOException>()
            caught!!.suppressed.toList() shouldBe listOf(stopError)

            starter.cancel()
            moduleScope.cancel()
        }

        /**
         * Debug recording is what a user reaches for when the app is ALREADY misbehaving, so a
         * diagnostics source that never answers must not be the thing that denies them the log.
         */
        @Test
        fun `a wedged upgrade diagnostics read does not hold up the recording`() {
            coEvery { upgradeDiagnostics.debugInfo() } coAnswers { awaitCancellation() }

            withRealtimeModule(headerTimeoutMs = 300L) { module ->
                val elapsed = measureTimeMillis { module.startRecorder() }

                module.state.first().isRecording shouldBe true
                logLines.any { it.startsWith("Upgrade diagnostics unavailable") } shouldBe true
                // Non-vacuity: without the bound this would sit on the wedged read forever.
                elapsed shouldBeLessThan 1_500L
            }
        }

        @Test
        fun `a flavor without diagnostics is not reported as unavailable`() {
            // FOSS has nothing to report and returns null: no diagnostics line at all, and above all
            // not one claiming the read failed or timed out.
            coEvery { upgradeDiagnostics.debugInfo() } returns null

            withRealtimeModule { module ->
                module.startRecorder()

                module.state.first().isRecording shouldBe true
                logLines.any { it.startsWith("Upgrade diagnostics") } shouldBe false
            }
        }
    }
}

// Independent of the production bound: a missing or mis-wired one has to fail the test, not hang
// the gradle worker.
private const val TEST_ENVELOPE_MS = 10_000L
