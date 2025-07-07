package eu.darken.bluemusic.main.ui.settings

import android.app.Activity
import android.view.KeyEvent
import eu.darken.bluemusic.common.architecture.BaseViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.main.core.Settings
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import javax.inject.Inject

data class SettingsState(
    val isProVersion: Boolean = false,
    val bugreportingEnabled: Boolean = false,
    val visibleAdjustments: Boolean = false,
    val speakerAutosave: Boolean = false,
    val autoplayKeycode: Int = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    val showUpgradeDialog: Boolean = false
)

sealed interface SettingsEvent {
    data object OnVisibleAdjustmentsToggled : SettingsEvent
    data object OnSpeakerAutosaveToggled : SettingsEvent
    data class OnBugreportingToggled(val enabled: Boolean) : SettingsEvent
    data class OnAutoplayKeycodeSelected(val keycode: Int) : SettingsEvent
    data object OnShowAutoplayKeycodeDialog : SettingsEvent
    data object OnAdvancedSettingsClicked : SettingsEvent
    data object OnAboutClicked : SettingsEvent
    data class OnPurchaseUpgrade(val activity: Activity) : SettingsEvent
    data object OnDismissDialog : SettingsEvent
}

class SettingsViewModel @Inject constructor(
    private val settings: Settings,
    private val upgradeRepo: UpgradeRepo,
    private val dispatcherProvider: DispatcherProvider
) : BaseViewModel<SettingsState, SettingsEvent>(SettingsState()) {

    companion object {
        private val TAG = logTag("SettingsViewModel")
    }
    
    init {
        observeProVersion()
        loadSettings()
    }
    
    private fun observeProVersion() {
//        launch {
//            iapRepo.recheck()
//            iapRepo.isProVersion
//                .catch { e ->
//                    log(TAG, ERROR) { "Failed to observe pro version: ${e.asLog()}" }
//                }
//                .collect { isProVersion ->
//                    updateState { copy(isProVersion = isProVersion) }
//                }
//        }
    }
    
    private fun loadSettings() {
        updateState {
            copy(
                bugreportingEnabled = settings.isBugReportingEnabled,
                visibleAdjustments = settings.isVolumeAdjustedVisibly,
                speakerAutosave = settings.isSpeakerAutoSaveEnabled,
                autoplayKeycode = settings.autoplayKeycode
            )
        }
    }
    
    override fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.OnVisibleAdjustmentsToggled -> toggleVisibleAdjustments()
            is SettingsEvent.OnSpeakerAutosaveToggled -> toggleSpeakerAutosave()
            is SettingsEvent.OnBugreportingToggled -> toggleBugreporting(event.enabled)
            is SettingsEvent.OnAutoplayKeycodeSelected -> updateAutoplayKeycode(event.keycode)
            is SettingsEvent.OnShowAutoplayKeycodeDialog -> {} // Handled in UI
            is SettingsEvent.OnAdvancedSettingsClicked -> {} // Handled in UI
            is SettingsEvent.OnAboutClicked -> {} // Handled in UI
            is SettingsEvent.OnPurchaseUpgrade -> purchaseUpgrade(event.activity)
            is SettingsEvent.OnDismissDialog -> dismissDialog()
        }
    }
    
    private fun toggleVisibleAdjustments() {
        if (!currentState.isProVersion) {
            updateState { copy(showUpgradeDialog = true) }
            return
        }

        val newValue = !settings.isVolumeAdjustedVisibly
        // TODO: settings.setVolumeAdjustedVisibly(newValue)
        updateState { copy(visibleAdjustments = newValue) }
    }
    
    private fun toggleSpeakerAutosave() {
        if (!currentState.isProVersion) {
            updateState { copy(showUpgradeDialog = true) }
            return
        }

        val newValue = !settings.isSpeakerAutoSaveEnabled
        // TODO: settings.setSpeakerAutoSaveEnabled(newValue)
        updateState { copy(speakerAutosave = newValue) }
    }
    
    private fun toggleBugreporting(enabled: Boolean) {
        // TODO: settings.setBugReportingEnabled(enabled)
        updateState { copy(bugreportingEnabled = enabled) }
    }
    
    private fun updateAutoplayKeycode(keycode: Int) {
        settings.autoplayKeycode = keycode
        updateState { copy(autoplayKeycode = keycode) }
    }
    
    private fun purchaseUpgrade(activity: Activity) {
        launch {
            // TODO: Implement purchase flow
            // iapRepo.startIAPFlow(AvailableSkus.PRO_VERSION, activity)
            dismissDialog()
        }
    }
    
    private fun dismissDialog() {
        updateState { copy(showUpgradeDialog = false) }
    }
}
