package eu.darken.bluemusic.devices.ui.dashboard

import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.common.apps.AppInfo
import eu.darken.bluemusic.common.apps.AppRepo
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.getDevice
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.main.core.GeneralSettings
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerTool
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val permissionHelper: PermissionHelper,
    private val deviceRepo: DeviceRepo,
    private val volumeTool: VolumeTool,
    private val ringerTool: RingerTool,
    upgradeRepo: UpgradeRepo,
    bluetoothSource: BluetoothRepo,
    private val generalSettings: GeneralSettings,
    private val devicesSettings: DevicesSettings,
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    appRepo: AppRepo,
) : ViewModel4(dispatcherProvider, logTag("Devices", "Managed", "VM"), navCtrl) {

    private val eventChannel = Channel<DashboardEvent>()
    val events = eventChannel.receiveAsFlow()

    private val devicesFlow = deviceRepo.devices

    private val batteryOptimizationHintFlow = combine(
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(1000)
            }
        },
        generalSettings.isBatteryOptimizationHintDismissed.flow
    ) { _, isDismissed ->
        permissionHelper.getBatteryOptimizationHint(isDismissed)
    }

    private val overlayPermissionHintFlow = combine(
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(1000)
            }
        },
        generalSettings.isAndroid10AppLaunchHintDismissed.flow,
        devicesFlow
    ) { _, isDismissed, devices ->
        val hasDevicesNeedingOverlay = devices.any { it.launchPkgs.isNotEmpty() || it.showHomeScreen }
        val hint = permissionHelper.getOverlayPermissionHint(isDismissed, hasDevicesNeedingOverlay)
        hint
    }

    private val notificationPermissionHintFlow = combine(
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(1000)
            }
        },
        generalSettings.isNotificationPermissionHintDismissed.flow
    ) { _, isDismissed ->
        permissionHelper.getNotificationPermissionHint(isDismissed)
    }

    private val devicesWithAppsFlow = combine(
        devicesFlow,
        appRepo.apps
    ) { devices, appInfos ->
        val appInfoMap = appInfos.associateBy { it.packageName }
        devices.map { device ->
            DeviceWithApps(
                device = device,
                launchApps = device.launchPkgs.mapNotNull { pkgName ->
                    appInfoMap[pkgName]
                }
            )
        }
    }

    val state = eu.darken.bluemusic.common.flow.combine(
        upgradeRepo.upgradeInfo,
        bluetoothSource.state,
        devicesWithAppsFlow,
        batteryOptimizationHintFlow,
        overlayPermissionHintFlow,
        notificationPermissionHintFlow,
    ) { upgradeInfo, bluetoothState, devicesWithApps, batteryHint, overlayHint, notificationHint ->
        State(
            isProVersion = upgradeInfo.isUpgraded,
            isBluetoothEnabled = bluetoothState.isEnabled,
            hasBluetoothPermission = bluetoothState.hasPermission,
            devicesWithApps = devicesWithApps,
            showBatteryOptimizationHint = batteryHint.shouldShow,
            batteryOptimizationIntent = batteryHint.intent,
            showAndroid10AppLaunchHint = overlayHint.shouldShow,
            android10AppLaunchIntent = overlayHint.intent,
            showNotificationPermissionHint = notificationHint.shouldShow,
        )
    }.asStateFlow()

    data class DeviceWithApps(
        val device: ManagedDevice,
        val launchApps: List<AppInfo>
    )

    data class State(
        val isProVersion: Boolean = false,
        val isBluetoothEnabled: Boolean = false,
        val hasBluetoothPermission: Boolean = true,
        val devicesWithApps: List<DeviceWithApps> = emptyList(),
        val isLoading: Boolean = false,
        val showBatteryOptimizationHint: Boolean = false,
        val batteryOptimizationIntent: Intent? = null,
        val showAndroid10AppLaunchHint: Boolean = false,
        val android10AppLaunchIntent: Intent? = null,
        val showNotificationPermissionHint: Boolean = false,
    ) {
        // Convenience property for backwards compatibility
        val devices: List<ManagedDevice> get() = devicesWithApps.map { it.device }
    }

    fun action(action: DashboardAction) = launch {
        log(tag) { "action: $action" }
        when (action) {
            is DashboardAction.RequestBluetoothPermission -> {
                launch {
                    val permission = permissionHelper.getBluetoothPermission()
                    eventChannel.send(DashboardEvent.RequestPermission(permission))
                }
            }

            is DashboardAction.RequestNotificationPermission -> {
                launch {
                    val permission = permissionHelper.getNotificationPermission()
                    if (permission != null) {
                        eventChannel.send(DashboardEvent.RequestPermission(permission))
                    }
                }
            }

            is DashboardAction.DismissBatteryOptimizationHint -> {
                launch {
                    generalSettings.isBatteryOptimizationHintDismissed.update { true }
                }
            }

            is DashboardAction.DismissAndroid10AppLaunchHint -> {
                launch {
                    generalSettings.isAndroid10AppLaunchHintDismissed.update { true }
                }
            }

            is DashboardAction.DismissNotificationPermissionHint -> {
                launch {
                    generalSettings.isNotificationPermissionHintDismissed.update { true }
                }
            }

            is DashboardAction.AdjustVolume -> {
                deviceRepo.updateDevice(action.addr) { oldConfig ->
                    oldConfig.updateVolume(action.type, action.volumeMode)
                }

                val device = deviceRepo.getDevice(action.addr)
                if (device?.isActive != true) return@launch

                when (action.volumeMode) {
                    is VolumeMode.Normal -> {
                        volumeTool.changeVolume(
                            streamId = device.getStreamId(action.type),
                            percent = action.volumeMode.percentage,
                            visible = devicesSettings.visibleAdjustments.value(),
                        )
                    }

                    is VolumeMode.Silent -> {
                        if (action.type == AudioStream.Type.RINGTONE || action.type == AudioStream.Type.NOTIFICATION) {
                            ringerTool.setRingerMode(RingerMode.SILENT)
                        }
                    }

                    is VolumeMode.Vibrate -> {
                        if (action.type == AudioStream.Type.RINGTONE || action.type == AudioStream.Type.NOTIFICATION) {
                            ringerTool.setRingerMode(RingerMode.VIBRATE)
                        }
                    }
                }
            }
        }
    }
}
