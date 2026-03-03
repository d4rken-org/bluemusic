package eu.darken.bluemusic.main.ui.settings.support

import android.content.Context
import android.text.format.Formatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.recorder.core.RecorderModule
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SupportScreenViewModel @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    @param:ApplicationContext private val context: Context,
    private val webpageTool: WebpageTool,
    private val recorderModule: RecorderModule,
) : ViewModel4(dispatcherProvider, logTag("Settings", "Support", "ViewModel"), navCtrl) {

    private val folderStats = MutableStateFlow<FolderStats?>(null)

    val state = combine(recorderModule.state, folderStats) { recState, stats ->
        State(
            isRecording = recState.isRecording,
            logPath = recState.currentLogDir,
            folderSessionCount = stats?.count ?: 0,
            folderTotalSize = stats?.formattedSize,
        )
    }.asStateFlow()

    val events = SingleEventFlow<SupportEvent>()

    init {
        refreshFolderStats()
    }

    fun onResume() {
        refreshFolderStats()
    }

    sealed interface SupportEvent {
        data object ShowShortRecordingWarning : SupportEvent
    }

    fun startDebugLog() = launch {
        log(tag) { "Starting debug log recording" }
        recorderModule.startRecorder()
    }

    fun stopDebugLog() = launch {
        log(tag) { "Requesting stop debug log recording" }
        when (val result = recorderModule.requestStopRecorder()) {
            is RecorderModule.StopResult.TooShort -> {
                log(tag) { "Recording too short: ${result.durationSeconds}s" }
                events.emit(SupportEvent.ShowShortRecordingWarning)
            }

            is RecorderModule.StopResult.Stopped -> {
                log(tag) { "Recording stopped: ${result.logDir}" }
                refreshFolderStats()
            }

            is RecorderModule.StopResult.NotRecording -> {
                log(tag) { "Was not recording" }
            }
        }
    }

    fun confirmStopDebugLog() = launch {
        log(tag) { "Force stopping debug log recording" }
        recorderModule.stopRecorder()
        refreshFolderStats()
    }

    fun deleteAllDebugLogs() = launch {
        log(tag) { "Deleting all debug logs" }
        withContext(dispatcherProvider.IO) {
            recorderModule.getLogsDir().listFiles()?.forEach { it.deleteRecursively() }
        }
        refreshFolderStats()
    }

    private fun refreshFolderStats() = launch {
        val stats = withContext(dispatcherProvider.IO) {
            val sessions = recorderModule.getLogEntries()
            val totalSize = recorderModule.getLogsDir()
                .walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
            FolderStats(
                count = sessions.size,
                formattedSize = Formatter.formatShortFileSize(context, totalSize),
            )
        }
        folderStats.value = stats
    }

    fun openUrl(url: String) = launch {
        log(tag) { "Opening URL: $url" }
        webpageTool.open(url)
    }

    private data class FolderStats(
        val count: Int,
        val formattedSize: String,
    )

    data class State(
        val isRecording: Boolean,
        val logPath: File?,
        val folderSessionCount: Int = 0,
        val folderTotalSize: String? = null,
    )

}
