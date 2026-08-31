package eu.darken.bluemusic.devices.ui.dashboard

import android.app.Activity
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.speaker.SpeakerDeviceProvider
import eu.darken.bluemusic.common.apps.AppInfo
import eu.darken.bluemusic.common.apps.AppRepo
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.common.review.ReviewTool
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.common.upgrade.isProForUi
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceLimits
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.NewDeviceCreator
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.getDevice
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.eq.core.EqCoordinator
import eu.darken.bluemusic.eq.core.EqEligibility
import eu.darken.bluemusic.eq.ui.EqAppResolver
import eu.darken.bluemusic.eq.ui.EqStatus
import eu.darken.bluemusic.eq.ui.deriveEqStatus
import eu.darken.bluemusic.main.core.GeneralSettings
import eu.darken.bluemusic.monitor.core.BackgroundActivityGuard
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeBand
import eu.darken.bluemusic.monitor.core.audio.VolumeLimitEnforcer
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeModeTool
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val permissionHelper: PermissionHelper,
    private val deviceRepo: DeviceRepo,
    private val volumeModeTool: VolumeModeTool,
    private val limitEnforcer: VolumeLimitEnforcer,
    private val ownerRegistry: AudioStreamOwnerRegistry,
    private val upgradeRepo: UpgradeRepo,
    bluetoothSource: BluetoothRepo,
    private val generalSettings: GeneralSettings,
    private val devicesSettings: DevicesSettings,
    private val deviceCreator: NewDeviceCreator,
    private val speakerProvider: SpeakerDeviceProvider,
    private val reviewTool: ReviewTool,
    private val eqAppResolver: EqAppResolver,
    private val backgroundActivityGuard: BackgroundActivityGuard,
    eqCoordinator: EqCoordinator,
    eqEligibility: EqEligibility,
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
        // Piggyback on this poll to retire the blocked-action notification: granting the permission
        // from the system settings or the hint card below doesn't otherwise reach the monitor until
        // the next connect event, which can be hours away.
        backgroundActivityGuard.syncNotificationState()
        val hasDevicesNeedingOverlay = devices.any { it.launchPkgs.isNotEmpty() || it.showHomeScreen || it.keepAwake }
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

    private val dndAccessHintFlow = combine(
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(1000)
            }
        },
        generalSettings.isDndAccessHintDismissed.flow,
        devicesFlow,
    ) { _, isDismissed, devices ->
        val hasDevicesNeedingDnd = devices.any { device ->
            device.getVolume(AudioStream.Type.RINGTONE) != null ||
                device.getVolume(AudioStream.Type.NOTIFICATION) != null ||
                device.dndMode != null
        }
        permissionHelper.getDndAccessHint(isDismissed, hasDevicesNeedingDnd)
    }

    /**
     * At most one device can have the equalizer running, so the dashboard only ever tracks that one.
     *
     * Debounced like the equalizer screen's own row: players announce a close/open pair around every
     * track change, and the dashboard has no business flickering along with it.
     */
    private val eqStatusFlow: Flow<EqStatusFor?> = combine(
        devicesFlow,
        eqEligibility.hasEngine,
        eqCoordinator.targetAddress,
        eqCoordinator.sessionState,
    ) { devices, hasEngine, targetAddress, sessionState ->
        val address = targetAddress ?: return@combine null
        val device = devices.firstOrNull { it.address == address } ?: return@combine null
        deriveEqStatus(
            deviceAddress = address,
            eqEnabled = device.eqEnabled,
            hasCapabilities = hasEngine,
            targetAddress = targetAddress,
            sessionState = sessionState,
        )?.let { EqStatusFor(address, it) }
    }
        .distinctUntilChanged()
        .debounce(EQ_STATUS_DEBOUNCE)
        .mapLatest { statusFor -> statusFor?.let { it.copy(status = eqAppResolver.resolved(it.status)) } }

    private val devicesWithAppsFlow = combine(
        devicesFlow,
        appRepo.apps,
        ownerRegistry.ownerSnapshots,
    ) { devices, appInfos, ownerSnapshot ->
        val appInfoMap = appInfos.associateBy { it.packageName }
        val ownerAddresses = ownerSnapshot.ownerAddresses.toSet()
        devices.map { device ->
            DeviceWithApps(
                device = device,
                launchApps = device.launchPkgs.mapNotNull { pkgName ->
                    appInfoMap[pkgName]
                },
                volumeBands = AudioStream.Type.entries.mapNotNull { type ->
                    val band = if (device.address in ownerAddresses) {
                        // Same range [DashboardAction.AdjustVolume] will write through, so the
                        // travel the slider offers is the travel the hardware will accept.
                        limitEnforcer.allowedBand(device.getStreamId(type), devices, ownerAddresses)
                    } else {
                        // Not an owner: nothing is applied to the hardware, its own bounds are
                        // all that describe what it will be restored to.
                        device.getVolumeBand(type)
                    }
                    band?.let { type to it }
                }.toMap(),
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
        dndAccessHintFlow,
        devicesSettings.lockedDevices.flow,
        generalSettings.isSpeakerHintDismissed.flow,
        // The review prompt is a nice-to-have: a failing review backend must never take the whole
        // dashboard down with it, so it falls back to "don't ask".
        reviewTool.state.catch { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            emit(ReviewTool.State())
        },
        // Seeded, so the debounce and the equalizer engine probe behind it can never hold up the
        // dashboard itself.
        eqStatusFlow.onStart { emit(null) },
    ) { upgradeInfo, bluetoothState, devicesWithApps, batteryHint, overlayHint, notificationHint, dndHint, lockedDevices, speakerHintDismissed, review, eqStatus ->
        val showSpeakerHint = !speakerHintDismissed &&
            devicesWithApps.any { it.device.type != SourceDevice.Type.PHONE_SPEAKER } &&
            devicesWithApps.none { it.device.type == SourceDevice.Type.PHONE_SPEAKER }
        State(
            isProVersion = upgradeInfo.isPro,
            isBluetoothEnabled = bluetoothState.isEnabled,
            hasBluetoothPermission = bluetoothState.hasPermission,
            devicesWithApps = devicesWithApps,
            lockedDevices = lockedDevices,
            showBatteryOptimizationHint = batteryHint.shouldShow,
            batteryOptimizationIntent = batteryHint.intent,
            showAndroid10AppLaunchHint = overlayHint.shouldShow,
            android10AppLaunchIntent = overlayHint.intent,
            showNotificationPermissionHint = notificationHint.shouldShow,
            showDndAccessHint = dndHint.shouldShow,
            dndAccessIntent = dndHint.intent,
            showSpeakerHint = showSpeakerHint,
            // Lowest priority card: only asked for on an otherwise quiet dashboard, i.e. no hint or
            // permission card is competing for attention and the user actually has devices set up.
            showReviewCard = review.shouldAskForReview &&
                bluetoothState.hasPermission &&
                bluetoothState.isEnabled &&
                !batteryHint.shouldShow &&
                !overlayHint.shouldShow &&
                !dndHint.shouldShow &&
                !notificationHint.shouldShow &&
                !showSpeakerHint &&
                devicesWithApps.isNotEmpty(),
            eqStatus = eqStatus,
        )
    }.asStateFlow()

    data class DeviceWithApps(
        val device: ManagedDevice,
        val launchApps: List<AppInfo> = emptyList(),
        /** Per stream travel the sliders may offer, already resolved across the owner group. */
        val volumeBands: Map<AudioStream.Type, VolumeBand> = emptyMap(),
    )

    /** What the equalizer is doing, and which device it is doing it for. */
    data class EqStatusFor(
        val address: DeviceAddr,
        val status: EqStatus,
    )

    data class State(
        val isProVersion: Boolean = false,
        val isBluetoothEnabled: Boolean = false,
        val hasBluetoothPermission: Boolean = true,
        val devicesWithApps: List<DeviceWithApps> = emptyList(),
        val lockedDevices: Set<DeviceAddr> = emptySet(),
        val isLoading: Boolean = false,
        val showBatteryOptimizationHint: Boolean = false,
        val batteryOptimizationIntent: Intent? = null,
        val showAndroid10AppLaunchHint: Boolean = false,
        val android10AppLaunchIntent: Intent? = null,
        val showNotificationPermissionHint: Boolean = false,
        val showDndAccessHint: Boolean = false,
        val dndAccessIntent: Intent? = null,
        val showSpeakerHint: Boolean = false,
        val showReviewCard: Boolean = false,
        val eqStatus: EqStatusFor? = null,
    ) {
        // Convenience property for backwards compatibility
        val devices: List<ManagedDevice> get() = devicesWithApps.map { it.device }
    }

    // The upgrade icon is rendered off the passively collected state, which reports non-Pro while
    // billing is still settling. Re-check through the UI gate on tap so an actual Pro user lands on
    // the status view instead of an acquisition screen that immediately navigates back out.
    fun onUpgradeClicked() = launch {
        log(tag) { "onUpgradeClicked()" }
        navTo(Nav.Main.Upgrade(manage = upgradeRepo.isProForUi()))
    }

    // Play's in-app review flow needs the hosting Activity, which never belongs in a [DashboardAction]
    // value. Kept symmetrical with its dismiss counterpart, same shape as [onUpgradeClicked].
    fun reviewNow(activity: Activity) = launch {
        log(tag) { "reviewNow($activity)" }
        reviewTool.reviewNow(activity)
    }

    fun reviewDismiss() = launch {
        log(tag) { "reviewDismiss()" }
        reviewTool.dismiss()
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

            is DashboardAction.DismissDndAccessHint -> {
                launch {
                    generalSettings.isDndAccessHintDismissed.update { true }
                }
            }

            is DashboardAction.DismissSpeakerHint -> {
                launch {
                    generalSettings.isSpeakerHintDismissed.update { true }
                }
            }

            is DashboardAction.AddSpeakerDevice -> {
                val devices = deviceRepo.devices.first()
                val speakerAddr = speakerProvider.address

                if (devices.any { it.type == SourceDevice.Type.PHONE_SPEAKER }) {
                    navTo(Nav.Main.DeviceConfig(speakerAddr))
                    return@launch
                }

                if (devices.size >= DeviceLimits.FREE_DEVICE_LIMIT && !upgradeRepo.isProForUi()) {
                    navTo(Nav.Main.Upgrade())
                    return@launch
                }

                deviceCreator.createNewdevice(speakerAddr)
                navTo(Nav.Main.DeviceConfig(speakerAddr))
            }

            is DashboardAction.ToggleAdjustmentLock -> {
                devicesSettings.lockedDevices.update { locked ->
                    if (action.addr in locked) locked - action.addr else locked + action.addr
                }
            }

            is DashboardAction.AdjustVolume -> {
                val locked = devicesSettings.lockedDevices.flow.first()
                if (action.addr in locked) return@launch

                deviceRepo.updateDevice(action.addr) { oldConfig ->
                    oldConfig.updateVolume(action.type, action.volumeMode)
                }

                val device = deviceRepo.getDevice(action.addr)
                if (device?.isActive != true) return@launch

                val streamId = device.getStreamId(action.type)
                volumeModeTool.apply(
                    streamId = streamId,
                    streamType = action.type,
                    volumeMode = action.volumeMode,
                    visible = device.visibleAdjustments,
                    band = device.getVolumeBand(action.type),
                    allowedLevels = limitEnforcer.allowedLevels(
                        streamId = streamId,
                        devices = deviceRepo.currentDevices(),
                        ownerAddresses = ownerRegistry.ownerAddressesFor(streamId).toSet(),
                    ),
                )
            }
        }
    }

    companion object {
        /** How long the session picture has to hold still before the dashboard follows it. */
        private val EQ_STATUS_DEBOUNCE = 400.milliseconds
    }
}
