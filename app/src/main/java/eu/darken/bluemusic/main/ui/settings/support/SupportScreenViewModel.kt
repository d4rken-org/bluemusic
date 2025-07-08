package eu.darken.bluemusic.main.ui.settings.support

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.recorder.core.RecorderModule
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
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

    fun debugLog() = launch {
        val currentState = recorderModule.state.map { it.isRecording }.first()
        if (currentState) {
            log(tag) { "Stopping debug log recording" }
            recorderModule.stopRecorder()
        } else {
            log(tag) { "Starting debug log recording" }
            recorderModule.startRecorder()
        }
    }

    fun openUrl(url: String) = launch {
        log(tag) { "Opening URL: $url" }
        webpageTool.open(url)
    }

    data class State(
        val isRecording: Boolean,
        val logPath: File?,
    )

}
