package eu.darken.bluemusic.devices.ui.dashboard

import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeMode

sealed interface DashboardAction {

    data class AdjustVolume(
        val addr: DeviceAddr,
        val type: AudioStream.Type,
        val volumeMode: VolumeMode
    ) : DashboardAction

    data object RequestBluetoothPermission : DashboardAction

    data object RequestNotificationPermission : DashboardAction

    data object DismissBatteryOptimizationHint : DashboardAction

    data object DismissAndroid10AppLaunchHint : DashboardAction

    data object DismissNotificationPermissionHint : DashboardAction

    data class ToggleAdjustmentLock(val addr: DeviceAddr) : DashboardAction
}