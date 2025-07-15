package eu.darken.bluemusic.devices.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
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
        devicesSettings.visibleAdjustments.flow,
        devicesSettings.restoreOnBoot.flow,
    ) { upgradeInfo, isEnabled, visibleAdjustments, restoreOnBoot ->
        State(
            isUpgraded = upgradeInfo.isUpgraded,
            isEnabled = isEnabled,
            visibleAdjustments = visibleAdjustments,
            restoreOnBoot = restoreOnBoot,
        )
    }.asStateFlow()

    fun upgradeButler() = launch {
        log(tag) { "upgradeButler()" }
        navTo(Nav.Main.Upgrade)
    }

    fun onToggleEnabled(enabled: Boolean) {
        log(tag) { "onToggleEnabled($enabled)" }
    }

    fun onToggleVisibleVolumeAdjustments(enabled: Boolean) {
        log(tag) { "onToggleVisibleVolumeAdjustments($enabled)" }
    }

    fun onToggleVolumeListening(enabled: Boolean) {
        log(tag) { "onToggleVolumeListening($enabled)" }
    }

    fun onToggleRestoreOnBoot(enabled: Boolean) {
        log(tag) { "onToggleRestoreOnBoot($enabled)" }
    }

    data class State(
        val isUpgraded: Boolean,
        val isEnabled: Boolean,
        val visibleAdjustments: Boolean,
        val restoreOnBoot: Boolean,
    )
}
