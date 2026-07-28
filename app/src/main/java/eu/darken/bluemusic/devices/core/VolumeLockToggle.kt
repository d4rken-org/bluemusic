package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.common.upgrade.isProForUi

enum class ToggleResult { SUCCESS, NOT_PRO, NOT_MANAGED }

// Both call paths (device config screen, Glance widget action) are user taps, so this uses the UI
// gate: a paying user must not be told "not pro" just because billing hasn't settled yet.
suspend fun DeviceRepo.toggleVolumeLock(address: DeviceAddr, upgradeRepo: UpgradeRepo): ToggleResult {
    if (!upgradeRepo.isProForUi()) return ToggleResult.NOT_PRO
    if (!isManaged(address)) return ToggleResult.NOT_MANAGED

    updateDevice(address) { config ->
        config.copy(
            volumeLock = !config.volumeLock,
        )
    }
    return ToggleResult.SUCCESS
}
