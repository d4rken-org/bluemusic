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
        devicesSettings.visibleAdjustments.flow,
        devicesSettings.restoreOnBoot.flow,
        devicesSettings.autoplayKeycodes.flow,
    ) { upgradeInfo, isEnabled, visibleAdjustments, restoreOnBoot, autoplayKeycodes ->
        State(
            isUpgraded = upgradeInfo.isUpgraded,
            isEnabled = isEnabled,
            visibleAdjustments = visibleAdjustments,
            restoreOnBoot = restoreOnBoot,
            autoplayKeycodes = autoplayKeycodes,
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

    fun onToggleVisibleVolumeAdjustments(enabled: Boolean) = launch {
        log(tag) { "onToggleVisibleVolumeAdjustments($enabled)" }
        devicesSettings.visibleAdjustments.value(enabled)
    }

    fun onToggleRestoreOnBoot(enabled: Boolean) = launch {
        log(tag) { "onToggleRestoreOnBoot($enabled)" }
        devicesSettings.restoreOnBoot.value(enabled)
    }

    fun onAutoplayKeycodesClicked() {
        log(tag) { "onAutoplayKeycodesClicked()" }
        // The dialog is shown directly in the UI, this is just for logging
    }

    fun onAutoplayKeycodesChanged(keycodes: List<Int>) = launch {
        log(tag) { "onAutoplayKeycodesChanged($keycodes)" }
        devicesSettings.autoplayKeycodes.update { keycodes }
    }

    data class State(
        val isUpgraded: Boolean,
        val isEnabled: Boolean,
        val visibleAdjustments: Boolean,
        val restoreOnBoot: Boolean,
        val autoplayKeycodes: List<Int>,
    )
}
