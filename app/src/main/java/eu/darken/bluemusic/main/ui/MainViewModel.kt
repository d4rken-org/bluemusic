package eu.darken.bluemusic.main.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.datastore.valueBlocking
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.theming.ThemeState
import eu.darken.bluemusic.common.theming.themeState
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.main.core.GeneralSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val upgradeRepo: UpgradeRepo,
    private val generalSettings: GeneralSettings,
) : ViewModel4(dispatcherProvider, logTag("Main", "Screen", "VM"), navCtrl) {

    val themeState: StateFlow<ThemeState> = generalSettings.themeState.stateIn(
        vmScope,
        SharingStarted.Eagerly,
        ThemeState(
            mode = generalSettings.themeMode.valueBlocking,
            style = generalSettings.themeStyle.valueBlocking,
            color = generalSettings.themeColor.valueBlocking,
        )
    )


    val state = combine(
        generalSettings.isOnboardingCompleted.flow,
        flowOf(Unit),
    ) { onBoardingComplete, _ ->
        State(
            startScreen = when {
                !onBoardingComplete -> State.StartScreen.ONBOARDING
                else -> State.StartScreen.HOME
            },
        )
    }
        .onEach { log(VERBOSE) { "New state: $it" } }
        .asStateFlow()

    fun checkUpgrades() = launch {
        log(tag) { "checkUpgrades()" }
        upgradeRepo.refresh()
    }

    data class State(
        val startScreen: StartScreen = StartScreen.ONBOARDING,
    ) {
        enum class StartScreen {
            ONBOARDING,
            HOME,
            ;
        }
    }
}
