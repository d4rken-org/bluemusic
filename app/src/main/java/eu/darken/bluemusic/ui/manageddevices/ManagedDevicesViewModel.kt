package eu.darken.bluemusic.ui.manageddevices

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import eu.darken.bluemusic.BuildConfig
import eu.darken.bluemusic.bluetooth.core.BluetoothSource
import eu.darken.bluemusic.common.architecture.BaseViewModel
import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import eu.darken.bluemusic.data.device.DeviceRepository
import eu.darken.bluemusic.data.device.ManagedDevice
import eu.darken.bluemusic.data.device.toManagedDevice
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.util.ApiHelper
import eu.darken.bluemusic.util.iap.IAPRepo
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject

data class ManagedDevicesState(
    val devices: List<ManagedDevice> = emptyList(),
    val isLoading: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val isProVersion: Boolean = false,
    val showBatteryOptimizationHint: Boolean = false,
    val batteryOptimizationIntent: Intent? = null,
    val showAndroid10AppLaunchHint: Boolean = false,
    val android10AppLaunchIntent: Intent? = null,
    val showNotificationPermissionHint: Boolean = false,
    val error: String? = null
)

sealed interface ManagedDevicesEvent {
    data object OnBatterySavingDismissed : ManagedDevicesEvent
    data object OnAppLaunchHintDismissed : ManagedDevicesEvent
    data object OnNotificationPermissionsDismissed : ManagedDevicesEvent
    data object OnNotificationPermissionsGranted : ManagedDevicesEvent
    data class OnUpdateMusicVolume(val device: ManagedDevice, val percentage: Float) : ManagedDevicesEvent
    data class OnUpdateCallVolume(val device: ManagedDevice, val percentage: Float) : ManagedDevicesEvent
    data class OnUpdateRingVolume(val device: ManagedDevice, val percentage: Float) : ManagedDevicesEvent
    data class OnUpdateNotificationVolume(val device: ManagedDevice, val percentage: Float) : ManagedDevicesEvent
    data class OnUpdateAlarmVolume(val device: ManagedDevice, val percentage: Float) : ManagedDevicesEvent
    data class OnDeleteDevice(val device: ManagedDevice) : ManagedDevicesEvent
    data object OnAddDeviceClicked : ManagedDevicesEvent
    data class OnDeviceClicked(val device: ManagedDevice) : ManagedDevicesEvent
}

