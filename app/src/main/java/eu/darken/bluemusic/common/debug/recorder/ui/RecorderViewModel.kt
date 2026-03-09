package eu.darken.bluemusic.common.debug.recorder.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.BlueMusicLinks
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.recorder.core.DebugSession
import eu.darken.bluemusic.common.debug.recorder.core.DebugSessionManager
import eu.darken.bluemusic.common.flow.DynamicStateFlow
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.ui.ViewModel3
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val sessionManager: DebugSessionManager,
    private val webpageTool: WebpageTool,
) : ViewModel3(dispatcherProvider) {

    data class LogEntry(
        val file: File,
        val size: Long,
    )

    data class State(
        val logDir: File? = null,
        val logEntries: List<LogEntry> = emptyList(),
        val totalSize: Long = 0L,
        val compressedSize: Long = -1L,
        val recordingDurationSecs: Long = 0L,
        val isWorking: Boolean = true,
    )

    sealed interface Event {
        data class ShareIntent(val intent: Intent) : Event
        data object Finish : Event
    }

    private val sessionId: String? = handle.get<String>(RecorderActivity.RECORD_SESSION_ID)
    private val legacyPath: String? = handle.get<String>(RecorderActivity.RECORD_PATH)

    private suspend fun resolveSession(): DebugSession? {
        if (sessionId != null) {
            val session = sessionManager.sessions.first().firstOrNull { it.id == sessionId }
            if (session != null) return session
            sessionManager.refresh()
            return sessionManager.sessions.drop(1).first().firstOrNull { it.id == sessionId }
        }
        if (legacyPath != null) {
            val file = File(legacyPath)
            val derivedId = DebugSessionManager.deriveSessionId(file)
            sessionManager.refresh()
            return sessionManager.sessions.drop(1).first().firstOrNull { it.id == derivedId }
        }
        return null
    }

    private val stater = DynamicStateFlow(TAG, vmScope + dispatcherProvider.IO) {
        val session = resolveSession()
        val logDir = when (session) {
            is DebugSession.Ready -> session.logDir
            is DebugSession.Compressing -> session.path
            is DebugSession.Failed -> session.path?.takeIf { it.isDirectory }
            is DebugSession.Recording -> session.path
            null -> legacyPath?.let { File(it) }
        }

        val isCompressing = session is DebugSession.Compressing

        if (logDir == null || !logDir.exists()) {
            return@DynamicStateFlow State(logDir = null, isWorking = isCompressing)
        }

        val files = logDir.listFiles()?.toList() ?: emptyList()
        val entries = files.map { LogEntry(it, it.length()) }
        val totalSize = entries.sumOf { it.size }

        val compressedSize = when (session) {
            is DebugSession.Compressing -> -1L
            is DebugSession.Ready -> session.compressedSize.takeIf { it > 0 } ?: -1L
            else -> -1L
        }

        val dirCreated = logDir.lastModified()
        val latestFileModified = files.maxOfOrNull { it.lastModified() } ?: dirCreated
        val durationSecs = ((latestFileModified - dirCreated) / 1000).coerceAtLeast(0)

        State(
            logDir = logDir,
            logEntries = entries,
            totalSize = totalSize,
            compressedSize = compressedSize,
            recordingDurationSecs = durationSecs,
            isWorking = isCompressing,
        )
    }
    val state = stater.flow

    val events = SingleEventFlow<Event>()

    init {
        sessionManager.sessions
            .onEach { allSessions ->
                val sid = sessionId ?: return@onEach
                val session = allSessions.firstOrNull { it.id == sid } ?: return@onEach
                if (session is DebugSession.Ready) {
                    stater.updateBlocking {
                        if (!isWorking) return@updateBlocking this
                        copy(
                            compressedSize = session.compressedSize.takeIf { it > 0 } ?: -1L,
                            isWorking = false,
                        )
                    }
                }
            }
            .launchIn(vmScope)
    }

    fun share() = launch {
        val sid = sessionId ?: return@launch

        stater.updateBlocking { copy(isWorking = true) }

        try {
            val uri = sessionManager.getZipUri(sid)
            val sessionName = sid.removePrefix("ext:").removePrefix("cache:")

            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "application/zip"
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra(Intent.EXTRA_SUBJECT, "BlueMusic DebugLog - $sessionName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooserIntent = Intent.createChooser(intent, context.getString(R.string.debug_log_file_label))
            events.tryEmit(Event.ShareIntent(chooserIntent))
        } catch (e: Exception) {
            log(TAG) { "share() failed: $e" }
            errorEvents.tryEmit(e)
        } finally {
            stater.updateBlocking { copy(isWorking = false) }
        }
    }

    fun keep() {
        events.tryEmit(Event.Finish)
    }

    fun discard() = launch {
        val sid = sessionId ?: return@launch
        sessionManager.deleteSession(sid)
        events.tryEmit(Event.Finish)
    }

    fun goPrivacyPolicy() {
        webpageTool.open(BlueMusicLinks.PRIVACY_POLICY)
    }

    companion object {
        private val TAG = logTag("Debug", "Recorder", "VM")
    }
}
