package eu.darken.bluemusic.main.ui.settings.support

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.recorder.core.DebugLogStore
import eu.darken.bluemusic.common.debug.recorder.core.RecorderModule
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SupportScreenViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val webpageTool: WebpageTool,
    private val recorderModule: RecorderModule,
    private val debugLogStore: DebugLogStore,
) : ViewModel4(dispatcherProvider, logTag("Settings", "Support", "ViewModel"), navCtrl) {

    private val storedStats = MutableStateFlow<DebugLogStore.Stats?>(null)

    val state = combine(recorderModule.state, storedStats) { recState, stored ->
        State(
            isRecording = recState.isRecording,
            logPath = recState.currentLogDir,
            storedStats = stored,
        )
    }.asStateFlow()

    val events = SingleEventFlow<SupportEvent>()
    val snackbarEvent = SingleEventFlow<String>()

    init {
        refreshStoredStats()
    }

    fun onResume() {
        refreshStoredStats()
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
                refreshStoredStats()
            }

            is RecorderModule.StopResult.NotRecording -> {
                log(tag) { "Was not recording" }
            }
        }
    }

    fun confirmStopDebugLog() = launch {
        log(tag) { "Force stopping debug log recording" }
        recorderModule.stopRecorder()
        refreshStoredStats()
    }

    private fun refreshStoredStats() = launch {
        val stats = debugLogStore.getStats()
        storedStats.value = stats
    }

    fun openUrl(url: String) = launch {
        log(tag) { "Opening URL: $url" }
        webpageTool.open(url)
    }

    fun deleteStoredLogs() = launch {
        log(tag) { "Deleting all stored debug logs" }
        debugLogStore.deleteAll()
        refreshStoredStats()
    }

    fun contactDeveloper() {
        navTo(Nav.Settings.ContactSupport)
    }

    data class State(
        val isRecording: Boolean,
        val logPath: File?,
        val storedStats: DebugLogStore.Stats? = null,
    )
}
