package eu.darken.bluemusic.devices.ui.config

import android.app.Activity
import android.app.NotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.AppTool
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.SingleEventFlow
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.observeDevice
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first


@HiltViewModel(assistedFactory = DeviceConfigViewModel.Factory::class)
class DeviceConfigViewModel @AssistedInject constructor(
    @Assisted private val deviceAddress: DeviceAddr,
    private val deviceRepo: DeviceRepo,
    private val streamHelper: StreamHelper,
    private val upgradeRepo: UpgradeRepo,
    private val appTool: AppTool,
    private val notificationManager: NotificationManager,
    private val dispatcherProvider: DispatcherProvider,
    private val navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, logTag("Devices", "Config", "VM"), navCtrl) {

    data class State(
        val device: ManagedDevice,
        val isProVersion: Boolean = false,
        val isLoading: Boolean = true,
        val error: String? = null,
        val launchAppLabel: String? = null
    )

    val events = SingleEventFlow<ConfigEvent>()

    val state = combine(
        upgradeRepo.upgradeInfo,
        deviceRepo.observeDevice(deviceAddress).filterNotNull(),
    ) { upgradeInfo, device ->
        State(
            device = device,
            isProVersion = upgradeInfo.isUpgraded,
        )
    }.asStateFlow()

    private fun toggleAutoPlay() {
//        val device = currentState.device ?: return
//
//        if (!currentState.isProVersion) {
//            updateState { copy(showPurchaseDialog = true) }
//            return
//        }
//
//        launch {
//            deviceRepo.updateDevice(device.address) { oldConfig ->
//                oldConfig.copy(autoplay = !device.autoplay)
//            }
//        }
    }

    private fun toggleVolumeLock() {
//        val device = currentState.device ?: return
//
//        if (!currentState.isProVersion) {
//            updateState { copy(showPurchaseDialog = true) }
//            return
//        }
//
//        launch {
//            deviceRepo.updateDevice(device.address) { oldConfig ->
//                oldConfig.copy(volumeLock = !device.volumeLock)
//            }
//        }
    }

    private fun toggleKeepAwake() {
//        val device = currentState.device ?: return
//
//        if (!currentState.isProVersion) {
//            updateState { copy(showPurchaseDialog = true) }
//            return
//        }
//
//        launch {
//            deviceRepo.updateDevice(device.address) { oldConfig ->
//                oldConfig.copy(keepAwake = !device.keepAwake)
//            }
//
//            // TODO this should be a global module/listener to the device states
//            if (device.isActive) {
//                if (device.keepAwake) {
//                    wakelockMan.tryAquire()
//                } else {
//                    wakelockMan.tryRelease()
//                }
//            }
//        }
    }

    private fun toggleNudgeVolume() {
//        val device = currentState.device ?: return
//
//        launch {
//            deviceRepo.updateDevice(device.address) { oldConfig ->
//                oldConfig.copy(nudgeVolume = !device.nudgeVolume)
//            }
//        }
    }

    private fun showAppPicker() {
//        if (!currentState.isProVersion) {
//            updateState { copy(showPurchaseDialog = true) }
//            return
//        }
//
//        updateState { copy(showAppPickerDialog = true) }
    }

    private fun clearLaunchApp() {
//        val device = currentState.device ?: return
//
//        launch {
//            deviceRepo.updateDevice(device.address) { oldConfig ->
//                oldConfig.copy(launchPkg = null)
//            }
//            updateState { copy(launchAppLabel = null) }
//        }
    }

    private fun showMonitoringDurationDialog() {
//        val device = currentState.device ?: return
//        updateState {
//            copy(
//                showMonitoringDurationDialog = device.monitoringDuration ?: DevicesSettings.DEFAULT_MONITORING_DURATION
//            )
//        }
    }

    private fun showReactionDelayDialog() {
//        val device = currentState.device ?: return
//        updateState { copy(showReactionDelayDialog = device.actionDelay ?: DevicesSettings.DEFAULT_REACTION_DELAY) }
    }

    private fun showAdjustmentDelayDialog() {
//        val device = currentState.device ?: return
//        updateState { copy(showAdjustmentDelayDialog = device.adjustmentDelay ?: DevicesSettings.DEFAULT_ADJUSTMENT_DELAY) }
    }

    private fun showRenameDialog() {
//        val device = currentState.device ?: return
//
//        if (!currentState.isProVersion) {
//            updateState { copy(showPurchaseDialog = true) }
//            return
//        }
//
//        updateState { copy(showRenameDialog = device.label) }
    }

    private fun showDeleteDialog() {
//        updateState { copy(showDeleteDialog = true) }
    }

    private fun dismissDialogs() {
//        updateState {
//            copy(
//                showPurchaseDialog = false,
//                showMonitoringDurationDialog = null,
//                showReactionDelayDialog = null,
//                showAdjustmentDelayDialog = null,
//                showRenameDialog = null,
//                showDeleteDialog = false,
//                showAppPickerDialog = false
//            )
//        }
    }

    private fun purchaseUpgrade(activity: Activity) {
        launch {
            // TODO: Implement purchase flow
            // iapRepo.startIAPFlow(AvailableSkus.PRO_VERSION, activity)
            dismissDialogs()
        }
    }

    private fun updateMonitoringDuration(duration: Long) {
//        val device = currentState.device ?: return
//
//        launch {
//            deviceRepo.updateDevice(device.address) { oldConfig ->
//                oldConfig.copy(monitoringDuration = if (duration > 0) duration else null)
//            }
//            dismissDialogs()
//        }
    }

    private fun updateReactionDelay(delay: Long) {
//        val device = currentState.device ?: return
//
//        launch {
//            deviceRepo.updateDevice(device.address) { oldConfig ->
//                oldConfig.copy(actionDelay = if (delay > 0) delay else null)
//            }
//            dismissDialogs()
//        }
    }

    private fun updateAdjustmentDelay(delay: Long) {
//        val device = currentState.device ?: return
//
//        launch {
//            deviceRepo.updateDevice(device.address) { oldConfig ->
//                oldConfig.copy(adjustmentDelay = if (delay > 0) delay else null)
//            }
//            dismissDialogs()
//        }
    }

    private fun renameDevice(newName: String) {
//        val device = currentState.device ?: return
//
//        launch {
//            deviceRepo.updateDevice(device.address) { oldConfig ->
//                oldConfig.copy(alias = newName)
//            }
//            dismissDialogs()
//        }
    }

    private fun handleDeleteConfirmation(confirmed: Boolean) {
//        if (confirmed) {
//            val device = currentState.device ?: return
//            launch {
//                deviceRepo.deleteDevice(device.address)
//                updateState { copy(shouldFinish = true) }
//            }
//        }
//        dismissDialogs()
    }

    private fun updateLaunchApp(packageName: String) {
//        val device = currentState.device ?: return
//
//        launch {
//            deviceRepo.updateDevice(device.address) { oldConfig ->
//                oldConfig.copy(launchPkg = packageName)
//            }
//            updateState {
//                copy(
//                    showAppPickerDialog = false,
//                    launchAppLabel = try {
//                        AppTool.getLabel(context, packageName)
//                    } catch (e: PackageManager.NameNotFoundException) {
//                        log(TAG, ERROR) { e.asLog() }
//                        null
//                    }
//                )
//            }
//        }
    }

    fun handleAction(action: ConfigAction) = launch {
        log(tag) { "handleAction: $action" }
        when (action) {
            is ConfigAction.OnAppSelected -> TODO()
            is ConfigAction.OnClearLaunchApp -> TODO()
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

            is ConfigAction.OnLaunchAppClicked -> {
                events.emit(ConfigEvent.ShowAppPickerDialog)
            }

            is ConfigAction.OnRename -> {
                deviceRepo.renameDevice(deviceAddress, action.newName)
            }

            is ConfigAction.OnRenameClicked -> {
                val device = state.first().device
                events.emit(ConfigEvent.ShowRenameDialog(device.label))
            }

            is ConfigAction.OnToggleAutoPlay -> deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                oldConfig.copy(autoplay = !oldConfig.autoplay)
            }

            is ConfigAction.OnToggleKeepAwake -> deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                oldConfig.copy(keepAwake = !oldConfig.keepAwake)
            }

            is ConfigAction.OnToggleNudgeVolume -> deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                oldConfig.copy(nudgeVolume = !oldConfig.nudgeVolume)
            }

            is ConfigAction.OnToggleVolumeLock -> deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                oldConfig.copy(
                    volumeLock = !oldConfig.volumeLock,
                    volumeObserving = if (oldConfig.volumeObserving && !oldConfig.volumeLock) false else oldConfig.volumeObserving,
                )
            }

            is ConfigAction.OnToggleVolumeObserving -> deviceRepo.updateDevice(deviceAddress) { oldConfig ->
                oldConfig.copy(
                    volumeObserving = !oldConfig.volumeObserving,
                    volumeLock = if (oldConfig.volumeLock && !oldConfig.volumeObserving) false else oldConfig.volumeLock,
                )
            }

            is ConfigAction.OnToggleVolume -> {
                val device = state.first().device

                val newVolume = if (device.getVolume(action.type) == null) {
                    streamHelper.getVolumePercentage(device.getStreamId(action.type))
                } else {
                    null
                }
                log(tag) { "toggleVolume: newVolume=$newVolume" }
                deviceRepo.updateDevice(device.address) { oldConfig ->
                    oldConfig.updateVolume(action.type, newVolume)
                }
            }
        }
    }


    @AssistedFactory
    interface Factory {
        fun create(deviceAddress: DeviceAddr): DeviceConfigViewModel
    }
}
