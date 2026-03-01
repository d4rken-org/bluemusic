package eu.darken.bluemusic.common.debug.recorder.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugLogStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val recorderModule: RecorderModule,
) {

    data class LogSession(
        val dir: File,
        val zipFile: File?,
        val timestamp: Long,
        val fileCount: Int,
        val totalSize: Long,
    )

    data class Stats(
        val sessionCount: Int,
        val totalSize: Long,
    )

    private val logsDir: File
        get() = File(context.externalCacheDir, "debug/logs")

    private suspend fun activeLogDir(): File? {
        val state = recorderModule.state.first()
        return if (state.isRecording) state.currentLogDir else null
    }

    suspend fun getSessions(): List<LogSession> = withContext(dispatcherProvider.IO) {
        val activeDir = activeLogDir()
        val baseDir = logsDir
        if (!baseDir.exists()) return@withContext emptyList()

        val dirs = baseDir.listFiles { file -> file.isDirectory }?.toList() ?: emptyList()

        dirs
            .filter { it != activeDir }
            .mapNotNull { dir ->
                val zipFile = File(dir.parentFile, "${dir.name}.zip").takeIf { it.exists() }
                val files = dir.listFiles() ?: emptyArray()
                val totalSize = if (zipFile != null) {
                    zipFile.length()
                } else {
                    files.sumOf { it.length() }
                }
                LogSession(
                    dir = dir,
                    zipFile = zipFile,
                    timestamp = dir.lastModified(),
                    fileCount = files.size,
                    totalSize = totalSize,
                )
            }
            .sortedByDescending { it.timestamp }
    }

    suspend fun getStats(): Stats = withContext(dispatcherProvider.IO) {
        val sessions = getSessions()
        Stats(
            sessionCount = sessions.size,
            totalSize = sessions.sumOf { it.totalSize },
        )
    }

    suspend fun deleteAll() = withContext(dispatcherProvider.IO) {
        val activeDir = activeLogDir()
        val baseDir = logsDir
        if (!baseDir.exists()) return@withContext

        val dirs = baseDir.listFiles { file -> file.isDirectory }?.toList() ?: emptyList()
        for (dir in dirs) {
            if (dir == activeDir) continue
            deleteSessionFiles(dir)
        }
        log(TAG) { "Deleted all stored debug log sessions" }
    }

    suspend fun deleteSession(session: LogSession) = withContext(dispatcherProvider.IO) {
        deleteSessionFiles(session.dir)
        log(TAG) { "Deleted session: ${session.dir.name}" }
    }

    private fun deleteSessionFiles(dir: File) {
        val zipFile = File(dir.parentFile, "${dir.name}.zip")
        if (zipFile.exists()) zipFile.delete()
        dir.deleteRecursively()
    }

    companion object {
        private val TAG = logTag("Debug", "Log", "Store")
    }
}
