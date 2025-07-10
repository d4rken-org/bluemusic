package eu.darken.bluemusic.devices.ui.manage

import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.main.core.GeneralSettings
import eu.darken.bluemusic.main.core.audio.StreamHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val permissionHelper: PermissionHelper,
    private val deviceRepo: DeviceRepo,
    private val streamHelper: StreamHelper,
    private val upgradeRepo: UpgradeRepo,
    private val bluetoothSource: BluetoothRepo,
    private val generalSettings: GeneralSettings,
    private val dispatcherProvider: DispatcherProvider,
    private val navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, logTag("Devices", "Managed", "VM"), navCtrl) {

    private val eventChannel = Channel<DevicesEvent>()
    val events = eventChannel.receiveAsFlow()

    private val permissionFlow: Flow<Boolean> = flow {
        while (true) {
            emit(permissionHelper.hasBluetoothPermission())
            delay(1000) // Check every second
        }
    }

    private val notificationPermissionFlow: Flow<Boolean> = flow {
        while (true) {
            emit(permissionHelper.hasNotificationPermission())
            delay(1000)
        }
    }

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
        val hasDevicesWithLaunchPkg = devices.any { it.launchPkg != null }
        val hint = permissionHelper.getOverlayPermissionHint(isDismissed, hasDevicesWithLaunchPkg)
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

    val state = eu.darken.bluemusic.common.flow.combine(
        upgradeRepo.upgradeInfo,
        bluetoothSource.isEnabled,
        permissionFlow,
        devicesFlow,
        batteryOptimizationHintFlow,
        overlayPermissionHintFlow,
        notificationPermissionHintFlow,
    ) { upgradeInfo, isEnabled, hasBluetoothPermission, devices, batteryHint, overlayHint, notificationHint ->
        State(
            isProVersion = upgradeInfo.isUpgraded,
            isBluetoothEnabled = isEnabled,
            hasBluetoothPermission = hasBluetoothPermission,
            devices = devices,
            showBatteryOptimizationHint = batteryHint.shouldShow,
            batteryOptimizationIntent = batteryHint.intent,
            showAndroid10AppLaunchHint = overlayHint.shouldShow,
            android10AppLaunchIntent = overlayHint.intent,
            showNotificationPermissionHint = notificationHint.shouldShow,
        )
    }.asStateFlow()

    data class State(
        val isProVersion: Boolean = false,
        val isBluetoothEnabled: Boolean = false,
        val hasBluetoothPermission: Boolean = true,
        val devices: List<ManagedDevice> = emptyList(),
        val isLoading: Boolean = false,
        val showBatteryOptimizationHint: Boolean = false,
        val batteryOptimizationIntent: Intent? = null,
        val showAndroid10AppLaunchHint: Boolean = false,
        val android10AppLaunchIntent: Intent? = null,
        val showNotificationPermissionHint: Boolean = false,
    )

    fun action(action: DevicesAction) {
        log(tag) { "action: $action" }
        when (action) {
            DevicesAction.RequestBluetoothPermission -> {
                launch {
                    val permission = permissionHelper.getBluetoothPermission()
                    eventChannel.send(DevicesEvent.RequestPermission(permission))
                }
            }

            DevicesAction.RequestNotificationPermission -> {
                launch {
                    val permission = permissionHelper.getNotificationPermission()
                    if (permission != null) {
                        eventChannel.send(DevicesEvent.RequestPermission(permission))
                    }
                }
            }

            DevicesAction.DismissBatteryOptimizationHint -> {
                launch {
                    generalSettings.isBatteryOptimizationHintDismissed.update { true }
                }
            }

            DevicesAction.DismissAndroid10AppLaunchHint -> {
                launch {
                    generalSettings.isAndroid10AppLaunchHintDismissed.update { true }
                }
            }

            DevicesAction.DismissNotificationPermissionHint -> {
                launch {
                    generalSettings.isNotificationPermissionHintDismissed.update { true }
                }
            }

            is DevicesAction.AdjustVolume -> {
                // Handle volume adjustment
            }
        }
    }
}
