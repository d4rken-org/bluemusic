package eu.darken.bluemusic.ui.settings

import android.app.Activity
import android.view.KeyEvent
import eu.darken.bluemusic.common.architecture.BaseViewModel
import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import eu.darken.bluemusic.settings.core.Settings
import eu.darken.bluemusic.util.iap.IAPRepo
import kotlinx.coroutines.flow.catch
import timber.log.Timber
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
    private val iapRepo: IAPRepo,
    private val dispatcherProvider: DispatcherProvider
) : BaseViewModel<SettingsState, SettingsEvent>(SettingsState()) {
    
    init {
        observeProVersion()
        loadSettings()
    }
    
    private fun observeProVersion() {
        launch {
            iapRepo.recheck()
            iapRepo.isProVersion()
                .catch { e ->
                    Timber.e(e, "Failed to observe pro version")
                }
                .collect { isProVersion ->
                    updateState { copy(isProVersion = isProVersion) }
                }
        }
    }
    
    private fun loadSettings() {
        updateState {
            copy(
                bugreportingEnabled = settings.isBugreportingEnabled,
                visibleAdjustments = settings.isVisibleAdjustments,
                speakerAutosave = settings.isSpeakerAutoSave,
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
        
        val newValue = !settings.isVisibleAdjustments
        settings.isVisibleAdjustments = newValue
        updateState { copy(visibleAdjustments = newValue) }
    }
    
    private fun toggleSpeakerAutosave() {
        if (!currentState.isProVersion) {
            updateState { copy(showUpgradeDialog = true) }
            return
        }
        
        val newValue = !settings.isSpeakerAutoSave
        settings.isSpeakerAutoSave = newValue
        updateState { copy(speakerAutosave = newValue) }
    }
    
    private fun toggleBugreporting(enabled: Boolean) {
        settings.isBugreportingEnabled = enabled
        updateState { copy(bugreportingEnabled = enabled) }
    }
    
    private fun updateAutoplayKeycode(keycode: Int) {
        settings.autoplayKeycode = keycode
        updateState { copy(autoplayKeycode = keycode) }
    }
    
    private fun purchaseUpgrade(activity: Activity) {
        launch {
            iapRepo.buyProVersion(activity)
            dismissDialog()
        }
    }
    
    private fun dismissDialog() {
        updateState { copy(showUpgradeDialog = false) }
    }
}