package eu.darken.bluemusic.main.ui.settings.support

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.recorder.core.DebugSessionManager
import eu.darken.bluemusic.common.debug.recorder.core.DebugSessionManager.DebugSession
import eu.darken.bluemusic.common.debug.recorder.core.RecorderModule
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SupportScreenViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val webpageTool: WebpageTool,
    private val debugSessionManager: DebugSessionManager,
) : ViewModel4(dispatcherProvider, logTag("Settings", "Support", "ViewModel"), navCtrl) {

    val state = debugSessionManager.sessions.map { sessions ->
        val readySessions = sessions.filterIsInstance<DebugSession.Ready>()
        State(
            isRecording = sessions.any { it is DebugSession.Recording },
            sessions = sessions,
            storedStats = DebugSessionManager.Stats(
                sessionCount = readySessions.size,
                totalSize = readySessions.sumOf { it.zipSize },
            ),
        )
    }.asStateFlow()

    val events = SingleEventFlow<SupportEvent>()
    val snackbarEvent = SingleEventFlow<String>()

    sealed interface SupportEvent {
        data object ShowShortRecordingWarning : SupportEvent
        data class OpenSession(val path: String) : SupportEvent
    }

    fun startDebugLog() = launch {
        log(tag) { "Starting debug log recording" }
        debugSessionManager.startRecording()
    }

    fun stopDebugLog() = launch {
        log(tag) { "Requesting stop debug log recording" }
        when (val result = debugSessionManager.requestStopRecording()) {
            is RecorderModule.StopResult.TooShort -> {
                log(tag) { "Recording too short: ${result.durationSeconds}s" }
                events.emit(SupportEvent.ShowShortRecordingWarning)
            }

            is RecorderModule.StopResult.Stopped -> {
                log(tag) { "Recording stopped: ${result.logDir}" }
            }

            is RecorderModule.StopResult.NotRecording -> {
                log(tag) { "Was not recording" }
            }
        }
    }

    fun confirmStopDebugLog() = launch {
        log(tag) { "Force stopping debug log recording" }
        debugSessionManager.stopRecording()
    }

    fun openSession(session: DebugSession.Ready) = launch {
        log(tag) { "Opening session: ${session.id}" }
        events.emit(SupportEvent.OpenSession(session.dir.path))
    }

    fun deleteSession(session: DebugSession) = launch {
        log(tag) { "Deleting session: ${session.id}" }
        debugSessionManager.deleteSession(session)
    }

    fun openUrl(url: String) = launch {
        log(tag) { "Opening URL: $url" }
        webpageTool.open(url)
    }

    fun contactDeveloper() {
        navTo(Nav.Settings.ContactSupport)
    }

    data class State(
        val isRecording: Boolean,
        val sessions: List<DebugSession> = emptyList(),
        val storedStats: DebugSessionManager.Stats? = null,
    )
}
