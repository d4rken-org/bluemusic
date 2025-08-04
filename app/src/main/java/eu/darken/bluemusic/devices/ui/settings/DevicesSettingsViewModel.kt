package eu.darken.bluemusic.devices.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class DevicesSettingsViewModel
@Inject
constructor(
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val devicesSettings: DevicesSettings,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider, logTag("Settings", "Devices", "ViewModel"), navCtrl) {

    val state = combine(
        upgradeRepo.upgradeInfo,
        devicesSettings.isEnabled.flow,
        devicesSettings.restoreOnBoot.flow,
    ) { upgradeInfo, isEnabled, restoreOnBoot ->
        State(
            isUpgraded = upgradeInfo.isUpgraded,
            isEnabled = isEnabled,
            restoreOnBoot = restoreOnBoot,
        )
    }.asStateFlow()

    fun upgrade() = launch {
        log(tag) { "upgrade()" }
        navTo(Nav.Main.Upgrade)
    }

    fun onToggleEnabled(enabled: Boolean) = launch {
        log(tag) { "onToggleEnabled($enabled)" }
        devicesSettings.isEnabled.value(enabled)
    }

    fun onToggleRestoreOnBoot(enabled: Boolean) = launch {
        log(tag) { "onToggleRestoreOnBoot($enabled)" }
        devicesSettings.restoreOnBoot.value(enabled)
    }


    data class State(
        val isUpgraded: Boolean,
        val isEnabled: Boolean,
        val restoreOnBoot: Boolean,
    )
}
