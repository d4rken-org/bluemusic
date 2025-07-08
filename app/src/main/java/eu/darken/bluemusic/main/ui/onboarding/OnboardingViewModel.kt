package eu.darken.bluemusic.main.ui.onboarding

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.BlueMusicLinks
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.WebpageTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.main.core.GeneralSettings
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject


@HiltViewModel
class OnboardingViewModel @Inject constructor(
    dispatchers: DispatcherProvider,
    navCtrl: NavigationController,
    private val generalSettings: GeneralSettings,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatchers, logTag("Onboarding", "Screen", "VM"), navCtrl) {

    val state = combine(
        generalSettings.isOnboardingCompleted.flow,
        flowOf(Unit),
    ) { isCompleted, _ ->
        State()
    }.asStateFlow()

    fun completeOnboarding() = launch {
        log(tag) { "completeOnboarding()" }
        generalSettings.isOnboardingCompleted.value(true)
        navTo(
            Nav.Main.ManageDevices,
            popUpTo = Nav.Main.ManageDevices,
            inclusive = true
        )
    }

    fun readPrivacyPolicy() = launch {
        log(tag) { "readPrivacyPolicy()" }
        webpageTool.open(BlueMusicLinks.PRIVACY_POLICY)
    }

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
