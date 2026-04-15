package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.common.upgrade.isPro

enum class ToggleResult { SUCCESS, NOT_PRO, NOT_MANAGED }

suspend fun DeviceRepo.toggleVolumeLock(address: DeviceAddr, upgradeRepo: UpgradeRepo): ToggleResult {
    if (!upgradeRepo.isPro()) return ToggleResult.NOT_PRO
    if (!isManaged(address)) return ToggleResult.NOT_MANAGED

    updateDevice(address) { config ->
        config.copy(
            volumeLock = !config.volumeLock,
        )
    }
    return ToggleResult.SUCCESS
}
