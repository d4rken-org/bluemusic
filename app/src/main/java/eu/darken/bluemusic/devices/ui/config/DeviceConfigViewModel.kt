package eu.darken.bluemusic.devices.ui.config

import android.app.Activity
import android.app.NotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.common.AppTool
import eu.darken.bluemusic.common.WakelockMan
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.observeDevice
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull


@HiltViewModel(assistedFactory = DeviceConfigViewModel.Factory::class)
class DeviceConfigViewModel @AssistedInject constructor(
    @Assisted private val addr: DeviceAddr,
    private val deviceRepo: DeviceRepo,
    private val streamHelper: StreamHelper,
    private val upgradeRepo: UpgradeRepo,
    private val appTool: AppTool,
    private val notificationManager: NotificationManager,
    private val wakelockMan: WakelockMan,
    private val dispatcherProvider: DispatcherProvider,
    private val navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, logTag("Devices", "Config", "VM"), navCtrl) {

    data class State(
        val device: ManagedDevice,
        val isProVersion: Boolean = false,
        val isLoading: Boolean = true,
        val error: String? = null,
        val showPurchaseDialog: Boolean = false,
        val showMonitoringDurationDialog: Long? = null,
        val showReactionDelayDialog: Long? = null,
        val showAdjustmentDelayDialog: Long? = null,
        val showRenameDialog: String? = null,
        val showDeleteDialog: Boolean = false,
        val showAppPickerDialog: Boolean = false,
        val shouldFinish: Boolean = false,
        val launchAppLabel: String? = null
    )

    val state = combine(
        upgradeRepo.upgradeInfo,
        deviceRepo.observeDevice(addr).filterNotNull(),
    ) { upgradeInfo, device ->
        State(
            device = device,
            isProVersion = upgradeInfo.isUpgraded,
        )
    }.asStateFlow()


//    override fun onEvent(event: ConfigEvent) {
//        when (event) {
//            is ConfigEvent.OnToggleMusicVolume -> toggleVolume(AudioStream.Type.MUSIC)
//            is ConfigEvent.OnToggleCallVolume -> toggleVolume(AudioStream.Type.CALL)
//            is ConfigEvent.OnToggleRingVolume -> toggleVolume(AudioStream.Type.RINGTONE)
//            is ConfigEvent.OnToggleNotificationVolume -> toggleVolume(AudioStream.Type.NOTIFICATION)
//            is ConfigEvent.OnToggleAlarmVolume -> toggleVolume(AudioStream.Type.ALARM)
//            is ConfigEvent.OnToggleAutoPlay -> toggleAutoPlay()
//            is ConfigEvent.OnToggleVolumeLock -> toggleVolumeLock()
//            is ConfigEvent.OnToggleKeepAwake -> toggleKeepAwake()
//            is ConfigEvent.OnToggleNudgeVolume -> toggleNudgeVolume()
//            is ConfigEvent.OnLaunchAppClicked -> showAppPicker()
//            is ConfigEvent.OnClearLaunchApp -> clearLaunchApp()
//            is ConfigEvent.OnEditMonitoringDurationClicked -> showMonitoringDurationDialog()
//            is ConfigEvent.OnEditReactionDelayClicked -> showReactionDelayDialog()
//            is ConfigEvent.OnEditAdjustmentDelayClicked -> showAdjustmentDelayDialog()
//            is ConfigEvent.OnRenameClicked -> showRenameDialog()
//            is ConfigEvent.OnDeleteDevice -> showDeleteDialog()
//            is ConfigEvent.OnDismissDialog -> dismissDialogs()
//            is ConfigEvent.OnPurchaseUpgrade -> purchaseUpgrade(event.activity)
//            is ConfigEvent.OnEditMonitoringDuration -> updateMonitoringDuration(event.duration)
//            is ConfigEvent.OnEditReactionDelay -> updateReactionDelay(event.delay)
//            is ConfigEvent.OnEditAdjustmentDelay -> updateAdjustmentDelay(event.delay)
//            is ConfigEvent.OnRename -> renameDevice(event.newName)
//            is ConfigEvent.OnConfirmDelete -> handleDeleteConfirmation(event.confirmed)
//            is ConfigEvent.OnAppSelected -> updateLaunchApp(event.packageName)
//        }
//    }

    private fun toggleVolume(type: AudioStream.Type) {
//        val device = currentState.device ?: return
//
//        if (!currentState.isProVersion && (type == AudioStream.Type.RINGTONE || type == AudioStream.Type.NOTIFICATION)) {
//            updateState { copy(showPurchaseDialog = true) }
//            return
//        }
//
//        launch {
//            val newVolume = if (device.getVolume(type) == null) {
//                streamHelper.getVolumePercentage(device.getStreamId(type))
//            } else {
//                null
//            }
//
//            deviceRepo.updateDevice(device.address) { oldConfig ->
//                oldConfig.updateVolume(type, newVolume)
//            }
//        }
    }

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

    @AssistedFactory
    interface Factory {
        fun create(addr: DeviceAddr): DeviceConfigViewModel
    }
}
