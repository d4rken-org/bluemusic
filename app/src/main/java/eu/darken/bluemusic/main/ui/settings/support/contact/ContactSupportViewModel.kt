package eu.darken.bluemusic.main.ui.settings.support.contact

import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.BlueMusicLinks
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.EmailTool
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.recorder.core.DebugSession
import eu.darken.bluemusic.common.debug.recorder.core.DebugSessionManager
import eu.darken.bluemusic.common.debug.recorder.core.RecorderModule
import eu.darken.bluemusic.common.flow.DynamicStateFlow
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject

@HiltViewModel
class ContactSupportViewModel @Inject constructor(
    navCtrl: NavigationController,
    dispatcherProvider: DispatcherProvider,
    @param:ApplicationContext private val context: Context,
    private val sessionManager: DebugSessionManager,
    private val emailTool: EmailTool,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider, logTag("Contact", "Support", "VM"), navCtrl) {

    data class State(
        val category: ContactCategory = ContactCategory.QUESTION,
        val description: String = "",
        val expectedBehavior: String = "",
        val isSending: Boolean = false,
        val isRecording: Boolean = false,
        val recordingStartedAt: Long = 0L,
        val sessions: List<DebugSession.Ready> = emptyList(),
        val selectedSessionId: String? = null,
    ) {
        val isBug: Boolean get() = category == ContactCategory.BUG

        val descriptionWordCount: Int
            get() = description.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size

        val expectedWordCount: Int
            get() = expectedBehavior.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size

        val canSend: Boolean
            get() = descriptionWordCount >= 20
                    && (!isBug || expectedWordCount >= 10)
                    && !isSending
                    && !isRecording
    }

    sealed interface Event {
        data class OpenEmail(val intent: Intent) : Event
        data class ShowSnackbar(val message: String) : Event
        data object ShowConsentDialog : Event
        data object ShowShortRecordingWarning : Event
    }

    val events = SingleEventFlow<Event>()

    private val stater = DynamicStateFlow(tag, vmScope) { State() }
    val state: Flow<State> = stater.flow

    @Volatile private var autoSelectSessionId: String? = null

    init {
        combine(
            sessionManager.recorderState,
            sessionManager.sessions,
        ) { recorderState, allSessions ->
            val completed = allSessions.filterIsInstance<DebugSession.Ready>().take(MAX_PICKER_SESSIONS)
            stater.updateBlocking {
                val pendingAutoSelect = autoSelectSessionId
                val newSelectedId = when {
                    pendingAutoSelect != null && completed.any { it.id == pendingAutoSelect } -> {
                        autoSelectSessionId = null
                        pendingAutoSelect
                    }
                    selectedSessionId != null && completed.none { it.id == selectedSessionId } -> {
                        if (allSessions.any { it.id == selectedSessionId }) selectedSessionId else null
                    }
                    else -> selectedSessionId
                }
                copy(
                    isRecording = recorderState.isRecording,
                    recordingStartedAt = recorderState.recordingStartedAt,
                    sessions = completed,
                    selectedSessionId = newSelectedId,
                )
            }
        }.launchIn(vmScope)
    }

    fun openUrl(url: String) {
        webpageTool.open(url)
    }

    fun selectCategory(category: ContactCategory) = launch {
        stater.updateBlocking { copy(category = category) }
    }

    fun updateDescription(text: String) = launch {
        if (text.length <= 5000) {
            stater.updateBlocking { copy(description = text) }
        }
    }

    fun updateExpectedBehavior(text: String) = launch {
        if (text.length <= 5000) {
            stater.updateBlocking { copy(expectedBehavior = text) }
        }
    }

    fun selectLogSession(id: String) = launch {
        stater.updateBlocking { copy(selectedSessionId = id) }
    }

    fun deleteLogSession(id: String) = launch {
        log(tag) { "deleteLogSession($id)" }
        sessionManager.deleteSession(id)
    }

    fun refreshLogSessions() = launch {
        sessionManager.refresh()
    }

    fun startRecording() {
        events.tryEmit(Event.ShowConsentDialog)
    }

    fun doStartRecording() = launch {
        log(tag) { "doStartRecording()" }
        sessionManager.startRecording()
    }

    fun stopRecording() = launch {
        when (val result = sessionManager.requestStopRecording()) {
            is RecorderModule.StopResult.TooShort -> events.tryEmit(Event.ShowShortRecordingWarning)
            is RecorderModule.StopResult.Stopped -> {
                log(tag) { "stopRecording() -> ${result.sessionId}" }
                autoSelectSessionId = result.sessionId
            }
            is RecorderModule.StopResult.NotRecording -> {}
        }
    }

    fun forceStopRecording() = launch {
        log(tag) { "forceStopRecording()" }
        val result = sessionManager.forceStopRecording()
        if (result != null) autoSelectSessionId = result.sessionId
    }

    fun confirmSent() = launch {
        val selectedId = stater.value().selectedSessionId
        if (selectedId != null) {
            log(tag) { "confirmSent() deleting session $selectedId" }
            sessionManager.deleteSession(selectedId)
        }
        navUp()
    }

    fun send() = launch {
        val currentState = stater.value()
        if (!currentState.canSend) return@launch

        stater.updateBlocking { copy(isSending = true) }

        try {
            val attachmentUri = currentState.selectedSessionId?.let { sessionId ->
                try {
                    sessionManager.getZipUri(sessionId)
                } catch (e: Exception) {
                    log(tag) { "Failed to prepare attachment: $e" }
                    events.tryEmit(
                        Event.ShowSnackbar(context.getString(R.string.support_contact_debuglog_zip_error))
                    )
                    return@launch
                }
            }

            val category = currentState.category
            val description = currentState.description.trim()

            val subjectPreview = description
                .split("\\s+".toRegex())
                .filter { it.isNotBlank() }
                .take(8)
                .joinToString(" ")
                .replace("\n", " ")

            val subject = "[BVM][${category.subjectTag}] $subjectPreview".take(72)

            val body = buildString {
                appendLine(description)
                if (currentState.isBug && currentState.expectedBehavior.isNotBlank()) {
                    appendLine()
                    appendLine("--- Expected behavior ---")
                    appendLine(currentState.expectedBehavior.trim())
                }
                appendLine()
                appendLine("--- Device info ---")
                appendLine("App: ${BuildConfigWrap.VERSION_DESCRIPTION}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            }

            val email = EmailTool.Email(
                recipients = listOf(BlueMusicLinks.SUPPORT_EMAIL),
                subject = subject,
                body = body,
                attachment = attachmentUri,
            )

            val intent = emailTool.build(email, offerChooser = true)
            events.tryEmit(Event.OpenEmail(intent))
        } finally {
            stater.updateBlocking { copy(isSending = false) }
        }
    }

    companion object {
        private val TAG = logTag("Contact", "Support", "VM")
        private const val MAX_PICKER_SESSIONS = 3
    }
}
