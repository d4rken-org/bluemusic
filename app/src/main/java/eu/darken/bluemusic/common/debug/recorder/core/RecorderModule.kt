package eu.darken.bluemusic.common.debug.recorder.core

import android.content.Context
import android.os.Build
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.BlueMusicId
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.error.addSuppressedSafely
import eu.darken.bluemusic.common.flow.DynamicStateFlow
import eu.darken.bluemusic.common.upgrade.UpgradeDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.annotation.VisibleForTesting
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RecorderModule @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val blueMusicId: BlueMusicId,
    private val upgradeDiagnostics: UpgradeDiagnostics,
    private val recorderProvider: Provider<Recorder>,
) {

    // Test seam: the header read below is bounded on real dispatchers, so a virtual-time test cannot
    // advance the production bound. Same pattern as BillingCache.cacheTimeoutMs.
    internal var headerReadTimeoutMs: Long = HEADER_READ_TIMEOUT_MS

    // Test seams for the two clocks the recording heuristics use. Same pattern as the header bound:
    // the durations are wall-clock/monotonic, so virtual time cannot drive them.
    internal var wallClock: () -> Long = System::currentTimeMillis
    internal var monotonicClock: () -> Long = android.os.SystemClock::elapsedRealtime

    // Serializes the public request surface, observation included: two callers racing the same
    // transition would otherwise each await a state the other one is about to overwrite, and report
    // each other's outcome instead of their own attempt's.
    private val startStopLock = Mutex()

    @Volatile
    internal var currentLogDir: File? = null
        private set

    private val triggerFile = try {
        File(context.getExternalFilesDir(null), FORCE_FILE)
    } catch (e: Exception) {
        File(
            Environment.getExternalStorageDirectory(),
            "/Android/data/${BuildConfigWrap.APPLICATION_ID}/files/$FORCE_FILE"
        )
    }

    private val internalState = DynamicStateFlow(TAG, appScope + dispatcherProvider.IO) {
        val triggerFileExists = triggerFile.exists()
        State(shouldRecord = triggerFileExists)
    }
    val state: Flow<State> = internalState.flow

    init {
        internalState.flow
            .onEach { state ->
                log(TAG) { "New Recorder state: $state" }
                reconcileState(state)
            }
            .launchIn(appScope)
    }

    private fun findExistingSessionDir(): File? = findExistingSessionDir(getLogDirectories())

    private suspend fun reconcileState(state: State) {
        if (!state.isRecording && state.shouldRecord) {
            startRecording()
        } else if (!state.shouldRecord && state.isRecording) {
            val recorder = state.recorder ?: return
            stopRecording(recorder)
        }
    }

    /**
     * Everything a start attempt does is inside the guard, the work that decides WHERE to record
     * included: a throw anywhere in here used to escape this collector and kill it for the rest of
     * the process — the recorder kept writing where nothing could stop it, the trigger file survived
     * to re-attempt the dead session on every launch, and [startRecorder] waited for a state nobody
     * would ever publish again.
     */
    private suspend fun startRecording() {
        // Rollback bookkeeping, all of it derived from what actually happened on disk: only a
        // trigger file and a session dir THIS attempt brought into existence may be removed again.
        var candidateRecorder: Recorder? = null
        var createdTrigger = false
        var createdSessionDir: File? = null

        try {
            // Keyed on the trigger file, NOT on directory reuse: a completed session dir survives
            // stopping, so findExistingSessionDir() also matches an ordinary repeat recording. Only
            // a trigger that already existed before this start sequence means the process died
            // mid-recording and we are resuming it — the one case with no usable monotonic base.
            val isProcessResume = triggerFile.exists()

            val existingDir = findExistingSessionDir()
            val sessionDir = existingDir ?: createSessionDir().also { createdSessionDir = it }
            val logFile = File(sessionDir, "core.log")

            // Bound BEFORE start() so a throw from inside start() still has something to roll back.
            val newRecorder = recorderProvider.get().also { candidateRecorder = it }
            newRecorder.start(logFile)

            // The return value is the only honest answer to "did this attempt create it": a trigger
            // that was already there belongs to the session being resumed.
            createdTrigger = triggerFile.createNewFile()

            if (existingDir != null) {
                log(TAG, INFO) { "Resuming recording in existing session: ${existingDir.name}" }
            }
            log(TAG, INFO) { "Build.Fingerprint: ${Build.FINGERPRINT}" }
            log(TAG, INFO) { "BuildConfig.Versions: ${BuildConfigWrap.VERSION_DESCRIPTION}" }

            try {
                // Billing complaints usually arrive as debug logs: having the local entitlement
                // record in the header saves a support round-trip. Bounded on top of that: debug
                // recording is what a user reaches for when the app is ALREADY misbehaving, so a
                // source that never answers (a stuck DataStore file lock, a billing store that
                // doesn't respond) must not hold up the start of the recording either.
                val read = withTimeoutOrNull(headerReadTimeoutMs) { HeaderRead(upgradeDiagnostics.debugInfo()) }
                when {
                    read == null -> log(TAG, WARN) {
                        "Upgrade diagnostics unavailable, read did not finish within ${headerReadTimeoutMs}ms"
                    }
                    // Completion is tracked separately from the value: a flavor that legitimately has
                    // nothing to report (FOSS) returns null and gets no line at all, not an "unavailable".
                    read.value != null -> log(TAG, INFO) { "Upgrade diagnostics: ${read.value}" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Diagnostics only — a broken read must not stop the recorder from starting.
                log(TAG, WARN) { "Upgrade diagnostics unavailable: ${e.asLog()}" }
            }

            val recordingStartedAt = if (existingDir != null) {
                existingDir.lastModified()
            } else {
                wallClock()
            }

            this@RecorderModule.currentLogDir = sessionDir

            internalState.updateBlocking {
                copy(
                    recorder = newRecorder,
                    currentLogDir = sessionDir,
                    recordingStartedAt = recordingStartedAt,
                    // A fresh start that reuses an old session dir gets BOTH bases: the mtime
                    // wall stamp above and this monotonic one. The heuristic prefers the
                    // monotonic base, so the stale mtime never decides a live recording.
                    recordingStartedAtMonotonic = if (isProcessResume) null else monotonicClock(),
                    startFailure = null,
                )
            }
        } catch (e: Throwable) {
            rollbackStart(e, candidateRecorder, createdTrigger, createdSessionDir)

            logSafely { log(TAG, ERROR) { "Failed to start recording: ${e.asLog()}" } }

            // Our own scope dying is the one failure that SHOULD take this collector with it.
            // Anything else — a cancellation from inside the start work included — becomes an
            // ordinary failure, because rethrowing it here wedges the module for the whole process.
            currentCoroutineContext().ensureActive()

            publishStartFailure(asStartFailure(e))
        }
    }

    /**
     * For the log lines that sit between cleanup and publication. The logging framework hands a
     * logger's failure straight back to the caller, and any installed logger can fail — not just
     * ours: an error line lost costs a diagnostic, an error line thrown costs the cleanup that was
     * still to run, the state commit behind it, and with it this module for the rest of the process.
     */
    private inline fun logSafely(block: () -> Unit) {
        try {
            block()
        } catch (ignore: Throwable) {
        }
    }

    /**
     * Total and uncancellable: every step runs regardless of what the ones before it did, so a start
     * that failed — or was cancelled — half-way leaves nothing behind that this module can no longer
     * reach. Cleanup failures are attached to the failure being reported instead of replacing it.
     */
    private suspend fun rollbackStart(
        error: Throwable,
        candidateRecorder: Recorder?,
        createdTrigger: Boolean,
        createdSessionDir: File?,
    ) = withContext(NonCancellable) {
        suspend fun step(block: suspend () -> Unit) {
            try {
                block()
            } catch (cleanupError: Throwable) {
                error.addSuppressedSafely(cleanupError)
            }
        }

        // The recorder may be live while nothing points at it yet: left behind it keeps its globally
        // installed loggers and stopRecorder() can never reach it.
        step { candidateRecorder?.stop() }
        // Only a trigger this attempt created: one that was already there is the resume marker of
        // the session we were resuming.
        step {
            if (createdTrigger && triggerFile.exists() && !triggerFile.delete()) {
                log(TAG, ERROR) { "Failed to delete trigger file after failed start" }
            }
        }
        // Same rule for the directory: a resumed one holds the recording the user already made.
        step {
            createdSessionDir?.let { dir ->
                if (dir.isDirectory && !dir.deleteRecursively()) {
                    log(TAG, ERROR) { "Failed to delete session dir after failed start: $dir" }
                }
            }
        }
        // DebugSessionManager reads this field directly — a stale value advertises a session that no
        // recorder is writing to.
        step { this@RecorderModule.currentLogDir = null }
    }

    /**
     * The last thing a failed start does, and it must not throw: shouldRecord is reset so this
     * collector is re-armed for a retry instead of walking straight back into the failing branch,
     * and the failure is committed so a waiting [startRecorder] observes it instead of waiting for a
     * recording that is never coming.
     */
    private suspend fun publishStartFailure(failure: Throwable) {
        try {
            internalState.updateBlocking {
                copy(
                    shouldRecord = false,
                    startFailure = failure,
                    recorder = null,
                    currentLogDir = null,
                    recordingStartedAt = 0L,
                    recordingStartedAtMonotonic = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logSafely { log(TAG, ERROR) { "Failed to publish the start failure: ${e.asLog()}" } }
        }
    }

    /**
     * Best effort, step by step: a stop that cannot complete must not strand everyone awaiting the
     * transition either — [stopRecorder], [requestStopRecorder] and the UI's isRecording all wait
     * for the cleared state, which is committed regardless and last.
     */
    private suspend fun stopRecording(recorder: Recorder) {
        suspend fun step(label: String, block: suspend () -> Unit) {
            try {
                block()
            } catch (e: Throwable) {
                // Our own scope dying still propagates; anything else is logged and cleanup goes on.
                currentCoroutineContext().ensureActive()
                logSafely { log(TAG, ERROR) { "$label: ${e.asLog()}" } }
            }
        }

        step("Failed to stop the recorder") { recorder.stop() }
        step("Failed to delete trigger file") {
            if (triggerFile.exists() && !triggerFile.delete()) {
                log(TAG, ERROR) { "Failed to delete trigger file" }
            }
        }
        step("Failed to clear the current log dir") { this@RecorderModule.currentLogDir = null }

        try {
            internalState.updateBlocking {
                copy(
                    recorder = null,
                    currentLogDir = null,
                    recordingStartedAt = 0L,
                    recordingStartedAtMonotonic = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logSafely { log(TAG, ERROR) { "Failed to publish the stopped state: ${e.asLog()}" } }
        }
    }

    /**
     * A start failure that arrived as a [CancellationException] while this module's own scope was
     * still alive — a bounded read inside the start work timing out, for example. Stored and
     * rethrown unchanged it would make every caller treat it as THEIR cancellation: the launch that
     * requested the start ends "normally" and the error handler that would have surfaced the failure
     * never runs.
     */
    class RecorderStartFailedException(cause: Throwable) : IllegalStateException("Failed to start recording", cause)

    /**
     * Runs AFTER [ensureActive] has confirmed the scope is alive, so a cancellation seen here can
     * only be a foreign one. Everything else is committed unchanged.
     */
    private fun asStartFailure(error: Throwable): Throwable = when (error) {
        is CancellationException -> RecorderStartFailedException(error)
        else -> error
    }

    /**
     * Completion marker for a header read: tells a source that legitimately has nothing to report
     * (no diagnostics on FOSS) apart from one that never answered within the deadline.
     */
    private class HeaderRead<T>(val value: T)

    private fun createSessionDir(): File {
        val timestamp = java.time.Instant.ofEpochMilli(wallClock()).atZone(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val installIdPrefix = blueMusicId.id.take(8)
        val dirName = "bluemusic_${BuildConfigWrap.VERSION_NAME}_${timestamp}_$installIdPrefix"

        val primaryParent = try {
            val dir = File(context.getExternalFilesDir(null), "debug/logs")
            dir.mkdirs()
            if (dir.canWrite()) dir else null
        } catch (e: Exception) {
            log(TAG, WARN) { "External files dir unavailable: $e" }
            null
        }

        val parent = primaryParent ?: File(context.cacheDir, "debug/logs").also { it.mkdirs() }

        // mkdirs() reports false for a directory that already exists, so the candidate that wins is
        // always one this call brought into existence. Adopting an existing one would hand the
        // rollback of a failed start somebody else's logs to delete — and a bare exists() check
        // cannot tell them apart here, because a session dir is deliberately reused across
        // recordings via findExistingSessionDir(). The name is stamped to the second, so a
        // collision means a leftover directory or a retry within the same second, not a long run.
        for (attempt in 0..NAME_COLLISION_LIMIT) {
            val candidate = File(parent, if (attempt == 0) dirName else "$dirName-$attempt")
            if (candidate.mkdirs()) {
                log(TAG) { "Created session dir: $candidate" }
                return candidate
            }
            // Not a collision but an unusable parent directory: further suffixes cannot help.
            if (!candidate.exists()) break
        }

        throw IOException("Failed to create a session dir for $dirName in $parent")
    }

    internal fun getLogDirectories(): List<File> = listOfNotNull(
        try {
            context.getExternalFilesDir(null)?.let { File(it, "debug/logs") }
        } catch (e: Exception) {
            null
        },
        File(context.cacheDir, "debug/logs"),
    )

    suspend fun startRecorder(): File = startStopLock.withLock {
        // Clearing the failure is part of the request: a stale one from an earlier attempt would
        // otherwise be reported as the outcome of this one.
        internalState.updateBlocking {
            copy(shouldRecord = true, startFailure = null)
        }
        // A start that cannot succeed has to settle this wait too, or the caller sits here forever.
        val settled = internalState.flow.first { it.isRecording || it.startFailure != null }
        settled.startFailure?.let { throw it }

        requireNotNull(settled.currentLogDir) { "Recording started but currentLogDir is null" }
    }

    suspend fun stopRecorder(): File? = startStopLock.withLock { stopRecorderLocked() }

    /**
     * Requires [startStopLock]. Reading the current session, publishing the request and observing
     * the stop have to happen without another caller in between, or two callers report having
     * stopped the same session — or one that is still running.
     */
    private suspend fun stopRecorderLocked(): File? {
        val currentDir = internalState.value().currentLogDir ?: return null
        internalState.updateBlocking {
            copy(shouldRecord = false)
        }
        internalState.flow.first { !it.isRecording }
        return currentDir
    }

    suspend fun requestStopRecorder(): StopResult = startStopLock.withLock {
        val currentState = internalState.value()
        if (!currentState.isRecording) return@withLock StopResult.NotRecording

        if (currentState.currentLogDir == null) return@withLock StopResult.NotRecording
        val elapsed = currentState.recordingStartedAtMonotonic
            ?.let { monotonicClock() - it }             // live session: immune to wall-clock adjustments
            ?: (wallClock() - currentState.recordingStartedAt)  // resumed: only the mtime-derived wall start exists
        // Negative = wall clock moved backward across a resume; fail open (no warning) rather than
        // trap the user in TooShort.
        if (elapsed in 0 until MIN_RECORDING_MS) return@withLock StopResult.TooShort

        // The delegated stop decides what was stopped: reporting the dir read above would claim a
        // stop that a concurrent caller had already performed.
        val stoppedDir = stopRecorderLocked() ?: return@withLock StopResult.NotRecording
        val sessionId = DebugSessionManager.deriveSessionId(stoppedDir)
        StopResult.Stopped(stoppedDir, sessionId)
    }

    sealed class StopResult {
        data object TooShort : StopResult()
        data class Stopped(val logDir: File, val sessionId: String) : StopResult()
        data object NotRecording : StopResult()
    }

    data class State(
        val shouldRecord: Boolean = false,
        internal val recorder: Recorder? = null,
        val currentLogDir: File? = null,
        val recordingStartedAt: Long = 0L,
        // Monotonic base for the duration heuristic, null when there is none: a session resumed
        // after process death has only the persisted wall-clock start, and a monotonic value from a
        // previous process or boot is meaningless. Nullable rather than 0L — 0 is a legal
        // elapsedRealtime near boot.
        val recordingStartedAtMonotonic: Long? = null,
        /**
         * Why the last start attempt did not produce a recording, cleared when a new one is
         * requested. Carried as the Throwable itself: the state flow is distinctUntilChanged, so a
         * failure has to produce a value distinct from its predecessor or a waiting [startRecorder]
         * never wakes up — Throwable's reference equality provides that.
         */
        val startFailure: Throwable? = null,
    ) {
        val isRecording: Boolean
            get() = recorder != null

        val currentLogPath: File?
            get() = recorder?.path
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "Recorder", "Module")
        private const val FORCE_FILE = "bluemusic_force_debug_run"
        /**
         * Duration heuristic for "did you forget to reproduce the issue?". A recording stopped
         * this quickly usually contains nothing but the recorder starting and stopping, which
         * costs a support round-trip to re-request.
         *
         * It stays a prompt because short recordings can be perfectly valid: a crash is logged
         * and flushed immediately, so the reproduction is already on disk. The
         * [StopResult.TooShort] consumers (the Support and ContactSupport screens) turn it into
         * a ShowShortRecordingWarning event, and their "stop anyway" answer goes through the
         * direct force-stop path, which has no duration check.
         */
        private const val MIN_RECORDING_MS = 10_000L

        /**
         * Suffixes tried when the timestamped session dir name is already taken. A handful is
         * plenty: the name is stamped to the second, so a collision means a leftover directory or a
         * retry within the same second, not a long run of them.
         */
        private const val NAME_COLLISION_LIMIT = 8

        // Budget for the header's diagnostics read.
        private const val HEADER_READ_TIMEOUT_MS = 5_000L

        @VisibleForTesting
        internal fun findExistingSessionDir(logDirectories: List<File>): File? {
            for (parent in logDirectories) {
                if (!parent.exists()) continue
                val dirs = parent.listFiles { f -> f.isDirectory && f.name.startsWith("bluemusic_") }
                    ?: continue
                val mostRecent = dirs.maxByOrNull { it.lastModified() } ?: continue
                val coreLog = File(mostRecent, "core.log")
                if (coreLog.exists()) return mostRecent
            }
            return null
        }
    }
}
