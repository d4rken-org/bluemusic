package eu.darken.bluemusic.main.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.architecture.BaseViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.main.core.Settings
import eu.darken.bluemusic.common.ApiHelper
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.main.core.GeneralSettings
import javax.inject.Inject


@HiltViewModel
class OnboardingViewModel @Inject constructor(
    dispatchers: DispatcherProvider,
    navCtrl: NavigationController,
    private val generalSettings: GeneralSettings,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatchers, logTag("Onboarding", "Screen", "VM"), navCtrl) {

    data class State(
        val startPage: Page = Page.WELCOME,
        val isBeta: Boolean = BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.RELEASE,
    ) {

        enum class Page {
            WELCOME,
            BETA,
            PRIVACY,
            ;
        }
    }

//    override fun onEvent(event: IntroEvent) {
//        when (event) {
//            is IntroEvent.OnFinishOnboarding -> finishOnboarding()
//            is IntroEvent.OnPermissionGranted -> onPermissionGranted()
//            is IntroEvent.OnPermissionDenied -> onPermissionDenied()
//            is IntroEvent.OnPrivacyPolicyClicked -> {} // Handled in UI
//        }
//    }
//
//    private fun finishOnboarding() {
//        if (ApiHelper.hasAndroid12() &&
//            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
//            != PackageManager.PERMISSION_GRANTED) {
//            log(TAG, WARN) { "BLUETOOTH_CONNECT permission is missing" }
//            updateState { copy(needsBluetoothPermission = true) }
//            return
//        }
//
//        completeOnboarding()
//    }
//
//    private fun onPermissionGranted() {
//        updateState { copy(needsBluetoothPermission = false) }
//        completeOnboarding()
//    }
//
//    private fun onPermissionDenied() {
//        log(TAG, WARN) { "Permission was not granted" }
//        updateState { copy(needsBluetoothPermission = false) }
//    }
//
//    private fun completeOnboarding() {
//        log(TAG, INFO) { "Setup is complete" }
//        settings.isShowOnboarding = false
//        updateState { copy(shouldClose = true) }
//    }
}