class ManagedDevicesViewModel @Inject constructor(
    private val context: Context,
    private val packageManager: PackageManager,
    private val deviceRepository: DeviceRepository,
    private val streamHelper: StreamHelper,
    private val iapRepo: IAPRepo,
    private val bluetoothSource: BluetoothSource,
    private val notificationManager: NotificationManager,
    private val powerManager: PowerManager,
    private val dispatcherProvider: DispatcherProvider
) : BaseViewModel<ManagedDevicesState, ManagedDevicesEvent>(ManagedDevicesState()) {
    
    private var isBatterySavingHintDismissed = false
    private var isAppLaunchHintDismissed = false
    private var isNotificationPermissionDismissed = false
    
    init {
        observeDevices()
        observeBluetoothState()
        observeProVersion()
        checkBatterySavingIssue()
        checkAppLaunchIssue()
        checkNotificationPermissions()
    }
    
    private fun observeDevices() {
        launch {
            deviceRepository.getAllDevices()
                .map { entities ->
                    entities
                        .map { it.toManagedDevice() }
                        .sortedByDescending { it.lastConnected }
                }
                .catch { e ->
                    Timber.e(e, "Failed to observe devices")
                    updateState { copy(error = e.message) }
                }
                .collect { devices ->
                    updateState { copy(devices = devices, isLoading = false) }
                }
        }
    }
    
    private fun observeBluetoothState() {
        launch {
            bluetoothSource.isEnabled()
                .catch { e ->
                    Timber.e(e, "Failed to observe bluetooth state")
                }
                .collect { enabled ->
                    updateState { copy(isBluetoothEnabled = enabled) }
                }
        }
    }
    
    private fun observeProVersion() {
        launch {
            iapRepo.isProVersion()
                .catch { e ->
                    Timber.e(e, "Failed to observe pro version")
                }
                .collect { isProVersion ->
                    updateState { copy(isProVersion = isProVersion) }
                }
        }
    }
    
    private fun checkBatterySavingIssue() {
        if (!ApiHelper.hasOreo()) return
        
        val batterySavingIntent = Intent().apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        }
        
        val resolveInfo = packageManager.resolveActivity(batterySavingIntent, 0)
        val displayHint = !isBatterySavingHintDismissed &&
                !powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID) &&
                resolveInfo != null
        
        updateState { 
            copy(
                showBatteryOptimizationHint = displayHint,
                batteryOptimizationIntent = if (displayHint) batterySavingIntent else null
            )
        }
    }
    
    private fun checkAppLaunchIssue() {
        if (!ApiHelper.hasAndroid10()) return
        
        val overlayIntent = Intent().apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            action = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
        }
        
        val displayHint = !isAppLaunchHintDismissed &&
                !Settings.canDrawOverlays(context)
        
        updateState {
            copy(
                showAndroid10AppLaunchHint = displayHint,
                android10AppLaunchIntent = if (displayHint) overlayIntent else null
            )
        }
    }
    
    private fun checkNotificationPermissions() {
        if (!ApiHelper.hasAndroid13()) return
        
        val displayHint = !isNotificationPermissionDismissed &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        
        updateState {
            copy(showNotificationPermissionHint = displayHint)
        }
    }
    
    override fun onEvent(event: ManagedDevicesEvent) {
        when (event) {
            is ManagedDevicesEvent.OnBatterySavingDismissed -> {
                isBatterySavingHintDismissed = true
                checkBatterySavingIssue()
            }
            is ManagedDevicesEvent.OnAppLaunchHintDismissed -> {
                isAppLaunchHintDismissed = true
                checkAppLaunchIssue()
            }
            is ManagedDevicesEvent.OnNotificationPermissionsDismissed -> {
                isNotificationPermissionDismissed = true
                checkNotificationPermissions()
            }
            is ManagedDevicesEvent.OnNotificationPermissionsGranted -> {
                checkNotificationPermissions()
            }
            is ManagedDevicesEvent.OnUpdateMusicVolume -> {
                updateDeviceVolume(event.device, AudioStream.Type.MUSIC, event.percentage)
            }
            is ManagedDevicesEvent.OnUpdateCallVolume -> {
                updateDeviceVolume(event.device, AudioStream.Type.CALL, event.percentage)
            }
            is ManagedDevicesEvent.OnUpdateRingVolume -> {
                updateDeviceVolume(event.device, AudioStream.Type.RINGTONE, event.percentage)
            }
            is ManagedDevicesEvent.OnUpdateNotificationVolume -> {
                updateDeviceVolume(event.device, AudioStream.Type.NOTIFICATION, event.percentage)
            }
            is ManagedDevicesEvent.OnUpdateAlarmVolume -> {
                updateDeviceVolume(event.device, AudioStream.Type.ALARM, event.percentage)
            }
            is ManagedDevicesEvent.OnDeleteDevice -> {
                deleteDevice(event.device)
            }
            is ManagedDevicesEvent.OnAddDeviceClicked -> {
                // Navigation handled by ScreenHost
            }
            is ManagedDevicesEvent.OnDeviceClicked -> {
                // Navigation handled by ScreenHost
            }
        }
    }
    
    private fun updateDeviceVolume(device: ManagedDevice, streamType: AudioStream.Type, percentage: Float) {
        launch {
            device.setVolume(streamType, percentage)
            val entity = deviceRepository.getDevice(device.address)
            if (entity != null) {
                val updated = when (streamType) {
                    AudioStream.Type.MUSIC -> entity.copy(musicVolume = percentage)
                    AudioStream.Type.CALL -> entity.copy(callVolume = percentage)
                    AudioStream.Type.RINGTONE -> entity.copy(ringVolume = percentage)
                    AudioStream.Type.NOTIFICATION -> entity.copy(notificationVolume = percentage)
                    AudioStream.Type.ALARM -> entity.copy(alarmVolume = percentage)
                }
                deviceRepository.updateDevice(updated)
                
                if (device.isActive()) {
                    streamHelper.changeVolume(
                        device.getStreamId(streamType),
                        device.getVolume(streamType),
                        true,
                        0
                    )
                }
            }
        }
    }
    
    private fun deleteDevice(device: ManagedDevice) {
        launch {
            deviceRepository.deleteDevice(device.address)
        }
    }
}