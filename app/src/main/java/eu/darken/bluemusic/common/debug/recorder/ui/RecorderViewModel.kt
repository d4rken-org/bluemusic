package eu.darken.bluemusic.common.debug.recorder.ui

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.BlueMusicLinks
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.recorder.core.DebugSessionManager
import eu.darken.bluemusic.common.debug.recorder.core.DebugSessionManager.DebugSession
import eu.darken.bluemusic.common.flow.DynamicStateFlow
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    navCtrl: NavigationController,
    dispatchers: DispatcherProvider,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val webpageTool: WebpageTool,
    private val debugSessionManager: DebugSessionManager,
) : ViewModel4(dispatchers, logTag("Debug", "Recorder", "Screen", "VM"), navCtrl) {

    private val recordedPath: File

    private val stater: DynamicStateFlow<State>
    val state: Flow<State>

    val shareEvent = SingleEventFlow<Intent>()
    val finishEvent = SingleEventFlow<Unit>()

    init {
        val path = savedStateHandle.get<String>(RecorderActivity.RECORD_PATH)
            ?: throw IllegalStateException("No path provided")
        recordedPath = File(path)

        val durationSeconds = savedStateHandle.get<Long>(RecorderActivity.RECORD_DURATION)
        val formattedDuration = durationSeconds?.let { formatDuration(it) }

        stater = DynamicStateFlow(TAG, vmScope) {
            State(logDir = recordedPath, sessionDuration = formattedDuration)
        }
        state = stater.flow

        launch {
            debugSessionManager.sessions
                .map { sessions -> sessions.find { it.dir == recordedPath } }
                .collect { session ->
                    when (session) {
                        is DebugSession.Compressing -> {
                            val logFiles = debugSessionManager.getSessionFiles(session)
                            val entries = logFiles.map { LogFileItem(path = it, size = it.length()) }
                                .sortedByDescending { it.size }
                            stater.updateBlocking {
                                copy(logEntries = entries, operationState = OperationState.COMPRESSING)
                            }
                        }

                        is DebugSession.Ready -> {
                            val logFiles = debugSessionManager.getSessionFiles(session)
                            val entries = logFiles.map { LogFileItem(path = it, size = it.length()) }
                                .sortedByDescending { it.size }
                            stater.updateBlocking {
                                copy(
                                    logEntries = entries,
                                    compressedFile = session.zipFile,
                                    compressedSize = session.zipSize,
                                    operationState = OperationState.READY,
                                )
                            }
                        }

                        is DebugSession.Failed -> {
                            log(TAG) { "Session failed: ${session.error}" }
                        }

                        is DebugSession.Recording -> {
                            // Should not normally happen from this screen
                        }

                        null -> {
                            // Session may have been deleted
                        }
                    }
                }
        }
    }

    fun share() = launch {
        val file = stater.value().compressedFile ?: return@launch
        if (!file.exists() || !file.canRead()) {
            log(TAG) { "Share file missing or unreadable: $file" }
            return@launch
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            val uri = FileProvider.getUriForFile(
                context,
                BuildConfigWrap.APPLICATION_ID + ".provider",
                file
            )

            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            type = "application/zip"

            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "${BuildConfigWrap.APPLICATION_ID} DebugLog - ${BuildConfigWrap.VERSION_DESCRIPTION})"
            )
            putExtra(Intent.EXTRA_TEXT, "Your text here.")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooserIntent = Intent.createChooser(intent, context.getString(R.string.debug_log_file_label))
        shareEvent.emit(chooserIntent)
    }

    fun keep() {
        log(TAG) { "Keeping debug log files at $recordedPath" }
        finishEvent.tryEmit(Unit)
    }

    fun delete() = launch {
        log(TAG) { "Deleting debug log files at $recordedPath" }
        stater.updateBlocking { copy(operationState = OperationState.DELETING) }

        val session = debugSessionManager.sessions
            .map { sessions -> sessions.find { it.dir == recordedPath } }
            .first()

        if (session != null) {
            debugSessionManager.deleteSession(session)
        }

        finishEvent.emit(Unit)
    }

    fun goPrivacyPolicy() {
        webpageTool.open(BlueMusicLinks.PRIVACY_POLICY)
    }

    enum class OperationState {
        COMPRESSING,
        READY,
        DELETING,
    }

    data class State(
        val logDir: File,
        val logEntries: List<LogFileItem> = emptyList(),
        val compressedFile: File? = null,
        val compressedSize: Long? = null,
        val sessionDuration: String? = null,
        val operationState: OperationState = OperationState.COMPRESSING,
    ) {
        val loading: Boolean
            get() = compressedSize == null

        fun getFormattedCompressedSize(context: Context): String? {
            return compressedSize?.let { Formatter.formatShortFileSize(context, it) }
        }
    }

    data class LogFileItem(
        val path: File,
        val size: Long? = null,
    ) {
        fun getFormattedSize(context: Context): String? {
            return size?.let { Formatter.formatShortFileSize(context, it) }
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Recorder", "ViewModel")

        private fun formatDuration(seconds: Long): String {
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            return if (minutes > 0) "${minutes}m ${remainingSeconds}s" else "${remainingSeconds}s"
        }
    }
}
