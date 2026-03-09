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
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.DynamicStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecorderModule @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val blueMusicId: BlueMusicId,
) {

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

    private fun findExistingSessionDir(): File? {
        for (parent in getLogDirectories()) {
            if (!parent.exists()) continue
            val dirs = parent.listFiles { f -> f.isDirectory && f.name.startsWith("bluemusic_") }
                ?: continue
            val mostRecent = dirs.maxByOrNull { it.lastModified() } ?: continue
            val coreLog = File(mostRecent, "core.log")
            if (coreLog.exists()) return mostRecent
        }
        return null
    }

    private suspend fun reconcileState(state: State) {
        if (!state.isRecording && state.shouldRecord) {
            val existingDir = findExistingSessionDir()
            val sessionDir = existingDir ?: createSessionDir()
            val logFile = File(sessionDir, "core.log")
            val newRecorder = Recorder()
            newRecorder.start(logFile)
            triggerFile.createNewFile()

            if (existingDir != null) {
                log(TAG, INFO) { "Resuming recording in existing session: ${existingDir.name}" }
            }
            log(TAG, INFO) { "Build.Fingerprint: ${Build.FINGERPRINT}" }
            log(TAG, INFO) { "BuildConfig.Versions: ${BuildConfigWrap.VERSION_DESCRIPTION}" }

            val startedAt = if (existingDir != null) {
                existingDir.lastModified()
            } else {
                System.currentTimeMillis()
            }

            this@RecorderModule.currentLogDir = sessionDir

            internalState.updateBlocking {
                copy(
                    recorder = newRecorder,
                    currentLogDir = sessionDir,
                    recordingStartedAt = startedAt,
                )
            }
        } else if (!state.shouldRecord && state.isRecording) {
            state.recorder!!.stop()

            if (triggerFile.exists() && !triggerFile.delete()) {
                log(TAG, ERROR) { "Failed to delete trigger file" }
            }

            this@RecorderModule.currentLogDir = null

            internalState.updateBlocking {
                copy(
                    recorder = null,
                    currentLogDir = null,
                    recordingStartedAt = 0L,
                )
            }
        }
    }

    private fun createSessionDir(): File {
        val timestamp = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
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
        val sessionDir = File(parent, dirName)
        sessionDir.mkdirs()

        log(TAG) { "Created session dir: $sessionDir" }
        return sessionDir
    }

    internal fun getLogDirectories(): List<File> = listOfNotNull(
        try {
            context.getExternalFilesDir(null)?.let { File(it, "debug/logs") }
        } catch (e: Exception) {
            null
        },
        File(context.cacheDir, "debug/logs"),
    )

    suspend fun startRecorder(): File {
        internalState.updateBlocking {
            copy(shouldRecord = true)
        }
        return requireNotNull(internalState.flow.filter { it.isRecording }.first().currentLogDir) {
            "Recording started but currentLogDir is null"
        }
    }

    suspend fun stopRecorder(): File? {
        val currentDir = internalState.value().currentLogDir ?: return null
        internalState.updateBlocking {
            copy(shouldRecord = false)
        }
        internalState.flow.filter { !it.isRecording }.first()
        return currentDir
    }

    suspend fun requestStopRecorder(): StopResult {
        val currentState = internalState.value()
        if (!currentState.isRecording) return StopResult.NotRecording

        val logDir = currentState.currentLogDir ?: return StopResult.NotRecording
        val elapsed = System.currentTimeMillis() - currentState.recordingStartedAt
        if (elapsed < MIN_RECORDING_MS) return StopResult.TooShort

        stopRecorder()
        val sessionId = DebugSessionManager.deriveSessionId(logDir)
        return StopResult.Stopped(logDir, sessionId)
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
    ) {
        val isRecording: Boolean
            get() = recorder != null

        val currentLogPath: File?
            get() = recorder?.path
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "Recorder", "Module")
        private const val FORCE_FILE = "bluemusic_force_debug_run"
        private const val MIN_RECORDING_MS = 5_000L
    }
}
