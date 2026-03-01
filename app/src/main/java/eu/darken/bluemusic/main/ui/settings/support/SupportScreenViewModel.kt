package eu.darken.bluemusic.main.ui.settings.support

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.recorder.core.DebugLogStore
import eu.darken.bluemusic.common.debug.recorder.core.RecorderModule
import eu.darken.bluemusic.common.flow.DynamicStateFlow
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val debugLogStore: DebugLogStore,
) : ViewModel4(dispatcherProvider, logTag("Settings", "Support", "ViewModel"), navCtrl) {

    private val stater = DynamicStateFlow(tag, vmScope) {
        State(
            isRecording = false,
            logPath = null,
        )
    }

    val state: Flow<State> = combine(
        stater.flow,
        recorderModule.state,
    ) { uiState, recState ->
        uiState.copy(
            isRecording = recState.isRecording,
            logPath = recState.currentLogDir,
        )
    }

    val snackbarEvent = SingleEventFlow<String>()

    init {
        launch { refreshStoredStats() }
    }

    private suspend fun refreshStoredStats() {
        val stats = debugLogStore.getStats()
        stater.updateBlocking { copy(storedStats = stats) }
    }

    fun debugLog() = launch {
        val currentState = recorderModule.state.map { it.isRecording }.first()
        if (currentState) {
            log(tag) { "Stopping debug log recording" }
            recorderModule.stopRecorder()
            refreshStoredStats()
        } else {
            log(tag) { "Starting debug log recording" }
            recorderModule.startRecorder()
        }
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
