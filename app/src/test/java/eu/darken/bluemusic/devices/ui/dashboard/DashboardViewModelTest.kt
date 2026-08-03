package eu.darken.bluemusic.devices.ui.dashboard

import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.bluetooth.core.speaker.SpeakerDeviceProvider
import eu.darken.bluemusic.common.apps.AppRepo
import eu.darken.bluemusic.common.datastore.DataStoreValue
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.NewDeviceCreator
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.main.core.GeneralSettings
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
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

    private lateinit var deviceCreator: NewDeviceCreator
    private lateinit var speakerProvider: SpeakerDeviceProvider

    private lateinit var batteryHintDismissed: DataStoreValue<Boolean>
    private lateinit var android10HintDismissed: DataStoreValue<Boolean>
    private lateinit var notificationHintDismissed: DataStoreValue<Boolean>
    private lateinit var dndHintDismissed: DataStoreValue<Boolean>
    private lateinit var speakerHintDismissed: DataStoreValue<Boolean>
    private lateinit var lockedDevices: DataStoreValue<Set<DeviceAddr>>

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
        deviceCreator = mockk(relaxed = true)
        speakerProvider = mockk(relaxed = true)
        every { speakerProvider.address } returns SPEAKER_ADDR

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

        every { generalSettings.isBatteryOptimizationHintDismissed } returns batteryHintDismissed
        every { generalSettings.isAndroid10AppLaunchHintDismissed } returns android10HintDismissed
        every { generalSettings.isNotificationPermissionHintDismissed } returns notificationHintDismissed
        every { generalSettings.isDndAccessHintDismissed } returns dndHintDismissed
        every { generalSettings.isSpeakerHintDismissed } returns speakerHintDismissed
        every { devicesSettings.lockedDevices } returns lockedDevices

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
        volumeModeTool = mockk(relaxed = true),
        upgradeRepo = upgradeRepo,
        bluetoothSource = bluetoothRepo,
        generalSettings = generalSettings,
        devicesSettings = devicesSettings,
        deviceCreator = deviceCreator,
        speakerProvider = speakerProvider,
        dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
        navCtrl = navCtrl,
        appRepo = appRepo,
    )

    private fun device(
        address: String = "AA:BB:CC:DD:EE:FF",
        keepAwake: Boolean = false,
        showHomeScreen: Boolean = false,
        launchPkgs: List<String> = emptyList(),
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

    companion object {
        private const val SPEAKER_ADDR = "self:speaker:main"
    }
}
