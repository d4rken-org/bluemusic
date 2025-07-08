package eu.darken.bluemusic.devices.ui.manage

import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.PowerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceRepository
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.main.core.audio.StreamHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class ManagedDevicesViewModel @Inject constructor(
    private val packageManager: PackageManager,
    private val deviceRepository: DeviceRepository,
    private val streamHelper: StreamHelper,
    private val upgradeRepo: UpgradeRepo,
    private val bluetoothSource: BluetoothRepo,
    private val notificationManager: NotificationManager,
    private val powerManager: PowerManager,
    private val dispatcherProvider: DispatcherProvider,
    private val navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, logTag("Devices", "Managed", "VM"), navCtrl) {

    val state = combine(
        upgradeRepo.upgradeInfo,
        bluetoothSource.isEnabled,
        flowOf(Unit),
        flowOf(Unit),
    ) { upgradeInfo, isEnabled, _, _ ->
        State(
            isProVersion = upgradeInfo.isUpgraded,
            isBluetoothEnabled = isEnabled
        )
    }.asStateFlow()

    data class State(
        val isProVersion: Boolean = false,
        val isBluetoothEnabled: Boolean = false,
        val devices: List<ManagedDevice> = emptyList(),
        val isLoading: Boolean = false,
    )

    fun action(action: DeviceAction) {
        log(tag) { "action: $action" }
    }

//    private var isBatterySavingHintDismissed = false
//    private var isAppLaunchHintDismissed = false
//    private var isNotificationPermissionDismissed = false
//
//    init {
//        observeDevices()
//        observeBluetoothState()
//        observeProVersion()
//        checkBatterySavingIssue()
//        checkAppLaunchIssue()
//        checkNotificationPermissions()
//    }
//
//    private fun observeDevices() {
//        launch {
//            deviceRepository.getAllDevices()
//                .map { entities ->
//                    entities
//                        .map { it.toManagedDevice() }
//                        .sortedByDescending { it.lastConnected }
//                }
//                .catch { e ->
//                    log(TAG, ERROR) { "Failed to observe devices: ${e.asLog()}" }
//                    updateState { copy(error = e.message) }
//                }
//                .collect { devices ->
//                    updateState { copy(devices = devices, isLoading = false) }
//                }
//        }
//    }
//
//    private fun observeBluetoothState() {
//        launch {
//            bluetoothSource.isEnabled
//                .catch { e ->
//                    log(TAG, ERROR) { "Failed to observe bluetooth state: ${e.asLog()}" }
//                }
//                .collect { enabled ->
//                    updateState { copy(isBluetoothEnabled = enabled) }
//                }
//        }
//    }
//
//    private fun observeProVersion() {
////        launch {
////            iapRepo.isProVersion
////                .catch { e ->
////                    log(TAG, ERROR) { "Failed to observe pro version: ${e.asLog()}" }
////                }
////                .collect { isProVersion ->
////                    updateState { copy(isProVersion = isProVersion) }
////                }
////        }
//    }
//
//    private fun checkBatterySavingIssue() {
//        if (!ApiHelper.hasOreo()) return
////
////        val batterySavingIntent = Intent().apply {
////            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
////            Intent.setAction = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
////        }
////
////        val resolveInfo = packageManager.resolveActivity(batterySavingIntent, 0)
////        val displayHint = !isBatterySavingHintDismissed &&
////                !powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID) &&
////                resolveInfo != null
////
////        updateState {
////            copy(
////                showBatteryOptimizationHint = displayHint,
////                batteryOptimizationIntent = if (displayHint) batterySavingIntent else null
////            )
////        }
//    }
//
//    private fun checkAppLaunchIssue() {
//        if (!ApiHelper.hasAndroid10()) return
////
////        val overlayIntent = Intent().apply {
////            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
////            Intent.setAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
////        }
////
////        val displayHint = !isAppLaunchHintDismissed &&
////                !Settings.canDrawOverlays(context)
////
////        updateState {
////            copy(
////                showAndroid10AppLaunchHint = displayHint,
////                android10AppLaunchIntent = if (displayHint) overlayIntent else null
////            )
////        }
//    }
//
//    private fun checkNotificationPermissions() {
//        if (!ApiHelper.hasAndroid13()) return
//
//        val displayHint = !isNotificationPermissionDismissed &&
//                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
//
//        updateState {
//            copy(showNotificationPermissionHint = displayHint)
//        }
//    }
//
//    override fun onEvent(event: ManagedDevicesEvent) {
//        when (event) {
//            is ManagedDevicesEvent.OnBatterySavingDismissed -> {
//                isBatterySavingHintDismissed = true
//                checkBatterySavingIssue()
//            }
//            is ManagedDevicesEvent.OnAppLaunchHintDismissed -> {
//                isAppLaunchHintDismissed = true
//                checkAppLaunchIssue()
//            }
//            is ManagedDevicesEvent.OnNotificationPermissionsDismissed -> {
//                isNotificationPermissionDismissed = true
//                checkNotificationPermissions()
//            }
//            is ManagedDevicesEvent.OnNotificationPermissionsGranted -> {
//                checkNotificationPermissions()
//            }
//            is ManagedDevicesEvent.OnUpdateMusicVolume -> {
//                updateDeviceVolume(event.device, AudioStream.Type.MUSIC, event.percentage)
//            }
//            is ManagedDevicesEvent.OnUpdateCallVolume -> {
//                updateDeviceVolume(event.device, AudioStream.Type.CALL, event.percentage)
//            }
//            is ManagedDevicesEvent.OnUpdateRingVolume -> {
//                updateDeviceVolume(event.device, AudioStream.Type.RINGTONE, event.percentage)
//            }
//            is ManagedDevicesEvent.OnUpdateNotificationVolume -> {
//                updateDeviceVolume(event.device, AudioStream.Type.NOTIFICATION, event.percentage)
//            }
//            is ManagedDevicesEvent.OnUpdateAlarmVolume -> {
//                updateDeviceVolume(event.device, AudioStream.Type.ALARM, event.percentage)
//            }
//            is ManagedDevicesEvent.OnDeleteDevice -> {
//                deleteDevice(event.device)
//            }
//            is ManagedDevicesEvent.OnAddDeviceClicked -> {
//                // Navigation handled by ScreenHost
//            }
//            is ManagedDevicesEvent.OnDeviceClicked -> {
//                // Navigation handled by ScreenHost
//            }
//        }
//    }
//
//    private fun updateDeviceVolume(_device: ManagedDevice, streamType: AudioStream.Type, percentage: Float) {
//        launch {
//            val device = _device.withUpdatedVolume(streamType, percentage)
//            val entity = deviceRepository.getDevice(device.address)
//            if (entity != null) {
//                val updated = when (streamType) {
//                    AudioStream.Type.MUSIC -> entity.copy(musicVolume = percentage)
//                    AudioStream.Type.CALL -> entity.copy(callVolume = percentage)
//                    AudioStream.Type.RINGTONE -> entity.copy(ringVolume = percentage)
//                    AudioStream.Type.NOTIFICATION -> entity.copy(notificationVolume = percentage)
//                    AudioStream.Type.ALARM -> entity.copy(alarmVolume = percentage)
//                }
//                deviceRepository.updateDevice(updated)
//
//                if (device.isActive) {
//                    streamHelper.changeVolume(
//                        device.getStreamId(streamType),
//                        device.getVolume(streamType) ?: 0f,
//                        true,
//                        0
//                    )
//                }
//            }
//        }
//    }
//
//    private fun deleteDevice(device: ManagedDevice) {
//        launch {
//            deviceRepository.deleteDevice(device.address)
//        }
//    }
}
