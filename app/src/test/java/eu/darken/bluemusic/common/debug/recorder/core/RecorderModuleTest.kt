package eu.darken.bluemusic.common.debug.recorder.core

import android.content.Context
import eu.darken.bluemusic.common.BlueMusicId
import eu.darken.bluemusic.common.upgrade.UpgradeDiagnostics
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.io.IOException
import javax.inject.Provider

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
        )

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
            advanceUntilIdle()

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
    }
}
