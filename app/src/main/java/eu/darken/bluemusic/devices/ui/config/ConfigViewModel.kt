package eu.darken.bluemusic.devices.ui.config

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import eu.darken.bluemusic.common.AppTool
import eu.darken.bluemusic.common.WakelockMan
import eu.darken.bluemusic.common.architecture.BaseViewModel
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceRepository
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.toManagedDevice
import eu.darken.bluemusic.main.core.Settings
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class ConfigState(
    val device: ManagedDevice? = null,
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

sealed interface ConfigEvent {
    data object OnToggleMusicVolume : ConfigEvent
    data object OnToggleCallVolume : ConfigEvent
    data object OnToggleRingVolume : ConfigEvent
    data object OnToggleNotificationVolume : ConfigEvent
    data object OnToggleAlarmVolume : ConfigEvent
    data object OnToggleAutoPlay : ConfigEvent
    data object OnToggleVolumeLock : ConfigEvent
    data object OnToggleKeepAwake : ConfigEvent
    data object OnToggleNudgeVolume : ConfigEvent
    data object OnLaunchAppClicked : ConfigEvent
    data object OnClearLaunchApp : ConfigEvent
    data object OnEditMonitoringDurationClicked : ConfigEvent
    data object OnEditReactionDelayClicked : ConfigEvent
    data object OnEditAdjustmentDelayClicked : ConfigEvent
    data object OnRenameClicked : ConfigEvent
    data object OnDeleteDevice : ConfigEvent
    data object OnDismissDialog : ConfigEvent
    data class OnPurchaseUpgrade(val activity: Activity) : ConfigEvent
    data class OnEditMonitoringDuration(val duration: Long) : ConfigEvent
    data class OnEditReactionDelay(val delay: Long) : ConfigEvent
    data class OnEditAdjustmentDelay(val delay: Long) : ConfigEvent
    data class OnRename(val newName: String) : ConfigEvent
    data class OnConfirmDelete(val confirmed: Boolean) : ConfigEvent
    data class OnAppSelected(val packageName: String) : ConfigEvent
}

class ConfigViewModel @Inject constructor(
    private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val streamHelper: StreamHelper,
    private val upgradeRepo: UpgradeRepo,
    private val appTool: AppTool,
    private val notificationManager: NotificationManager,
    private val wakelockMan: WakelockMan,
    private val dispatcherProvider: DispatcherProvider
) : BaseViewModel<ConfigState, ConfigEvent>(ConfigState()) {

    companion object {
        private val TAG = logTag("ConfigViewModel")
    }

    private var deviceAddress: String? = null

    fun setDeviceAddress(address: String) {
        deviceAddress = address
        observeDevice()
        observeProVersion()
    }

    private fun observeDevice() {
        val address = deviceAddress ?: return

        launch {
            deviceRepository.observeDevice(address)
                .filterNotNull()
                .map { it.toManagedDevice() }
                .catch { e ->
                    log(TAG, ERROR) { "Failed to observe device: ${e.asLog()}" }
                    updateState { copy(error = e.message, shouldFinish = true) }
                }
                .collect { device ->
                    updateState {
                        copy(
                            device = device,
                            isLoading = false,
                            launchAppLabel = device.launchPkg?.let {
                                try {
                                    AppTool.getLabel(context, it)
                                } catch (e: PackageManager.NameNotFoundException) {
                                    log(TAG, ERROR) { e.asLog() }
                                    null
                                }
                            }
                        )
                    }
                }
        }
    }

    private fun observeProVersion() {
//        launch {
//            iapRepo.recheck()
//            iapRepo.isProVersion
//                .catch { e ->
//                    log(TAG, ERROR) { "Failed to observe pro version: ${e.asLog()}" }
//                }
//                .collect { isProVersion ->
//                    updateState { copy(isProVersion = isProVersion) }
//                }
//        }
    }

    override fun onEvent(event: ConfigEvent) {
        when (event) {
            is ConfigEvent.OnToggleMusicVolume -> toggleVolume(AudioStream.Type.MUSIC)
            is ConfigEvent.OnToggleCallVolume -> toggleVolume(AudioStream.Type.CALL)
            is ConfigEvent.OnToggleRingVolume -> toggleVolume(AudioStream.Type.RINGTONE)
            is ConfigEvent.OnToggleNotificationVolume -> toggleVolume(AudioStream.Type.NOTIFICATION)
            is ConfigEvent.OnToggleAlarmVolume -> toggleVolume(AudioStream.Type.ALARM)
            is ConfigEvent.OnToggleAutoPlay -> toggleAutoPlay()
            is ConfigEvent.OnToggleVolumeLock -> toggleVolumeLock()
            is ConfigEvent.OnToggleKeepAwake -> toggleKeepAwake()
            is ConfigEvent.OnToggleNudgeVolume -> toggleNudgeVolume()
            is ConfigEvent.OnLaunchAppClicked -> showAppPicker()
            is ConfigEvent.OnClearLaunchApp -> clearLaunchApp()
            is ConfigEvent.OnEditMonitoringDurationClicked -> showMonitoringDurationDialog()
            is ConfigEvent.OnEditReactionDelayClicked -> showReactionDelayDialog()
            is ConfigEvent.OnEditAdjustmentDelayClicked -> showAdjustmentDelayDialog()
            is ConfigEvent.OnRenameClicked -> showRenameDialog()
            is ConfigEvent.OnDeleteDevice -> showDeleteDialog()
            is ConfigEvent.OnDismissDialog -> dismissDialogs()
            is ConfigEvent.OnPurchaseUpgrade -> purchaseUpgrade(event.activity)
            is ConfigEvent.OnEditMonitoringDuration -> updateMonitoringDuration(event.duration)
            is ConfigEvent.OnEditReactionDelay -> updateReactionDelay(event.delay)
            is ConfigEvent.OnEditAdjustmentDelay -> updateAdjustmentDelay(event.delay)
            is ConfigEvent.OnRename -> renameDevice(event.newName)
            is ConfigEvent.OnConfirmDelete -> handleDeleteConfirmation(event.confirmed)
            is ConfigEvent.OnAppSelected -> updateLaunchApp(event.packageName)
        }
    }

    private fun toggleVolume(type: AudioStream.Type) {
        val device = currentState.device ?: return

        if (!currentState.isProVersion && (type == AudioStream.Type.RINGTONE || type == AudioStream.Type.NOTIFICATION)) {
            updateState { copy(showPurchaseDialog = true) }
            return
        }

        launch {
            val newVolume = if (device.getVolume(type) == null) {
                streamHelper.getVolumePercentage(device.getStreamId(type))
            } else {
                null
            }

            updateDeviceInRepository(device.withUpdatedVolume(type, newVolume))
        }
    }

    private fun toggleAutoPlay() {
        val device = currentState.device ?: return

        if (!currentState.isProVersion) {
            updateState { copy(showPurchaseDialog = true) }
            return
        }

        launch {
            updateDeviceInRepository(device.copy(autoplay = !device.autoplay))
        }
    }

    private fun toggleVolumeLock() {
        val device = currentState.device ?: return

        if (!currentState.isProVersion) {
            updateState { copy(showPurchaseDialog = true) }
            return
        }

        launch {
            updateDeviceInRepository(device.copy(volumeLock = !device.volumeLock))
        }
    }

    private fun toggleKeepAwake() {
        val device = currentState.device ?: return

        if (!currentState.isProVersion) {
            updateState { copy(showPurchaseDialog = true) }
            return
        }

        launch {
            updateDeviceInRepository(device.copy(keepAwake = !device.keepAwake))

            if (device.isActive) {
                if (device.keepAwake) {
                    wakelockMan.tryAquire()
                } else {
                    wakelockMan.tryRelease()
                }
            }
        }
    }

    private fun toggleNudgeVolume() {
        val device = currentState.device ?: return

        launch {
            updateDeviceInRepository(device.copy(nudgeVolume = !device.nudgeVolume))
        }
    }

    private fun showAppPicker() {
        if (!currentState.isProVersion) {
            updateState { copy(showPurchaseDialog = true) }
            return
        }

        updateState { copy(showAppPickerDialog = true) }
    }

    private fun clearLaunchApp() {
        val device = currentState.device ?: return

        launch {
            updateDeviceInRepository(device.copy(launchPkg = null))
            updateState { copy(launchAppLabel = null) }
        }
    }

    private fun showMonitoringDurationDialog() {
        val device = currentState.device ?: return
        updateState {
            copy(
                showMonitoringDurationDialog = device.monitoringDuration ?: Settings.DEFAULT_MONITORING_DURATION
            )
        }
    }

    private fun showReactionDelayDialog() {
        val device = currentState.device ?: return
        updateState { copy(showReactionDelayDialog = device.actionDelay ?: Settings.DEFAULT_REACTION_DELAY) }
    }

    private fun showAdjustmentDelayDialog() {
        val device = currentState.device ?: return
        updateState { copy(showAdjustmentDelayDialog = device.adjustmentDelay ?: Settings.DEFAULT_ADJUSTMENT_DELAY) }
    }

    private fun showRenameDialog() {
        val device = currentState.device ?: return

        if (!currentState.isProVersion) {
            updateState { copy(showPurchaseDialog = true) }
            return
        }

        updateState { copy(showRenameDialog = device.label) }
    }

    private fun showDeleteDialog() {
        updateState { copy(showDeleteDialog = true) }
    }

    private fun dismissDialogs() {
        updateState {
            copy(
                showPurchaseDialog = false,
                showMonitoringDurationDialog = null,
                showReactionDelayDialog = null,
                showAdjustmentDelayDialog = null,
                showRenameDialog = null,
                showDeleteDialog = false,
                showAppPickerDialog = false
            )
        }
    }

    private fun purchaseUpgrade(activity: Activity) {
        launch {
            // TODO: Implement purchase flow
            // iapRepo.startIAPFlow(AvailableSkus.PRO_VERSION, activity)
            dismissDialogs()
        }
    }

    private fun updateMonitoringDuration(duration: Long) {
        val device = currentState.device ?: return

        launch {
            updateDeviceInRepository(device.copy(monitoringDuration = if (duration > 0) duration else null))
            dismissDialogs()
        }
    }

    private fun updateReactionDelay(delay: Long) {
        val device = currentState.device ?: return

        launch {
            updateDeviceInRepository(device.copy(actionDelay = if (delay > 0) delay else null))
            dismissDialogs()
        }
    }

    private fun updateAdjustmentDelay(delay: Long) {
        val device = currentState.device ?: return

        launch {
            updateDeviceInRepository(device.copy(adjustmentDelay = if (delay > 0) delay else null))
            dismissDialogs()
        }
    }

    private fun renameDevice(newName: String) {
        val device = currentState.device ?: return

        launch {
            updateDeviceInRepository(device.copy(alias = newName))
            dismissDialogs()
        }
    }

    private fun handleDeleteConfirmation(confirmed: Boolean) {
        if (confirmed) {
            val device = currentState.device ?: return
            launch {
                deviceRepository.deleteDevice(device.address)
                updateState { copy(shouldFinish = true) }
            }
        }
        dismissDialogs()
    }

    private fun updateLaunchApp(packageName: String) {
        val device = currentState.device ?: return

        launch {
            updateDeviceInRepository(device.copy(launchPkg = packageName))
            updateState {
                copy(
                    showAppPickerDialog = false,
                    launchAppLabel = try {
                        AppTool.getLabel(context, packageName)
                    } catch (e: PackageManager.NameNotFoundException) {
                        log(TAG, ERROR) { e.asLog() }
                        null
                    }
                )
            }
        }
    }

    private suspend fun updateDeviceInRepository(device: ManagedDevice) {
        deviceRepository.updateDevice(device.address) { currentEntity ->
            currentEntity.copy(
                musicVolume = device.getVolume(AudioStream.Type.MUSIC),
                callVolume = device.getVolume(AudioStream.Type.CALL),
                ringVolume = device.getVolume(AudioStream.Type.RINGTONE),
                notificationVolume = device.getVolume(AudioStream.Type.NOTIFICATION),
                alarmVolume = device.getVolume(AudioStream.Type.ALARM),
                autoplay = device.autoplay,
                volumeLock = device.volumeLock,
                keepAwake = device.keepAwake,
                nudgeVolume = device.nudgeVolume,
                launchPkg = device.launchPkg,
                actionDelay = device.actionDelay,
                adjustmentDelay = device.adjustmentDelay,
                monitoringDuration = device.monitoringDuration,
                alias = device.alias
            )
        }
    }
}
