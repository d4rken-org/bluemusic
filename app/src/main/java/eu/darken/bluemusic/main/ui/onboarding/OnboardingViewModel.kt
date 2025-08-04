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
import eu.darken.bluemusic.main.core.LegacySettings
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject


@HiltViewModel
class OnboardingViewModel @Inject constructor(
    dispatchers: DispatcherProvider,
    navCtrl: NavigationController,
    private val generalSettings: GeneralSettings,
    private val legacySettings: LegacySettings,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatchers, logTag("Onboarding", "Screen", "VM"), navCtrl) {

    val state = combine(
        generalSettings.isOnboardingCompleted.flow,
        flowOf(Unit),
    ) { isCompleted, _ ->
        val isExistingUser = try {
            legacySettings.getInstallTime() < System.currentTimeMillis() - 1000
        } catch (e: Exception) {
            false
        }

        val pages = mutableListOf<State.Page>()
        pages.add(State.Page.WELCOME)

        // Show rewrite page only for existing users
        if (isExistingUser) {
            pages.add(State.Page.REWRITE)
        }

        if (BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.RELEASE) {
            pages.add(State.Page.BETA)
        }

        pages.add(State.Page.PRIVACY)

        State(
            pages = pages,
            startPage = pages.first(),
            isBeta = BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.RELEASE,
        )
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
        val pages: List<Page> = emptyList(),
        val startPage: Page = Page.WELCOME,
        val isBeta: Boolean = BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.RELEASE,
    ) {

        enum class Page {
            WELCOME,
            REWRITE,
            BETA,
            PRIVACY,
            ;
        }
    }
}
