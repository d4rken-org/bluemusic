package eu.darken.bluemusic.main.ui.settings.support

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.recorder.core.DebugSession
import eu.darken.bluemusic.common.debug.recorder.core.DebugSessionManager
import eu.darken.bluemusic.common.debug.recorder.core.RecorderModule
import eu.darken.bluemusic.common.flow.DynamicStateFlow
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SupportScreenViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val webpageTool: WebpageTool,
    private val sessionManager: DebugSessionManager,
) : ViewModel4(dispatcherProvider, logTag("Settings", "Support", "ViewModel"), navCtrl) {

    data class State(
        val isRecording: Boolean = false,
        val currentLogPath: File? = null,
        val recordingStartedAt: Long = 0L,
        val sessions: List<DebugSession> = emptyList(),
    ) {
        val logSessionCount: Int get() = sessions.count { it !is DebugSession.Recording }
        val logFolderSize: Long get() = sessions.sumOf { it.diskSize }
        val failedSessions: List<DebugSession.Failed> get() = sessions.filterIsInstance<DebugSession.Failed>()
    }

    sealed interface Event {
        data object ShowConsentDialog : Event
        data object ShowShortRecordingWarning : Event
        data class OpenRecorderActivity(val sessionId: String, val legacyPath: String?) : Event
    }

    val events = SingleEventFlow<Event>()

    private val stater = DynamicStateFlow(tag, vmScope) { State() }
    val state = stater.flow

    init {
        combine(
            sessionManager.recorderState,
            sessionManager.sessions,
        ) { recorderState, sessions ->
            stater.updateBlocking {
                copy(
                    isRecording = recorderState.isRecording,
                    currentLogPath = recorderState.currentLogPath,
                    recordingStartedAt = recorderState.recordingStartedAt,
                    sessions = sessions,
                )
            }
        }.launchIn(vmScope)
    }

    fun openUrl(url: String) {
        webpageTool.open(url)
    }

    fun contactDeveloper() {
        navTo(Nav.Settings.ContactSupport)
    }

    fun onDebugLogToggle() = launch {
        if (stater.value().isRecording) {
            doStopDebugLog()
        } else {
            events.tryEmit(Event.ShowConsentDialog)
        }
    }

    fun startDebugLog() = launch {
        log(tag) { "startDebugLog()" }
        sessionManager.startRecording()
    }

    fun stopDebugLog() = launch {
        doStopDebugLog()
    }

    private suspend fun doStopDebugLog() {
        when (val result = sessionManager.requestStopRecording()) {
            is RecorderModule.StopResult.TooShort -> events.tryEmit(Event.ShowShortRecordingWarning)
            is RecorderModule.StopResult.Stopped -> {
                log(tag) { "stopDebugLog() -> ${result.sessionId}" }
                events.tryEmit(Event.OpenRecorderActivity(result.sessionId, result.logDir.path))
            }
            is RecorderModule.StopResult.NotRecording -> {}
        }
    }

    fun forceStopDebugLog() = launch {
        log(tag) { "forceStopDebugLog()" }
        val result = sessionManager.forceStopRecording()
        if (result != null) {
            events.tryEmit(Event.OpenRecorderActivity(result.sessionId, result.logDir.path))
        }
    }

    fun clearDebugLogs() = launch {
        log(tag) { "clearDebugLogs()" }
        sessionManager.deleteAllSessions()
    }

    fun openSession(sessionId: String) = launch {
        val session = sessionManager.sessions.first().firstOrNull { it.id == sessionId } ?: return@launch
        val legacyPath = (session as? DebugSession.Ready)?.logDir?.path
        events.tryEmit(Event.OpenRecorderActivity(sessionId, legacyPath))
    }

    fun refreshSessions() = launch {
        sessionManager.refresh()
    }

    fun deleteSession(id: String) = launch {
        log(tag) { "deleteSession($id)" }
        sessionManager.deleteSession(id)
    }

    companion object {
        private val TAG = logTag("Settings", "Support", "VM")
    }
}
