package eu.darken.bluemusic.main.ui.settings.support

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.recorder.core.RecorderModule
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SupportScreenViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val webpageTool: WebpageTool,
    private val recorderModule: RecorderModule,
) : ViewModel4(dispatcherProvider, logTag("Settings", "Support", "ViewModel"), navCtrl) {

    val state = recorderModule.state.map { recState ->
        State(
            isRecording = recState.isRecording,
            logPath = recState.currentLogDir,
        )
    }

    val shortRecordingWarningEvent = SingleEventFlow<Unit>()

    private var stopInFlight = false

    fun debugLog() = launch {
        val recState = recorderModule.state.first()
        if (recState.isRecording) {
            if (stopInFlight) return@launch

            val startedAt = recState.recordingStartedAt
            if (startedAt != null && Duration.between(startedAt, Instant.now()).seconds < SHORT_RECORDING_THRESHOLD_SECS) {
                log(tag) { "Recording is very short, showing warning" }
                stopInFlight = true
                shortRecordingWarningEvent.emit(Unit)
            } else {
                log(tag) { "Stopping debug log recording" }
                recorderModule.stopRecorder()
            }
        } else {
            log(tag) { "Starting debug log recording" }
            recorderModule.startRecorder()
        }
    }

    fun forceStopDebugLog() = launch {
        log(tag) { "Force stopping debug log recording" }
        stopInFlight = false
        recorderModule.stopRecorder()
    }

    fun cancelStopWarning() {
        log(tag) { "User chose to continue recording" }
        stopInFlight = false
    }

    fun openUrl(url: String) = launch {
        log(tag) { "Opening URL: $url" }
        webpageTool.open(url)
    }

    data class State(
        val isRecording: Boolean,
        val logPath: File?,
    )

    companion object {
        private const val SHORT_RECORDING_THRESHOLD_SECS = 5L
    }

}
