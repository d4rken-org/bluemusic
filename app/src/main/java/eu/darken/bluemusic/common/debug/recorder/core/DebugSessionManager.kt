package eu.darken.bluemusic.common.debug.recorder.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.compression.Zipper
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.DynamicStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
) {

    sealed interface DebugSession {
        val id: String
        val dir: File
        val timestamp: Long

        data class Recording(
            override val id: String,
            override val dir: File,
            override val timestamp: Long,
        ) : DebugSession

        data class Compressing(
            override val id: String,
            override val dir: File,
            override val timestamp: Long,
        ) : DebugSession

        data class Ready(
            override val id: String,
            override val dir: File,
            override val timestamp: Long,
            val zipFile: File,
            val zipSize: Long,
            val fileCount: Int,
        ) : DebugSession

        data class Failed(
            override val id: String,
            override val dir: File,
            override val timestamp: Long,
            val error: Exception,
        ) : DebugSession
    }

    data class Stats(
        val sessionCount: Int,
        val totalSize: Long,
    )

    private data class InternalState(
        val sessions: List<DebugSession> = emptyList(),
    )

    private val compressionJobs = ConcurrentHashMap<String, Job>()
    private val compressionQueue = Mutex()
    private val failedSessions = ConcurrentHashMap<String, Exception>()

    private val logsDir: File
        get() = File(context.externalCacheDir, "debug/logs")

    private val internalState = DynamicStateFlow(TAG, appScope + dispatcherProvider.IO) {
        InternalState()
    }

    val sessions: Flow<List<DebugSession>> = internalState.flow.map { it.sessions }

    val isRecording: Flow<Boolean> = sessions.map { list -> list.any { it is DebugSession.Recording } }

    val stats: Flow<Stats> = sessions.map { list ->
        val readySessions = list.filterIsInstance<DebugSession.Ready>()
        Stats(
            sessionCount = readySessions.size,
            totalSize = readySessions.sumOf { it.zipSize },
        )
    }

    init {
        recorderModule.state
            .distinctUntilChangedBy { it.isRecording to it.currentLogDir to it.lastLogDir }
            .onEach { recState ->
                log(TAG) { "Recorder state changed: isRecording=${recState.isRecording}, lastLogDir=${recState.lastLogDir}" }

                if (!recState.isRecording && recState.lastLogDir != null) {
                    val lastDir = recState.lastLogDir
                    triggerCompression(lastDir)
                }

                rescan(recState)
            }
            .launchIn(appScope + dispatcherProvider.IO)

        appScope.launch(dispatcherProvider.IO) {
            cleanupStaleTmpFiles()
            compressOrphanSessions()
        }
    }

    suspend fun startRecording(): File = recorderModule.startRecorder()

    suspend fun stopRecording(): File? = recorderModule.stopRecorder()

    suspend fun requestStopRecording(): RecorderModule.StopResult = recorderModule.requestStopRecorder()

    suspend fun deleteSession(session: DebugSession) = withContext(dispatcherProvider.IO) {
        if (session is DebugSession.Recording) {
            log(TAG, WARN) { "Cannot delete active recording session: ${session.id}" }
            return@withContext
        }
        log(TAG) { "Deleting session: ${session.id}" }
        cancelCompression(session.id)
        failedSessions.remove(session.id)
        deleteSessionFiles(session.dir)
        rescan()
    }

    fun getSessionFiles(session: DebugSession): List<File> {
        return session.dir.listFiles()?.toList() ?: emptyList()
    }

    private suspend fun rescan(recState: RecorderModule.State? = null) {
        val state = recState ?: recorderModule.state.first()
        val scanned = scanSessions(state)
        internalState.updateBlocking { copy(sessions = scanned) }
    }

    private fun scanSessions(recState: RecorderModule.State): List<DebugSession> {
        if (!logsDir.exists()) return emptyList()

        val activeDir = if (recState.isRecording) recState.currentLogDir else null
        val dirs = logsDir.listFiles { file -> file.isDirectory }?.toList() ?: emptyList()

        return dirs.mapNotNull { dir ->
            val id = dir.name
            val timestamp = dir.lastModified()

            when {
                dir == activeDir -> DebugSession.Recording(
                    id = id,
                    dir = dir,
                    timestamp = timestamp,
                )

                compressionJobs.containsKey(id) && compressionJobs[id]?.isActive == true -> {
                    DebugSession.Compressing(
                        id = id,
                        dir = dir,
                        timestamp = timestamp,
                    )
                }

                failedSessions.containsKey(id) -> {
                    DebugSession.Failed(
                        id = id,
                        dir = dir,
                        timestamp = timestamp,
                        error = failedSessions[id]!!,
                    )
                }

                else -> {
                    val zipFile = File(dir.parentFile, "${dir.name}.zip")
                    if (zipFile.exists()) {
                        val files = dir.listFiles() ?: emptyArray()
                        DebugSession.Ready(
                            id = id,
                            dir = dir,
                            timestamp = timestamp,
                            zipFile = zipFile,
                            zipSize = zipFile.length(),
                            fileCount = files.size,
                        )
                    } else {
                        null
                    }
                }
            }
        }.sortedWith(compareByDescending<DebugSession> { it.timestamp }.thenBy { it.id })
    }

    private fun triggerCompression(dir: File) {
        val id = dir.name
        log(TAG) { "Triggering compression for session: $id" }

        val job = appScope.launch(dispatcherProvider.IO) {
            compressionQueue.withLock {
                compressSession(dir)
            }
        }

        compressionJobs[id] = job

        job.invokeOnCompletion {
            compressionJobs.remove(id)
            appScope.launch(dispatcherProvider.IO) { rescan() }
        }

        appScope.launch(dispatcherProvider.IO) { rescan() }
    }

    private fun compressSession(dir: File) {
        val id = dir.name
        log(TAG) { "Compressing session: $id" }

        try {
            val logFiles = dir.listFiles() ?: emptyArray()
            if (logFiles.isEmpty()) {
                log(TAG, WARN) { "No files to compress in $id" }
                return
            }

            val zipFile = File(dir.parentFile, "${dir.name}.zip")
            val tmpFile = File(dir.parentFile, "${dir.name}.zip.tmp")

            Zipper().zip(
                logFiles.map { it.path },
                tmpFile.path,
            )

            if (!tmpFile.renameTo(zipFile)) {
                val error = IllegalStateException("Failed to rename tmp to zip: $tmpFile -> $zipFile")
                log(TAG, ERROR) { error.message!! }
                tmpFile.delete()
                failedSessions[id] = error
                return
            }

            log(TAG) { "Compression complete for $id: ${zipFile.length()}B" }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Compression failed for $id: ${e.asLog()}" }
            failedSessions[id] = e
        }
    }

    private suspend fun cancelCompression(sessionId: String) {
        compressionJobs[sessionId]?.let { job ->
            log(TAG) { "Cancelling compression for: $sessionId" }
            job.cancel()
            job.join()
        }
    }

    private fun deleteSessionFiles(dir: File) {
        val zipFile = File(dir.parentFile, "${dir.name}.zip")
        val tmpFile = File(dir.parentFile, "${dir.name}.zip.tmp")
        if (tmpFile.exists()) tmpFile.delete()
        if (zipFile.exists()) zipFile.delete()
        dir.deleteRecursively()
    }

    private fun cleanupStaleTmpFiles() {
        val parent = logsDir
        if (!parent.exists()) return
        parent.listFiles { file -> file.name.endsWith(".zip.tmp") }?.forEach { tmp ->
            log(TAG) { "Cleaning up stale tmp file: ${tmp.name}" }
            tmp.delete()
        }
    }

    private suspend fun compressOrphanSessions() {
        val recState = recorderModule.state.first()
        val activeDir = if (recState.isRecording) recState.currentLogDir else null

        if (!logsDir.exists()) return

        val dirs = logsDir.listFiles { file -> file.isDirectory }?.toList() ?: emptyList()

        for (dir in dirs) {
            if (dir == activeDir) continue
            val zipFile = File(dir.parentFile, "${dir.name}.zip")
            if (!zipFile.exists()) {
                log(TAG) { "Found orphan session without zip: ${dir.name}" }
                triggerCompression(dir)
            }
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Session", "Manager")
    }
}
