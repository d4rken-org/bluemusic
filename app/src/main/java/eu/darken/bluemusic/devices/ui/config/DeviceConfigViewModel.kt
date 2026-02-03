package eu.darken.bluemusic.devices.ui.config

import android.annotation.SuppressLint
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.apps.AppRepo
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.common.upgrade.isPro
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.observeDevice
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.Duration


@HiltViewModel(assistedFactory = DeviceConfigViewModel.Factory::class)
class DeviceConfigViewModel @AssistedInject constructor(
    @Assisted private val deviceAddress: DeviceAddr,
    private val deviceRepo: DeviceRepo,
    private val volumeTool: VolumeTool,
    private val upgradeRepo: UpgradeRepo,
    appRepo: AppRepo,
    dispatcherProvider: DispatcherProvider,
    navCtrl: NavigationController,
    private val permissionHelper: PermissionHelper,
) : ViewModel4(dispatcherProvider, logTag("Devices", "Config", "VM"), navCtrl) {

    data class State(
        val device: ManagedDevice,
        val isProVersion: Boolean = false,
        val isLoading: Boolean = true,
        val error: String? = null,
        val launchAppLabel: String? = null,
        val launchAppLabels: List<String> = emptyList()
    )

    val events = SingleEventFlow<ConfigEvent>()

    val state = combine(
        upgradeRepo.upgradeInfo,
        deviceRepo.observeDevice(deviceAddress).filterNotNull(),
        appRepo.apps
    ) { upgradeInfo, device, appInfos ->
        val appInfoMap = appInfos.associateBy { it.packageName }
        
        // For backward compatibility, show first app if any
        val launchAppLabel = device.launchPkgs.firstOrNull()?.let { pkg ->
            appInfoMap[pkg]?.label
        }
        
        val launchAppLabels = device.launchPkgs.mapNotNull { pkg ->
            appInfoMap[pkg]?.label
        }
        
        State(
            device = device,
            isProVersion = upgradeInfo.isUpgraded,
            launchAppLabel = launchAppLabel,
            launchAppLabels = launchAppLabels
        )
    }.asStateFlow()

    fun handleAction(action: ConfigAction) = launch {
        log(tag) { "handleAction: $action" }
        when (action) {
            is ConfigAction.DeleteDevice -> {
                if (!action.confirmed) {
                    // Show delete confirmation dialog
                    events.emit(ConfigEvent.ShowDeleteDialog)
                }
            }

            is ConfigAction.OnConfirmDelete -> {
                if (action.confirmed) {
                    // Delete the device and navigate back
                    deviceRepo.deleteDevice(deviceAddress)
                    events.emit(ConfigEvent.NavigateBack)
                }
            }

            is ConfigAction.OnEditAdjustmentDelay -> {
                deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                    oldConfig.copy(adjustmentDelay = action.delay?.toMillis())
                }
            }

            is ConfigAction.OnEditAdjustmentDelayClicked -> {
                val device = state.first().device
                val currentDelay = device.adjustmentDelay
                events.emit(ConfigEvent.ShowAdjustmentDelayDialog(currentDelay))
            }

            is ConfigAction.OnEditMonitoringDuration -> {
                deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                    oldConfig.copy(monitoringDuration = action.duration?.toMillis())
                }
            }

            is ConfigAction.OnEditMonitoringDurationClicked -> {
                val device = state.first().device
                val currentDuration = device.monitoringDuration
                events.emit(ConfigEvent.ShowMonitoringDurationDialog(currentDuration))
            }

            is ConfigAction.OnEditReactionDelay -> {
                deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                    oldConfig.copy(actionDelay = action.delay?.toMillis())
                }
            }

            is ConfigAction.OnEditReactionDelayClicked -> {
                val device = state.first().device
                val currentDelay = device.actionDelay
                events.emit(ConfigEvent.ShowReactionDelayDialog(currentDelay))
            }

            is ConfigAction.OnEditVolumeRateLimitIncrease -> {
                deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                    oldConfig.copy(volumeRateLimitIncreaseMs = action.duration?.toMillis())
                }
            }

            is ConfigAction.OnEditVolumeRateLimitDecrease -> {
                deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                    oldConfig.copy(volumeRateLimitDecreaseMs = action.duration?.toMillis())
                }
            }

            is ConfigAction.OnEditVolumeRateLimitIncreaseClicked -> {
                val device = state.first().device
                val currentLimit = Duration.ofMillis(device.volumeRateLimitIncreaseMs)
                events.emit(ConfigEvent.ShowVolumeRateLimitIncreaseDialog(currentLimit))
            }

            is ConfigAction.OnEditVolumeRateLimitDecreaseClicked -> {
                val device = state.first().device
                val currentLimit = Duration.ofMillis(device.volumeRateLimitDecreaseMs)
                events.emit(ConfigEvent.ShowVolumeRateLimitDecreaseDialog(currentLimit))
            }

            is ConfigAction.OnLaunchAppClicked -> {
                if (!upgradeRepo.isPro()) {
                    events.emit(ConfigEvent.RequiresPro)
                } else {
                    navTo(Nav.Main.AppSelection(deviceAddress))
                }
            }

            is ConfigAction.OnRename -> {
                deviceRepo.renameDevice(deviceAddress, action.newName)
            }

            is ConfigAction.OnRenameClicked -> {
                val device = state.first().device
                events.emit(ConfigEvent.ShowRenameDialog(device.label))
            }

            is ConfigAction.OnToggleAutoPlay -> {
                if (!upgradeRepo.isPro()) {
                    events.emit(ConfigEvent.RequiresPro)
                } else {
                    deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                        oldConfig.copy(autoplay = !oldConfig.autoplay)
                    }
                }
            }

            is ConfigAction.OnEditAutoplayKeycodesClicked -> {
                if (!upgradeRepo.isPro()) {
                    events.emit(ConfigEvent.RequiresPro)
                } else {
                    events.emit(ConfigEvent.ShowAutoplayKeycodesDialog)
                }
            }

            is ConfigAction.OnEditAutoplayKeycodes -> {
                deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                    oldConfig.copy(autoplayKeycodes = action.keycodes)
                }
            }

            is ConfigAction.OnToggleKeepAwake -> deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                oldConfig.copy(keepAwake = !oldConfig.keepAwake)
            }

            is ConfigAction.OnToggleNudgeVolume -> deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                oldConfig.copy(nudgeVolume = !oldConfig.nudgeVolume)
            }

            is ConfigAction.OnToggleVisibleAdjustments -> deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                oldConfig.copy(visibleAdjustments = if (oldConfig.visibleAdjustments == null) false else !oldConfig.visibleAdjustments)
            }

            is ConfigAction.OnToggleShowHomeScreen -> {
                if (!upgradeRepo.isPro()) {
                    events.emit(ConfigEvent.RequiresPro)
                } else {
                    deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                        oldConfig.copy(showHomeScreen = !oldConfig.showHomeScreen)
                    }
                }
            }

            is ConfigAction.OnToggleVolumeLock -> {
                if (!upgradeRepo.isPro()) {
                    events.emit(ConfigEvent.RequiresPro)
                } else {
                    deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                        oldConfig.copy(
                            volumeLock = !oldConfig.volumeLock,
                            volumeObserving = if (oldConfig.volumeObserving && !oldConfig.volumeLock) false else oldConfig.volumeObserving,
                            volumeRateLimiter = if (oldConfig.volumeRateLimiter && !oldConfig.volumeLock) false else oldConfig.volumeRateLimiter,
                        )
                    }
                }
            }

            is ConfigAction.OnToggleVolumeObserving -> deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                oldConfig.copy(
                    volumeObserving = !oldConfig.volumeObserving,
                    volumeLock = if (oldConfig.volumeLock && !oldConfig.volumeObserving) false else oldConfig.volumeLock,
                )
            }

            is ConfigAction.OnToggleVolumeSaveOnDisconnect -> deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                oldConfig.copy(volumeSaveOnDisconnect = !oldConfig.volumeSaveOnDisconnect)
            }

            is ConfigAction.OnToggleVolumeRateLimiter -> {
                if (!upgradeRepo.isPro()) {
                    events.emit(ConfigEvent.RequiresPro)
                } else {
                    deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                        oldConfig.copy(
                            volumeRateLimiter = !oldConfig.volumeRateLimiter,
                            volumeLock = if (oldConfig.volumeLock && !oldConfig.volumeRateLimiter) false else oldConfig.volumeLock,
                        )
                    }
                }
            }

            is ConfigAction.OnToggleVolume -> {
                val device = state.first().device

                // Check if trying to enable a volume type that requires notification policy access
                if (device.getVolume(action.type) == null) {
                    when (action.type) {
                        AudioStream.Type.RINGTONE -> {
                            if (!permissionHelper.hasNotificationPolicyAccess()) {
                                @SuppressLint("NewApi")
                                val intent = permissionHelper.getNotificationPolicyAccessIntent()
                                events.emit(
                                    ConfigEvent.RequiresNotificationPolicyAccess(
                                        intent,
                                        ConfigEvent.RequiresNotificationPolicyAccess.Feature.RINGTONE
                                    )
                                )
                                return@launch
                            }
                        }

                        AudioStream.Type.NOTIFICATION -> {
                            if (!permissionHelper.hasNotificationPolicyAccess()) {
                                @SuppressLint("NewApi")
                                val intent = permissionHelper.getNotificationPolicyAccessIntent()
                                events.emit(
                                    ConfigEvent.RequiresNotificationPolicyAccess(
                                        intent,
                                        ConfigEvent.RequiresNotificationPolicyAccess.Feature.NOTIFICATION
                                    )
                                )
                                return@launch
                            }
                        }

                        else -> {
                            // Other volume types don't require notification policy access
                        }
                    }
                }

                val newVolume = if (device.getVolume(action.type) == null) {
                    volumeTool.getVolumePercentage(device.getStreamId(action.type))
                } else {
                    null
                }
                log(tag) { "toggleVolume: newVolume=$newVolume" }
                deviceRepo.updateDevice(device.address) { oldConfig ->
                    oldConfig.updateVolume(action.type, newVolume)
                }
            }

            is ConfigAction.OnToggleEnabled -> {
                deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                    oldConfig.copy(isEnabled = !oldConfig.isEnabled)
                }
            }

            is ConfigAction.OnEditDndModeClicked -> {
                val device = state.first().device
                if (device.dndMode == null && !permissionHelper.hasNotificationPolicyAccess()) {
                    @SuppressLint("NewApi")
                    val intent = permissionHelper.getNotificationPolicyAccessIntent()
                    events.emit(
                        ConfigEvent.RequiresNotificationPolicyAccess(
                            intent,
                            ConfigEvent.RequiresNotificationPolicyAccess.Feature.DND
                        )
                    )
                } else {
                    events.emit(ConfigEvent.ShowDndModeDialog(device.dndMode))
                }
            }

            is ConfigAction.OnEditDndMode -> {
                deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                    oldConfig.copy(dndMode = action.mode)
                }
            }

            is ConfigAction.OnEditConnectionAlertClicked -> {
                if (!upgradeRepo.isPro()) {
                    events.emit(ConfigEvent.RequiresPro)
                } else {
                    val device = state.first().device
                    events.emit(
                        ConfigEvent.ShowConnectionAlertDialog(
                            device.connectionAlertType,
                            device.connectionAlertSoundUri
                        )
                    )
                }
            }

            is ConfigAction.OnEditConnectionAlertType -> {
                deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                    oldConfig.copy(connectionAlertType = action.type)
                }
            }

            is ConfigAction.OnEditConnectionAlertSoundUri -> {
                deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                    oldConfig.copy(connectionAlertSoundUri = action.uri)
                }
            }
        }
    }


    @AssistedFactory
    interface Factory {
        fun create(deviceAddress: DeviceAddr): DeviceConfigViewModel
    }
}
