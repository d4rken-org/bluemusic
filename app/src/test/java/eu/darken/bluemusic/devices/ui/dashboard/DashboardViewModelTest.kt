package eu.darken.bluemusic.devices.ui.dashboard

import android.app.Activity
import android.media.AudioManager
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.bluetooth.core.speaker.SpeakerDeviceProvider
import eu.darken.bluemusic.common.apps.AppRepo
import eu.darken.bluemusic.common.datastore.DataStoreValue
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.common.review.ReviewTool
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.NewDeviceCreator
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.eq.core.EqCoordinator
import eu.darken.bluemusic.eq.core.EqEligibility
import eu.darken.bluemusic.eq.core.EqSession
import eu.darken.bluemusic.eq.core.EqSessionState
import eu.darken.bluemusic.eq.ui.EqAppResolver
import eu.darken.bluemusic.eq.ui.EqStatus
import eu.darken.bluemusic.eq.ui.EqStatusApp
import eu.darken.bluemusic.main.core.GeneralSettings
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeBand
import eu.darken.bluemusic.monitor.core.audio.VolumeLimitEnforcer
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeModeTool
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Instant
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.audio.normalRingerTool
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.upgrade.FakeUpgradeInfo
import testhelpers.upgrade.fakeUpgradeInfos

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest : BaseTest() {

    private lateinit var permissionHelper: PermissionHelper
    private lateinit var deviceRepo: DeviceRepo
    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>
    private lateinit var upgradeRepo: UpgradeRepo
    private lateinit var upgradeInfos: MutableStateFlow<UpgradeRepo.Info>
    private lateinit var bluetoothRepo: BluetoothRepo
    private lateinit var generalSettings: GeneralSettings
    private lateinit var devicesSettings: DevicesSettings
    private lateinit var appRepo: AppRepo
    private lateinit var navCtrl: NavigationController

    private lateinit var volumeModeTool: VolumeModeTool
    private lateinit var limitEnforcer: VolumeLimitEnforcer
    private lateinit var ownerRegistry: AudioStreamOwnerRegistry
    private lateinit var deviceCreator: NewDeviceCreator
    private lateinit var speakerProvider: SpeakerDeviceProvider
    private lateinit var reviewTool: ReviewTool
    private lateinit var reviewStates: MutableStateFlow<ReviewTool.State>

    private lateinit var eqCoordinator: EqCoordinator
    private lateinit var eqTargetAddress: MutableStateFlow<DeviceAddr?>
    private lateinit var eqSessions: MutableStateFlow<EqSessionState>
    private lateinit var eqEligibility: EqEligibility

    private lateinit var batteryHintDismissed: DataStoreValue<Boolean>
    private lateinit var android10HintDismissed: DataStoreValue<Boolean>
    private lateinit var notificationHintDismissed: DataStoreValue<Boolean>
    private lateinit var dndHintDismissed: DataStoreValue<Boolean>
    private lateinit var speakerHintDismissed: DataStoreValue<Boolean>
    private lateinit var lockedDevices: DataStoreValue<Set<DeviceAddr>>
    private lateinit var monitoringEnabled: DataStoreValue<Boolean>

    @BeforeEach
    fun setup() {
        permissionHelper = mockk(relaxed = true)
        deviceRepo = mockk(relaxed = true)
        upgradeRepo = mockk(relaxed = true)
        bluetoothRepo = mockk(relaxed = true)
        generalSettings = mockk(relaxed = true)
        devicesSettings = mockk(relaxed = true)
        appRepo = mockk(relaxed = true)
        navCtrl = mockk(relaxed = true)
        volumeModeTool = mockk(relaxed = true)
        ownerRegistry = AudioStreamOwnerRegistry()
        limitEnforcer = VolumeLimitEnforcer(
            VolumeTool(mockk<AudioManager>(relaxed = true).also {
                every { it.getStreamMaxVolume(any()) } returns 15
            }),
            normalRingerTool(),
        )
        deviceCreator = mockk(relaxed = true)
        speakerProvider = mockk(relaxed = true)
        every { speakerProvider.address } returns SPEAKER_ADDR

        // Explicit, never relaxed: a relaxed ReviewTool would hand out an empty state flow and
        // silently turn every "the card is shown" assertion into a false negative.
        reviewStates = MutableStateFlow(ReviewTool.State())
        reviewTool = mockk<ReviewTool>().apply {
            every { state } returns reviewStates
            coJustRun { dismiss() }
            coJustRun { reviewNow(any()) }
        }

        eqTargetAddress = MutableStateFlow(null)
        eqSessions = MutableStateFlow(EqSessionState())
        eqCoordinator = mockk<EqCoordinator>().apply {
            every { targetAddress } returns eqTargetAddress
            every { sessionState } returns eqSessions
        }
        eqEligibility = mockk<EqEligibility>().apply {
            every { hasEngine } returns MutableStateFlow(true)
        }

        devicesFlow = MutableStateFlow(emptyList())
        every { deviceRepo.devices } returns devicesFlow

        upgradeInfos = fakeUpgradeInfos()
        every { upgradeRepo.upgradeInfo } returns upgradeInfos
        every { bluetoothRepo.state } returns MutableStateFlow(
            BluetoothRepo.State(isEnabled = true, hasPermission = true, devices = emptySet())
        )
        every { appRepo.apps } returns MutableStateFlow(emptySet())

        batteryHintDismissed = stubBoolValue(true)
        android10HintDismissed = stubBoolValue(false)
        notificationHintDismissed = stubBoolValue(true)
        dndHintDismissed = stubBoolValue(true)
        speakerHintDismissed = stubBoolValue(false)
        lockedDevices = stubSetValue(emptySet())
        monitoringEnabled = stubBoolValue(true)

        every { generalSettings.isBatteryOptimizationHintDismissed } returns batteryHintDismissed
        every { generalSettings.isAndroid10AppLaunchHintDismissed } returns android10HintDismissed
        every { generalSettings.isNotificationPermissionHintDismissed } returns notificationHintDismissed
        every { generalSettings.isDndAccessHintDismissed } returns dndHintDismissed
        every { generalSettings.isSpeakerHintDismissed } returns speakerHintDismissed
        every { devicesSettings.lockedDevices } returns lockedDevices
        every { devicesSettings.isEnabled } returns monitoringEnabled

        // Default: hint helpers report shouldShow=false unless test sets up otherwise.
        every { permissionHelper.getBatteryOptimizationHint(any()) } returns
                PermissionHelper.PermissionHint(shouldShow = false)
        every { permissionHelper.getNotificationPermissionHint(any()) } returns
                PermissionHelper.PermissionHint(shouldShow = false)
        every { permissionHelper.getDndAccessHint(any(), any()) } returns
                PermissionHelper.PermissionHint(shouldShow = false)
    }

    @Suppress("UNCHECKED_CAST")
    private fun stubBoolValue(initial: Boolean): DataStoreValue<Boolean> {
        val mock = mockk<DataStoreValue<Boolean>>(relaxed = true)
        every { mock.flow } returns MutableStateFlow(initial)
        return mock
    }

    @Suppress("UNCHECKED_CAST")
    private fun stubSetValue(initial: Set<DeviceAddr>): DataStoreValue<Set<DeviceAddr>> {
        val mock = mockk<DataStoreValue<Set<DeviceAddr>>>(relaxed = true)
        every { mock.flow } returns MutableStateFlow(initial)
        return mock
    }

    private fun TestScope.viewModel() = DashboardViewModel(
        permissionHelper = permissionHelper,
        deviceRepo = deviceRepo,
        volumeModeTool = volumeModeTool,
        limitEnforcer = limitEnforcer,
        ownerRegistry = ownerRegistry,
        upgradeRepo = upgradeRepo,
        bluetoothSource = bluetoothRepo,
        generalSettings = generalSettings,
        devicesSettings = devicesSettings,
        deviceCreator = deviceCreator,
        speakerProvider = speakerProvider,
        reviewTool = reviewTool,
        eqAppResolver = mockk<EqAppResolver>().apply {
            coEvery { resolved(any()) } answers { firstArg() }
        },
        backgroundActivityGuard = mockk(relaxed = true),
        eqCoordinator = eqCoordinator,
        eqEligibility = eqEligibility,
        dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
        navCtrl = navCtrl,
        appRepo = appRepo,
    )

    private fun device(
        address: String = "AA:BB:CC:DD:EE:FF",
        keepAwake: Boolean = false,
        showHomeScreen: Boolean = false,
        launchPkgs: List<String> = emptyList(),
        eqEnabled: Boolean = false,
    ): ManagedDevice = ManagedDevice(
        isConnected = true,
        device = SourceDeviceWrapper(
            address = address,
            alias = "TestDevice",
            name = "TestDevice",
            deviceType = SourceDevice.Type.HEADPHONES,
            isConnected = true,
        ),
        config = DeviceConfigEntity(
            address = address,
            isEnabled = true,
            keepAwake = keepAwake,
            showHomeScreen = showHomeScreen,
            launchPkgs = launchPkgs,
            eqEnabled = eqEnabled,
        ),
    )

    private fun speakerDevice(): ManagedDevice = ManagedDevice(
        isConnected = false,
        device = SourceDeviceWrapper(
            address = SPEAKER_ADDR,
            alias = "Device speaker",
            name = "Device speaker",
            deviceType = SourceDevice.Type.PHONE_SPEAKER,
            isConnected = false,
        ),
        config = DeviceConfigEntity(
            address = SPEAKER_ADDR,
            isEnabled = true,
        ),
    )

    @Test
    fun `keepAwake-only device flags hasDevicesNeedingOverlay = true`() = runTest(UnconfinedTestDispatcher()) {
        val needsOverlaySlot = slot<Boolean>()
        every {
            permissionHelper.getOverlayPermissionHint(any(), capture(needsOverlaySlot))
        } returns PermissionHelper.PermissionHint(shouldShow = true)

        devicesFlow.value = listOf(device(keepAwake = true))

        val state = viewModel().state.filterNotNull().first()
        state.showAndroid10AppLaunchHint shouldBe true
        needsOverlaySlot.captured shouldBe true
    }

    @Test
    fun `device with no overlay-relevant flags does not need overlay`() = runTest(UnconfinedTestDispatcher()) {
        val needsOverlaySlot = slot<Boolean>()
        every {
            permissionHelper.getOverlayPermissionHint(any(), capture(needsOverlaySlot))
        } returns PermissionHelper.PermissionHint(shouldShow = false)

        devicesFlow.value = listOf(device(keepAwake = false, showHomeScreen = false, launchPkgs = emptyList()))

        viewModel().state.filterNotNull().first()
        needsOverlaySlot.captured shouldBe false
    }

    @Test
    fun `device with launchPkgs flags hasDevicesNeedingOverlay = true`() = runTest(UnconfinedTestDispatcher()) {
        val needsOverlaySlot = slot<Boolean>()
        every {
            permissionHelper.getOverlayPermissionHint(any(), capture(needsOverlaySlot))
        } returns PermissionHelper.PermissionHint(shouldShow = false)

        devicesFlow.value = listOf(device(launchPkgs = listOf("com.example.app")))

        viewModel().state.filterNotNull().first()
        needsOverlaySlot.captured shouldBe true
    }

    @Test
    fun `upgrade icon tap routes a settled non-pro user to the acquisition screen`() =
        runTest(UnconfinedTestDispatcher()) {
            upgradeInfos.value = FakeUpgradeInfo(isPro = false, isSettled = true)

            viewModel().onUpgradeClicked()
            advanceUntilIdle()

            verify { navCtrl.goTo(Nav.Main.Upgrade(manage = false), any(), any()) }
        }

    @Test
    fun `upgrade icon tap waits out the unsettled window and routes a pro user to manage`() =
        runTest(UnconfinedTestDispatcher()) {
            upgradeInfos.value = FakeUpgradeInfo(isPro = false, isSettled = false)

            val vm = viewModel()
            vm.onUpgradeClicked()
            // Still gated: nothing decided while billing hasn't settled.
            verify(exactly = 0) { navCtrl.goTo(any(), any(), any()) }

            upgradeInfos.value = FakeUpgradeInfo(isPro = true, isSettled = true)
            advanceUntilIdle()

            verify { navCtrl.goTo(Nav.Main.Upgrade(manage = true), any(), any()) }
        }

    @Test
    fun `device with showHomeScreen flags hasDevicesNeedingOverlay = true`() = runTest(UnconfinedTestDispatcher()) {
        val needsOverlaySlot = slot<Boolean>()
        every {
            permissionHelper.getOverlayPermissionHint(any(), capture(needsOverlaySlot))
        } returns PermissionHelper.PermissionHint(shouldShow = false)

        devicesFlow.value = listOf(device(showHomeScreen = true))

        viewModel().state.filterNotNull().first()
        needsOverlaySlot.captured shouldBe true
    }

    @Test
    fun `speaker hint shows while the speaker is unmanaged`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(device())

        viewModel().state.filterNotNull().first().showSpeakerHint shouldBe true
    }

    @Test
    fun `speaker hint stays hidden once dismissed`() = runTest(UnconfinedTestDispatcher()) {
        every { speakerHintDismissed.flow } returns MutableStateFlow(true)
        devicesFlow.value = listOf(device())

        viewModel().state.filterNotNull().first().showSpeakerHint shouldBe false
    }

    @Test
    fun `speaker hint stays hidden without any managed device`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = emptyList()

        viewModel().state.filterNotNull().first().showSpeakerHint shouldBe false
    }

    @Test
    fun `speaker hint stays hidden when the speaker is already managed`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(device(), speakerDevice())

        viewModel().state.filterNotNull().first().showSpeakerHint shouldBe false
    }

    @Test
    fun `speaker hint shows for a free user at the device limit`() = runTest(UnconfinedTestDispatcher()) {
        upgradeInfos.value = FakeUpgradeInfo(isPro = false, isSettled = true)
        devicesFlow.value = listOf(device(address = "AA:AA:AA:AA:AA:AA"), device(address = "BB:BB:BB:BB:BB:BB"))

        viewModel().state.filterNotNull().first().showSpeakerHint shouldBe true
    }

    @Test
    fun `adding the speaker creates it and opens its config`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(device())

        viewModel().action(DashboardAction.AddSpeakerDevice)
        advanceUntilIdle()

        coVerify { deviceCreator.createNewdevice(SPEAKER_ADDR) }
        verify { navCtrl.goTo(Nav.Main.DeviceConfig(SPEAKER_ADDR), any(), any()) }
    }

    @Test
    fun `adding the speaker at the free limit routes to upgrade`() = runTest(UnconfinedTestDispatcher()) {
        upgradeInfos.value = FakeUpgradeInfo(isPro = false, isSettled = true)
        devicesFlow.value = listOf(device(address = "AA:AA:AA:AA:AA:AA"), device(address = "BB:BB:BB:BB:BB:BB"))

        viewModel().action(DashboardAction.AddSpeakerDevice)
        advanceUntilIdle()

        coVerify(exactly = 0) { deviceCreator.createNewdevice(any()) }
        verify { navCtrl.goTo(Nav.Main.Upgrade(), any(), any()) }
    }

    @Test
    fun `adding an already managed speaker opens its config even at the free limit`() =
        runTest(UnconfinedTestDispatcher()) {
            upgradeInfos.value = FakeUpgradeInfo(isPro = false, isSettled = true)
            devicesFlow.value = listOf(device(address = "AA:AA:AA:AA:AA:AA"), speakerDevice())

            viewModel().action(DashboardAction.AddSpeakerDevice)
            advanceUntilIdle()

            coVerify(exactly = 0) { deviceCreator.createNewdevice(any()) }
            verify(exactly = 0) { navCtrl.goTo(Nav.Main.Upgrade(), any(), any()) }
            verify { navCtrl.goTo(Nav.Main.DeviceConfig(SPEAKER_ADDR), any(), any()) }
        }

    @Test
    fun `dismissing the speaker hint persists the flag`() = runTest(UnconfinedTestDispatcher()) {
        val updateSlot = slot<(Boolean) -> Boolean?>()

        viewModel().action(DashboardAction.DismissSpeakerHint)
        advanceUntilIdle()

        coVerify { speakerHintDismissed.update(capture(updateSlot)) }
        updateSlot.captured(false) shouldBe true
    }

    @Test
    fun `state reports monitoring disabled when the master switch is off`() = runTest(UnconfinedTestDispatcher()) {
        val enabled = MutableStateFlow(false)
        every { monitoringEnabled.flow } returns enabled

        val vm = viewModel()
        backgroundScope.launch { vm.state.collect { } }
        runCurrent()

        vm.state.value!!.isMonitoringEnabled shouldBe false

        enabled.value = true
        runCurrent()

        vm.state.value!!.isMonitoringEnabled shouldBe true
    }

    @Test
    fun `enable monitoring action writes the setting`() = runTest(UnconfinedTestDispatcher()) {
        viewModel().action(DashboardAction.EnableMonitoring)
        advanceUntilIdle()

        coVerify { devicesSettings.setEnabled(true) }
    }

    /**
     * Every gate the review card sits behind, satisfied at once. The negative tests below each flip
     * exactly one of them, so a gate that silently stops mattering surfaces as a failure.
     */
    private fun quietDashboard() {
        every { bluetoothRepo.state } returns MutableStateFlow(
            BluetoothRepo.State(isEnabled = true, hasPermission = true, devices = emptySet())
        )
        every { permissionHelper.getBatteryOptimizationHint(any()) } returns
                PermissionHelper.PermissionHint(shouldShow = false)
        every { permissionHelper.getOverlayPermissionHint(any(), any()) } returns
                PermissionHelper.PermissionHint(shouldShow = false)
        every { permissionHelper.getNotificationPermissionHint(any()) } returns
                PermissionHelper.PermissionHint(shouldShow = false)
        every { permissionHelper.getDndAccessHint(any(), any()) } returns
                PermissionHelper.PermissionHint(shouldShow = false)
        // The shared fixture leaves the speaker hint active as soon as a device exists.
        every { speakerHintDismissed.flow } returns MutableStateFlow(true)
        devicesFlow.value = listOf(device())
        reviewStates.value = ReviewTool.State(shouldAskForReview = true)
    }

    @Test
    fun `the review card shows on a quiet dashboard`() = runTest(UnconfinedTestDispatcher()) {
        quietDashboard()

        viewModel().state.filterNotNull().first().showReviewCard shouldBe true
    }

    @Test
    fun `the review card yields to a permission hint`() = runTest(UnconfinedTestDispatcher()) {
        quietDashboard()
        every { permissionHelper.getNotificationPermissionHint(any()) } returns
                PermissionHelper.PermissionHint(shouldShow = true)

        viewModel().state.filterNotNull().first().showReviewCard shouldBe false
    }

    @Test
    fun `the review card stays hidden while bluetooth is off`() = runTest(UnconfinedTestDispatcher()) {
        quietDashboard()
        every { bluetoothRepo.state } returns MutableStateFlow(
            BluetoothRepo.State(isEnabled = false, hasPermission = true, devices = emptySet())
        )

        viewModel().state.filterNotNull().first().showReviewCard shouldBe false
    }

    @Test
    fun `the review card stays hidden while monitoring is off`() = runTest(UnconfinedTestDispatcher()) {
        quietDashboard()
        every { monitoringEnabled.flow } returns MutableStateFlow(false)

        viewModel().state.filterNotNull().first().showReviewCard shouldBe false
    }

    @Test
    fun `the review card stays hidden without any managed device`() = runTest(UnconfinedTestDispatcher()) {
        quietDashboard()
        devicesFlow.value = emptyList()

        viewModel().state.filterNotNull().first().showReviewCard shouldBe false
    }

    @Test
    fun `dismissing the review card delegates to the review tool`() = runTest(UnconfinedTestDispatcher()) {
        viewModel().reviewDismiss()
        advanceUntilIdle()

        coVerify { reviewTool.dismiss() }
    }

    @Test
    fun `the review action forwards the hosting activity unchanged`() = runTest(UnconfinedTestDispatcher()) {
        val activity = mockk<Activity>()
        val activitySlot = slot<Activity>()

        viewModel().reviewNow(activity)
        advanceUntilIdle()

        // Play's flow is launched on whatever Activity the screen handed over, a substitute would
        // put the review dialog on the wrong window.
        coVerify { reviewTool.reviewNow(capture(activitySlot)) }
        activitySlot.captured shouldBe activity
    }

    // region equalizer status

    /** One controlled session, the way an app that cooperates with the system equalizer reports it. */
    private fun playingSessions(packageName: String = "com.spotify.music") = EqSessionState(
        listening = true,
        generation = 1L,
        sessions = mapOf(
            11 to EqSession(
                sessionId = 11,
                generation = 1L,
                openedAt = Instant.EPOCH,
                packageName = packageName,
                attached = true,
                hasControl = true,
            )
        ),
    )

    /** The state once the status debounce has run out. */
    private fun TestScope.settledState(): DashboardViewModel.State {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect { } }
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        return vm.state.value!!
    }

    @Test
    fun `the device the equalizer runs for gets a status`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(device(eqEnabled = true))
        eqTargetAddress.value = "AA:BB:CC:DD:EE:FF"
        eqSessions.value = playingSessions()

        settledState().eqStatus shouldBe DashboardViewModel.EqStatusFor(
            address = "AA:BB:CC:DD:EE:FF",
            status = EqStatus.Active(
                app = EqStatusApp("com.spotify.music"),
                multiple = false,
                since = Instant.EPOCH,
            ),
        )
    }

    @Test
    fun `a device that is not the equalizer target gets no status`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(device(eqEnabled = true))
        // The equalizer runs for a device that isn't on the dashboard list at all.
        eqTargetAddress.value = "11:22:33:44:55:66"
        eqSessions.value = playingSessions()

        settledState().eqStatus shouldBe null
    }

    @Test
    fun `no target at all means no status`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(device(eqEnabled = true))
        eqSessions.value = playingSessions()

        settledState().eqStatus shouldBe null
    }

    @Test
    fun `a target with the equalizer switched off gets no status`() = runTest(UnconfinedTestDispatcher()) {
        devicesFlow.value = listOf(device(eqEnabled = false))
        eqTargetAddress.value = "AA:BB:CC:DD:EE:FF"
        eqSessions.value = playingSessions()

        settledState().eqStatus shouldBe null
    }

    @Test
    fun `without an equalizer engine there is no status`() = runTest(UnconfinedTestDispatcher()) {
        every { eqEligibility.hasEngine } returns MutableStateFlow(false)
        devicesFlow.value = listOf(device(eqEnabled = true))
        eqTargetAddress.value = "AA:BB:CC:DD:EE:FF"
        eqSessions.value = playingSessions()

        settledState().eqStatus shouldBe null
    }

    // endregion

    companion object {
        private const val SPEAKER_ADDR = "self:speaker:main"
    }

    @Test
    fun `grouped earbuds - the slider band is the strictest bound in the group`() = runTest(UnconfinedTestDispatcher()) {
        val budL = "AA:BB:CC:DD:EE:01"
        val budR = "AA:BB:CC:DD:EE:02"

        fun bud(addr: String, musicVolumeMax: Float) = ManagedDevice(
            isConnected = true,
            device = SourceDeviceWrapper(
                address = addr,
                alias = "Buds3 Pro",
                name = "Buds3 Pro",
                deviceType = SourceDevice.Type.HEADPHONES,
                isConnected = true,
            ),
            config = DeviceConfigEntity(
                address = addr,
                isEnabled = true,
                musicVolume = 1f,
                volumeLimit = true,
                musicVolumeMax = musicVolumeMax,
            ),
        )

        devicesFlow.value = listOf(bud(budL, 0.8f), bud(budR, 0.4f))
        ownerRegistry.onDeviceConnected(budL, "Buds3 Pro", SourceDevice.Type.HEADPHONES, 1000L, 0L)
        ownerRegistry.onDeviceConnected(budR, "Buds3 Pro", SourceDevice.Type.HEADPHONES, 1002L, 1L)

        val state = viewModel().state.filterNotNull().first()

        // Strictest ceiling is 0.4 → level 6 of 0..15 → 0.4 back in percent space, for both cards.
        state.devicesWithApps.forEach {
            it.volumeBands[AudioStream.Type.MUSIC] shouldBe VolumeBand(min = 0f, max = 0.4f)
        }
    }

    @Test
    fun `a device outside the owner group keeps its own band`() = runTest(UnconfinedTestDispatcher()) {
        val ownerAddr = "AA:BB:CC:DD:EE:01"
        val otherAddr = "11:22:33:44:55:66"

        fun dev(addr: String, name: String, musicVolumeMax: Float) = ManagedDevice(
            isConnected = true,
            device = SourceDeviceWrapper(
                address = addr,
                alias = name,
                name = name,
                deviceType = SourceDevice.Type.HEADPHONES,
                isConnected = true,
            ),
            config = DeviceConfigEntity(
                address = addr,
                isEnabled = true,
                musicVolume = 1f,
                volumeLimit = true,
                musicVolumeMax = musicVolumeMax,
            ),
        )

        devicesFlow.value = listOf(dev(ownerAddr, "AirPods", 0.4f), dev(otherAddr, "Speaker", 0.8f))
        ownerRegistry.onDeviceConnected(otherAddr, "Speaker", SourceDevice.Type.HEADPHONES, 1000L, 0L)
        ownerRegistry.onDeviceConnected(ownerAddr, "AirPods", SourceDevice.Type.HEADPHONES, 5000L, 1L)

        val state = viewModel().state.filterNotNull().first()

        val bands = state.devicesWithApps.associate { it.device.address to it.volumeBands[AudioStream.Type.MUSIC] }
        bands[ownerAddr] shouldBe VolumeBand(min = 0f, max = 0.4f)
        bands[otherAddr] shouldBe VolumeBand(min = null, max = 0.8f)
    }

    @Test
    fun `adjusting a device outside the owner group never reaches the hardware`() =
        runTest(UnconfinedTestDispatcher()) {
            val ownerAddr = "AA:BB:CC:DD:EE:01"
            val otherAddr = "11:22:33:44:55:66"

            fun config(addr: String, musicVolumeMax: Float) = DeviceConfigEntity(
                address = addr,
                isEnabled = true,
                musicVolume = 0.1f,
                volumeLimit = true,
                musicVolumeMax = musicVolumeMax,
            )

            fun dev(addr: String, name: String, musicVolumeMax: Float) = ManagedDevice(
                isConnected = true,
                device = SourceDeviceWrapper(
                    address = addr,
                    alias = name,
                    name = name,
                    deviceType = SourceDevice.Type.HEADPHONES,
                    isConnected = true,
                ),
                config = config(addr, musicVolumeMax),
            )

            devicesFlow.value = listOf(dev(ownerAddr, "AirPods", 0.4f), dev(otherAddr, "Speaker", 0.8f))
            ownerRegistry.onDeviceConnected(otherAddr, "Speaker", SourceDevice.Type.HEADPHONES, 1000L, 0L)
            ownerRegistry.onDeviceConnected(ownerAddr, "AirPods", SourceDevice.Type.HEADPHONES, 5000L, 1L)

            val update = slot<(DeviceConfigEntity) -> DeviceConfigEntity>()
            coJustRun { deviceRepo.updateDevice(otherAddr, capture(update)) }

            viewModel().action(
                DashboardAction.AdjustVolume(otherAddr, AudioStream.Type.MUSIC, VolumeMode.Normal(0.8f))
            )
            advanceUntilIdle()

            // Its own target is stored, ready for when it becomes the routed device.
            update.captured(config(otherAddr, 0.8f)).musicVolume shouldBe 0.8f
            // The routed group's hardware stays untouched.
            coVerify(exactly = 0) {
                volumeModeTool.apply(any(), any(), any(), any(), any(), any(), any())
            }
        }
}
