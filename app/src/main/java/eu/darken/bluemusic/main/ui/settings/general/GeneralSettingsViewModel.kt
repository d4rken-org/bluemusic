package eu.darken.bluemusic.main.ui.settings.general

import android.annotation.SuppressLint
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.combine
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.common.locale.LocaleManager
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.theming.ThemeMode
import eu.darken.bluemusic.common.theming.ThemeState
import eu.darken.bluemusic.common.theming.ThemeStyle
import eu.darken.bluemusic.common.theming.themeState
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.main.core.GeneralSettings
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsViewModel
@Inject
constructor(
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val generalSettings: GeneralSettings,
    private val localeManager: LocaleManager,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider, logTag("Settings", "General", "ViewModel"), navCtrl) {

    val state = combine(
        generalSettings.themeState,
        flowOf(hasApiLevel(33)),
        upgradeRepo.upgradeInfo,
    ) { themeState, languageSwitcher, upgradeInfo ->
        State(
            themeState = themeState,
            showLanguageSwitcher = languageSwitcher,
            isUpgraded = upgradeInfo.isUpgraded,
        )
    }
        .asStateFlow()

    @SuppressLint("NewApi")
    fun showLanguagePicker() = launch {
        log(tag) { "showLanguagPicker()" }
        if (hasApiLevel(33)) {
            localeManager.showLanguagePicker()
        } else {
            throw IllegalStateException("This should not be clickable below API 33...")
        }
    }

    fun updateThemeMode(mode: ThemeMode) = launch {
        log(tag) { "updateThemeMode($mode)" }
        generalSettings.themeMode.value(mode)
    }

    fun updateThemeStyle(style: ThemeStyle) = launch {
        log(tag) { "updateThemeStyle($style)" }
        generalSettings.themeStyle.value(style)
    }

    fun upgradeButler() = launch {
        log(tag) { "upgradeButler()" }
        navTo(Nav.Main.Upgrade)
    }

    data class State(
        val themeState: ThemeState = ThemeState(),
        val filePreviews: Boolean = false,
        val showLanguageSwitcher: Boolean = false,
        val updateCheckEnabled: Boolean = false,
        val motdEnabled: Boolean = false,
        val confirmExitEnabled: Boolean = true,
        val isUpgraded: Boolean = false,
    )
}
