package eu.darken.bluemusic.main.ui.settings.support.contact

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.BlueMusicId
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.recorder.core.DebugLogStore
import eu.darken.bluemusic.common.flow.DynamicStateFlow
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class ContactSupportViewModel @Inject constructor(
    navCtrl: NavigationController,
    dispatcherProvider: DispatcherProvider,
    @param:ApplicationContext private val context: Context,
    private val debugLogStore: DebugLogStore,
    private val blueMusicId: BlueMusicId,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider, logTag("Contact", "Support", "VM"), navCtrl) {

    private val stater = DynamicStateFlow(tag, vmScope) {
        State()
    }
    val state: Flow<State> = stater.flow

    val sendEvent = SingleEventFlow<Intent>()
    val snackbarEvent = SingleEventFlow<String>()

    init {
        launch { loadSessions() }
    }

    private suspend fun loadSessions() {
        val sessions = debugLogStore.getSessions()
        stater.updateBlocking { copy(logSessions = sessions) }
    }

    fun selectCategory(category: ContactCategory) = launch {
        stater.updateBlocking { copy(category = category) }
    }

    fun updateDescription(text: String) = launch {
        stater.updateBlocking { copy(description = text) }
    }

    fun selectLogSession(session: DebugLogStore.LogSession?) = launch {
        stater.updateBlocking { copy(selectedLogSession = session) }
    }

    fun send() = launch {
        val currentState = stater.value()
        if (!currentState.canSend) return@launch

        val category = currentState.category!!
        val description = currentState.description.trim()

        val subjectPreview = description
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .take(8)
            .joinToString(" ")
            .replace("\n", " ")

        val subject = "[BVM][${category.subjectTag}] $subjectPreview".take(72)

        val installId = try {
            blueMusicId.id
        } catch (e: IllegalStateException) {
            "unavailable"
        }

        val upgradeInfo = try {
            upgradeRepo.upgradeInfo.first()
        } catch (e: Exception) {
            null
        }

        val bodyBuilder = StringBuilder()
        bodyBuilder.appendLine(description)

        bodyBuilder.appendLine()
        bodyBuilder.appendLine("--- Device info ---")
        bodyBuilder.appendLine("App: ${BuildConfigWrap.VERSION_DESCRIPTION}")
        bodyBuilder.appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        bodyBuilder.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        bodyBuilder.appendLine("Upgraded: ${upgradeInfo?.isUpgraded ?: "unknown"}")
        bodyBuilder.appendLine("Install ID: $installId")

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@darken.eu"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, bodyBuilder.toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val selectedSession = currentState.selectedLogSession
        if (selectedSession != null) {
            val attachFile = selectedSession.zipFile ?: selectedSession.dir
            if (attachFile.exists() && attachFile.canRead()) {
                val uri = FileProvider.getUriForFile(
                    context,
                    BuildConfigWrap.APPLICATION_ID + ".provider",
                    attachFile,
                )
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                log(tag) { "Attachment file missing: $attachFile" }
                snackbarEvent.emit(context.getString(eu.darken.bluemusic.R.string.contact_attachment_missing_msg))
                return@launch
            }
        }

        val chooser = Intent.createChooser(intent, context.getString(eu.darken.bluemusic.R.string.contact_send_action))
        if (chooser.resolveActivity(context.packageManager) != null || intent.resolveActivity(context.packageManager) != null) {
            sendEvent.emit(chooser)
        } else {
            snackbarEvent.emit(context.getString(eu.darken.bluemusic.R.string.contact_no_email_app_msg))
        }
    }

    data class State(
        val category: ContactCategory? = ContactCategory.QUESTION,
        val description: String = "",
        val logSessions: List<DebugLogStore.LogSession> = emptyList(),
        val selectedLogSession: DebugLogStore.LogSession? = null,
    ) {
        val descriptionWordCount: Int
            get() = description.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size

        val descriptionCharCount: Int
            get() = description.trim().length

        val descriptionValid: Boolean
            get() = descriptionWordCount >= 20 || descriptionCharCount >= 60

        val canSend: Boolean
            get() = category != null && descriptionValid
    }

    companion object {
        private val TAG = logTag("Contact", "Support", "VM")
    }
}
