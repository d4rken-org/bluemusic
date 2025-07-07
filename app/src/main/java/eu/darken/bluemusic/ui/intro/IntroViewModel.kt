package eu.darken.bluemusic.ui.intro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import eu.darken.bluemusic.common.architecture.BaseViewModel
import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import eu.darken.bluemusic.settings.core.Settings
import eu.darken.bluemusic.util.ApiHelper
import timber.log.Timber
import javax.inject.Inject

data class IntroState(
    val needsBluetoothPermission: Boolean = false,
    val shouldClose: Boolean = false
)

sealed interface IntroEvent {
    data object OnFinishOnboarding : IntroEvent
    data object OnPermissionGranted : IntroEvent
    data object OnPermissionDenied : IntroEvent
    data object OnPrivacyPolicyClicked : IntroEvent
}

class IntroViewModel @Inject constructor(
    private val context: Context,
    private val settings: Settings,
    private val dispatcherProvider: DispatcherProvider
) : BaseViewModel<IntroState, IntroEvent>(IntroState()) {
    
    override fun onEvent(event: IntroEvent) {
        when (event) {
            is IntroEvent.OnFinishOnboarding -> finishOnboarding()
            is IntroEvent.OnPermissionGranted -> onPermissionGranted()
            is IntroEvent.OnPermissionDenied -> onPermissionDenied()
            is IntroEvent.OnPrivacyPolicyClicked -> {} // Handled in UI
        }
    }
    
    private fun finishOnboarding() {
        if (ApiHelper.hasAndroid12() && 
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
            != PackageManager.PERMISSION_GRANTED) {
            Timber.w("BLUETOOTH_CONNECT permission is missing")
            updateState { copy(needsBluetoothPermission = true) }
            return
        }
        
        completeOnboarding()
    }
    
    private fun onPermissionGranted() {
        updateState { copy(needsBluetoothPermission = false) }
        completeOnboarding()
    }
    
    private fun onPermissionDenied() {
        Timber.w("Permission was not granted")
        updateState { copy(needsBluetoothPermission = false) }
    }
    
    private fun completeOnboarding() {
        Timber.i("Setup is complete")
        settings.isShowOnboarding = false
        updateState { copy(shouldClose = true) }
    }
}