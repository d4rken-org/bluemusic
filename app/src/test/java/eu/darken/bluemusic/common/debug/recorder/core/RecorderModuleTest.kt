package eu.darken.bluemusic.common.debug.recorder.core

import android.content.Context
import eu.darken.bluemusic.common.BlueMusicId
import eu.darken.bluemusic.common.debug.logging.FileLogger
import eu.darken.bluemusic.common.debug.logging.Logging
import eu.darken.bluemusic.common.upgrade.UpgradeDiagnostics
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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

    @TempDir
    lateinit var tempDir: File

    private val context: Context = mockk(relaxed = true)
    private val blueMusicId: BlueMusicId = mockk()
    private val upgradeDiagnostics: UpgradeDiagnostics = mockk()
    private val recorderProvider: Provider<Recorder> = mockk()
    private val mockRecorder: Recorder = mockk()

    private lateinit var externalDir: File
    private lateinit var cacheDir: File
    private lateinit var externalLogDir: File

    private val triggerFile: File
        get() = File(externalDir, TRIGGER_FILE)

    // Test-controlled clocks handed to the module's seams: the durations under test are
    // wall-clock/monotonic, so virtual time cannot drive them. The wall clock is fixed, which also
    // pins the session dir name - the collision walk is only observable with a stable name.
    // Volatile: the concurrency suite advances them from the test thread while the module reads them
    // on its own dispatcher threads.
    @Volatile
    private var wallNow = WALL_BASE

    @Volatile
    private var monotonicNow = MONOTONIC_BASE

    @BeforeEach
    fun setupFixture() {
        externalDir = File(tempDir, "external").apply { mkdirs() }
        cacheDir = File(tempDir, "cache").apply { mkdirs() }
        externalLogDir = File(externalDir, "debug/logs").apply { mkdirs() }

        every { context.getExternalFilesDir(null) } returns externalDir
        every { context.cacheDir } returns cacheDir

        every { blueMusicId.id } returns "abcdefgh12345678"
        coEvery { upgradeDiagnostics.debugInfo() } returns "BillingCache(test)"

        every { recorderProvider.get() } returns mockRecorder
        coEvery { mockRecorder.start(any()) } returns Unit
        coEvery { mockRecorder.stop() } returns Unit

        wallNow = WALL_BASE
        monotonicNow = MONOTONIC_BASE
    }

    private fun createModule(scope: CoroutineScope, dispatcher: CoroutineDispatcher) = RecorderModule(
        context = context,
        appScope = scope,
        dispatcherProvider = TestDispatcherProvider(dispatcher),
        blueMusicId = blueMusicId,
        upgradeDiagnostics = upgradeDiagnostics,
        recorderProvider = recorderProvider,
    ).apply {
        // The monotonic seam defaults to SystemClock.elapsedRealtime, an Android framework stub that
        // throws in plain JVM tests. RecorderModuleDurationTest owns the duration heuristic; here the
        // clocks only have to be readable and controllable.
        wallClock = { wallNow }
        monotonicClock = { monotonicNow }
    }

    private val logLines = CopyOnWriteArrayList<String>()
    private val logCapture = object : Logging.Logger {
        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            logLines.add(message)
        }
    }

    /**
     * Real dispatchers on purpose: these tests are about a failed or raced transition SETTLING, and
     * virtual time would skip past a wedge rather than expose it — the header's read deadline is
     * wall-clock too. The envelope is what turns a regression into a failure in seconds instead of a
     * gradle worker stuck until the job timeout, which is what the pre-fix module did.
     *
     * The recorder is stopped in a nested finally BEFORE the scope goes: cancelling the scope alone
     * does not uninstall a running recorder's globally installed [FileLogger]. This harness injects a
     * mocked [Recorder], so nothing should install one at all — the accounting is what catches a
     * change that starts doing so.
     */
    private fun withRealtimeModule(
        headerTimeoutMs: Long = 300L,
        block: suspend (RecorderModule) -> Unit,
    ) {
        val fileLoggersBefore = Logging.loggers.filterIsInstance<FileLogger>()
        val moduleScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        Logging.install(logCapture)
        var module: RecorderModule? = null
        try {
            try {
                val created = createModule(moduleScope, Dispatchers.IO)
                created.headerReadTimeoutMs = headerTimeoutMs
                module = created
                runBlocking {
                    withTimeout(TEST_ENVELOPE_MS) {
                        // Settled before the test acts: the state flow only goes hot once the init
                        // collector subscribes, and a request racing that start-up is not what any
                        // of these tests are about.
                        created.state.first()
                        block(created)
                    }
                }
            } finally {
                module?.let {
                    runBlocking {
                        withTimeoutOrNull(TEST_ENVELOPE_MS) { runCatching { it.stopRecorder() } }
                    }
                }
            }
        } finally {
            Logging.remove(logCapture)
            moduleScope.cancel()
            // A leaked logger must fail THIS test, not poison later ones. Remove stragglers after
            // asserting so one failure cannot cascade.
            val leaked = Logging.loggers.filterIsInstance<FileLogger>() - fileLoggersBefore.toSet()
            leaked.forEach { Logging.remove(it) }
            leaked shouldBe emptyList<FileLogger>()
        }
    }

    /**
     * A recorder started from the debug settings has no trigger file yet — and an unwritable external
     * files dir makes creating it fail right after the recorder went live.
     */
    private fun blockTriggerFileCreation() {
        val blockedParent = File(tempDir, "blocked").apply { createNewFile() }
        every { context.getExternalFilesDir(null) } returns blockedParent
    }

    /** A session interrupted by process death: a dir with a log in it plus the trigger that marks it. */
    private fun seedResumableSession(): File {
        val sessionDir = File(externalLogDir, "bluemusic_1.0_20260309T120000Z_abcdefgh").apply { mkdirs() }
        File(sessionDir, "core.log").writeText("the recording the user already made\n")
        check(triggerFile.createNewFile()) { "Failed to seed the trigger file" }
        return sessionDir
    }

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

        @Test
        fun `startFailure is null`() {
            RecorderModule.State().startFailure shouldBe null
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

        @Test
        fun `a cancellation during the log header stops the uncommitted recorder`() = runTest {
            // The recorder is started before the header is written but only committed to the state
            // afterwards: a cancellation in between would orphan a running recorder that
            // stopRecorder() can never reach.
            triggerFile.createNewFile()
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
            triggerFile.createNewFile()

            val dispatcher = StandardTestDispatcher(testScheduler)
            val moduleScope = CoroutineScope(dispatcher + Job())
            val module = createModule(moduleScope, dispatcher)

            // Queued alongside the module's own coroutines: the first turn where currentLogDir is
            // set is the one where the start is parked in the state commit.
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
        fun `a failing log header surfaces to the caller instead of hanging`() {
            // Same window as the cancellation cases, but for an ordinary failure of the header
            // writes: it reaches the caller, and the module stays usable.
            blockTriggerFileCreation()

            withRealtimeModule { module ->
                shouldThrow<IOException> { module.startRecorder() }

                coVerify { mockRecorder.start(any()) }
                coVerify { mockRecorder.stop() }
                val state = module.state.first()
                state.isRecording shouldBe false
                state.startFailure.shouldNotBeNull()
                module.currentLogDir.shouldBeNull()
            }
        }

        @Test
        fun `a failing stop is reported with the original failure`() {
            // The stop is a best-effort cleanup: losing the reason the start failed would leave the
            // debug log with a mystery instead of the disk error that caused it.
            blockTriggerFileCreation()
            val stopError = IllegalStateException("recorder is wedged")
            coEvery { mockRecorder.stop() } throws stopError

            withRealtimeModule { module ->
                val caught = shouldThrow<IOException> { module.startRecorder() }
                caught.suppressed.toList() shouldBe listOf(stopError)
            }
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

    /**
     * A start that fails must not take the state collector with it: that collector is the only thing
     * that ever serves a start or stop request, so killing it wedges every later caller forever —
     * while the recorder it abandoned keeps writing where nothing can reach it.
     */
    @Nested
    inner class StartFailures {

        @Test
        fun `a failed start settles the request and leaves the collector alive`() {
            coEvery { mockRecorder.start(any()) } throws IOException("recorder broken")

            withRealtimeModule { module ->
                shouldThrow<IOException> { module.startRecorder() }

                val failed = module.state.first()
                failed.isRecording shouldBe false
                // Reset on failure, or the collector walks straight back into the start branch.
                failed.shouldRecord shouldBe false
                failed.startFailure.shouldNotBeNull()
                failed.currentLogDir.shouldBeNull()
                module.currentLogDir.shouldBeNull()
                triggerFile.exists() shouldBe false

                coEvery { mockRecorder.start(any()) } returns Unit

                // Non-vacuity: with a rethrow the collector would be dead here and this would hang
                // until the envelope kills the test.
                val logDir = module.startRecorder()
                logDir.exists() shouldBe true
                val recording = module.state.first { it.isRecording }
                recording.currentLogDir shouldBe logDir
                // A stale failure must not be reported as the outcome of the attempt that succeeded.
                recording.startFailure.shouldBeNull()
                triggerFile.exists() shouldBe true
            }
        }

        @Test
        fun `a failed start rolls back the session dir it created`() {
            coEvery { mockRecorder.start(any()) } throws IOException("recorder broken")

            withRealtimeModule { module ->
                shouldThrow<IOException> { module.startRecorder() }

                // Publishing the failure kicks off a session scan, and an empty dir left here would
                // be picked up as an orphan session that never held a recording.
                externalLogDir.listFiles()?.toList().orEmpty().shouldBeEmpty()
                triggerFile.exists() shouldBe false
            }
        }

        @Test
        fun `a failed start deletes the trigger file it created`() {
            // Fails AFTER the trigger was written: a trigger left behind re-attempts the dead
            // session on every single app launch.
            withRealtimeModule { module ->
                module.monotonicClock = { throw IOException("clock broken") }

                shouldThrow<IOException> { module.startRecorder() }

                triggerFile.exists() shouldBe false
                externalLogDir.listFiles()?.toList().orEmpty().shouldBeEmpty()
                module.currentLogDir.shouldBeNull()
                module.state.first().startFailure.shouldNotBeNull()
            }
        }

        @Test
        fun `a failed resume keeps the markers of the session it was resuming`() {
            // The boot path starts a recording without anyone calling startRecorder(): a trigger left
            // from a previous run makes the module resume on construction. None of it was created by
            // the failing attempt, so none of it may be rolled away.
            val sessionDir = seedResumableSession()
            coEvery { mockRecorder.start(any()) } throws IOException("recorder broken")

            withRealtimeModule { module ->
                module.state.first { it.startFailure != null }

                module.state.first().isRecording shouldBe false
                module.currentLogDir.shouldBeNull()
                sessionDir.exists() shouldBe true
                File(sessionDir, "core.log").readText() shouldBe "the recording the user already made\n"
                triggerFile.exists() shouldBe true
            }
        }

        /**
         * A [CancellationException] out of the start work does NOT mean this module's scope is going
         * away — a bounded read timing out looks exactly like this. Handed to the caller unchanged it
         * would unwind THEM as if they had been cancelled, and the ViewModel error handler ignores
         * cancellations: the user is told nothing at all.
         */
        @Test
        fun `a foreign cancellation reaches the caller as a start failure`() {
            coEvery { mockRecorder.start(any()) } throws CancellationException("bounded read gave up")

            withRealtimeModule { module ->
                val error = shouldThrow<RecorderModule.RecorderStartFailedException> { module.startRecorder() }
                // The conversion is what matters, not which instance survived it: a cancellation
                // travelling through the coroutine machinery may well arrive as a copy.
                error.cause.shouldBeInstanceOf<CancellationException>()
                module.state.first().startFailure.shouldBeInstanceOf<RecorderModule.RecorderStartFailedException>()

                coEvery { mockRecorder.start(any()) } returns Unit

                // Non-vacuity: with a rethrow the collector would be dead here.
                val logDir = module.startRecorder()
                module.state.first { it.isRecording }.currentLogDir shouldBe logDir
            }
        }

        /**
         * The stop side of the same window: everything awaiting the transition — stopRecorder(),
         * requestStopRecorder(), the UI's isRecording — depends on the cleared state being committed
         * even when the recorder itself cannot be shut down.
         */
        @Test
        fun `a recorder that fails to stop still clears the recording state`() {
            withRealtimeModule { module ->
                val logDir = module.startRecorder()
                triggerFile.exists() shouldBe true

                coEvery { mockRecorder.stop() } throws IOException("log writer wedged")

                module.stopRecorder() shouldBe logDir

                val state = module.state.first()
                state.isRecording shouldBe false
                state.currentLogDir.shouldBeNull()
                state.recordingStartedAt shouldBe 0L
                module.currentLogDir.shouldBeNull()
                triggerFile.exists() shouldBe false

                coEvery { mockRecorder.stop() } returns Unit

                // The module survived the failed stop and still serves the next request.
                module.startRecorder().exists() shouldBe true
            }
        }

        @Test
        fun `a session name collision takes a new dir instead of adopting the existing one`() {
            withRealtimeModule { module ->
                val first = module.startRecorder()
                module.stopRecorder() shouldBe first

                // The mocked recorder writes no core.log, so the finished dir is not resumable and
                // the create path runs straight into its name — the wall clock is fixed, so the
                // second recording computes the very same one.
                File(first, "core.log").exists() shouldBe false

                val second = module.startRecorder()

                // Adopting it would mark somebody else's directory as created-by-this-attempt, and a
                // failed start would then delete it.
                second.name shouldBe "${first.name}-1"
                second.isDirectory shouldBe true
                first.isDirectory shouldBe true
                DebugSessionManager.deriveSessionId(second) shouldBe "ext:${second.name}"
            }
        }
    }

    /**
     * The public request surface is serialized: without that, two callers racing the same transition
     * observe each other's outcome — two of them report having stopped the same session, or a start
     * request settles on a failure that belongs to somebody else's attempt.
     *
     * Every test here pins the first caller INSIDE the transition — parked in the mocked recorder's
     * own start or stop, holding the lock — before the racers are launched, and checks they are still
     * blocked before releasing it. Launching them side by side on [Dispatchers.IO] does not race
     * anything: the transitions are short enough to serialize on their own, and the assertions would
     * hold with the mutex removed.
     */
    @Nested
    inner class Concurrency {

        @Test
        fun `four concurrent stop requests report exactly one stop`() {
            val stopEntered = CompletableDeferred<Unit>()
            val releaseStop = CompletableDeferred<Unit>()
            coEvery { mockRecorder.stop() } coAnswers {
                stopEntered.complete(Unit)
                releaseStop.await()
            }

            withRealtimeModule { module ->
                module.startRecorder()
                monotonicNow += 20_000L

                val results = coroutineScope {
                    val first = async(Dispatchers.IO) { module.requestStopRecorder() }
                    // The transition is in flight and its caller holds the lock: the state still
                    // says "recording" while the stop it asked for has not committed.
                    stopEntered.await()

                    val racers = (1..3).map { async(Dispatchers.IO) { module.requestStopRecorder() } }
                    delay(SETTLE_MS)
                    // Without the lock they would read that stale "recording" and each report having
                    // stopped the very same session.
                    racers.forEach { it.isCompleted shouldBe false }

                    releaseStop.complete(Unit)
                    (listOf(first) + racers).awaitAll()
                }

                results.count { it is RecorderModule.StopResult.Stopped } shouldBe 1
                results.count { it == RecorderModule.StopResult.NotRecording } shouldBe 3
                module.state.first().isRecording shouldBe false
            }
        }

        @Test
        fun `a direct stop racing a stop request reports the stop exactly once`() {
            val stopEntered = CompletableDeferred<Unit>()
            val releaseStop = CompletableDeferred<Unit>()
            coEvery { mockRecorder.stop() } coAnswers {
                stopEntered.complete(Unit)
                releaseStop.await()
            }

            withRealtimeModule { module ->
                val logDir = module.startRecorder()
                monotonicNow += 20_000L

                val (direct, requested) = coroutineScope {
                    val a = async(Dispatchers.IO) { module.stopRecorder() }
                    // Same window, from the direct stop's side: it is parked in the recorder's stop
                    // with the lock held when the request below arrives.
                    stopEntered.await()

                    val b = async(Dispatchers.IO) { module.requestStopRecorder() }
                    delay(SETTLE_MS)
                    b.isCompleted shouldBe false

                    releaseStop.complete(Unit)
                    a.await() to b.await()
                }

                val reports = listOfNotNull(direct).size +
                    (if (requested is RecorderModule.StopResult.Stopped) 1 else 0)
                reports shouldBe 1
                (direct ?: (requested as RecorderModule.StopResult.Stopped).logDir) shouldBe logDir
                module.state.first().isRecording shouldBe false
            }
        }

        @Test
        fun `concurrent failed starts each get their own attempt`() {
            val firstStartEntered = CompletableDeferred<Unit>()
            val releaseFirstStart = CompletableDeferred<Unit>()
            coEvery { mockRecorder.start(any()) } coAnswers {
                // Only the first attempt is held: the second one has to be free to run to completion
                // once the lock is handed over to it.
                if (firstStartEntered.complete(Unit)) releaseFirstStart.await()
                throw IOException("recorder broken")
            }

            withRealtimeModule { module ->
                coroutineScope {
                    val first = async(Dispatchers.IO) { runCatching { module.startRecorder() } }
                    firstStartEntered.await()

                    val second = async(Dispatchers.IO) { runCatching { module.startRecorder() } }
                    delay(SETTLE_MS)
                    // Without the lock this request would fold into the attempt already running -
                    // its shouldRecord=true is not a distinct state - and report that one's failure
                    // as its own, which the attempt count below then contradicts.
                    second.isCompleted shouldBe false

                    releaseFirstStart.complete(Unit)

                    // Serialized: neither request can clear the failure the other one is waiting for
                    // and leave it hanging on a recording that is never coming.
                    first.await().exceptionOrNull().shouldBeInstanceOf<IOException>()
                    second.await().exceptionOrNull().shouldBeInstanceOf<IOException>()
                }

                // One attempt per request, not one shared outcome reported twice.
                coVerify(exactly = 2) { mockRecorder.start(any()) }
            }
        }

        @Test
        fun `a stop request during an in-flight start waits for it`() {
            val startEntered = CompletableDeferred<Unit>()
            val releaseStart = CompletableDeferred<Unit>()
            coEvery { mockRecorder.start(any()) } coAnswers {
                startEntered.complete(Unit)
                releaseStart.await()
            }

            withRealtimeModule { module ->
                coroutineScope {
                    val starting = async(Dispatchers.IO) { module.startRecorder() }
                    startEntered.await()

                    val stopping = async(Dispatchers.IO) { module.stopRecorder() }
                    delay(SETTLE_MS)

                    // Reading the state outside the lock answered "not recording" here, and the start
                    // committed right afterwards — a recording the user had already asked to stop.
                    stopping.isCompleted shouldBe false

                    releaseStart.complete(Unit)

                    val logDir = starting.await()
                    stopping.await() shouldBe logDir
                }

                module.state.first().isRecording shouldBe false
                module.currentLogDir.shouldBeNull()
                triggerFile.exists() shouldBe false
            }
        }
    }

    companion object {
        // Independent of any production bound: a wedged wait has to fail the test, not hang the
        // gradle worker.
        private const val TEST_ENVELOPE_MS = 10_000L

        // Real time, not virtual: long enough for a transition that should NOT have happened to show
        // itself, short enough to stay well inside the envelope.
        private const val SETTLE_MS = 300L

        private const val WALL_BASE = 1_800_000_000_000L
        private const val MONOTONIC_BASE = 100_000L
        private const val TRIGGER_FILE = "bluemusic_force_debug_run"
    }
}
