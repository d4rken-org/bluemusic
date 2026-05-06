package eu.darken.bluemusic.devices.ui.dashboard

import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.common.apps.AppRepo
import eu.darken.bluemusic.common.datastore.DataStoreValue
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.main.core.GeneralSettings
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest : BaseTest() {

    private lateinit var permissionHelper: PermissionHelper
    private lateinit var deviceRepo: DeviceRepo
    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>
    private lateinit var upgradeRepo: UpgradeRepo
    private lateinit var bluetoothRepo: BluetoothRepo
    private lateinit var generalSettings: GeneralSettings
    private lateinit var devicesSettings: DevicesSettings
    private lateinit var appRepo: AppRepo
    private lateinit var navCtrl: NavigationController

    private lateinit var batteryHintDismissed: DataStoreValue<Boolean>
    private lateinit var android10HintDismissed: DataStoreValue<Boolean>
    private lateinit var notificationHintDismissed: DataStoreValue<Boolean>
    private lateinit var dndHintDismissed: DataStoreValue<Boolean>
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

        devicesFlow = MutableStateFlow(emptyList())
        every { deviceRepo.devices } returns devicesFlow

        every { upgradeRepo.upgradeInfo } returns MutableStateFlow(
            object : UpgradeRepo.Info {
                override val isUpgraded = false
                override val type = UpgradeRepo.Type.FOSS
                override val upgradedAt: java.time.Instant? = null
                override val error: Throwable? = null
            }
        )
        every { bluetoothRepo.state } returns MutableStateFlow(
            BluetoothRepo.State(isEnabled = true, hasPermission = true, devices = emptySet())
        )
        every { appRepo.apps } returns MutableStateFlow(emptySet())

        batteryHintDismissed = stubBoolValue(true)
        android10HintDismissed = stubBoolValue(false)
        notificationHintDismissed = stubBoolValue(true)
        dndHintDismissed = stubBoolValue(true)
        lockedDevices = stubSetValue(emptySet())

        every { generalSettings.isBatteryOptimizationHintDismissed } returns batteryHintDismissed
        every { generalSettings.isAndroid10AppLaunchHintDismissed } returns android10HintDismissed
        every { generalSettings.isNotificationPermissionHintDismissed } returns notificationHintDismissed
        every { generalSettings.isDndAccessHintDismissed } returns dndHintDismissed
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

    private fun viewModel() = DashboardViewModel(
        permissionHelper = permissionHelper,
        deviceRepo = deviceRepo,
        volumeModeTool = mockk(relaxed = true),
        upgradeRepo = upgradeRepo,
        bluetoothSource = bluetoothRepo,
        generalSettings = generalSettings,
        devicesSettings = devicesSettings,
        dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher()),
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
    fun `device with showHomeScreen flags hasDevicesNeedingOverlay = true`() = runTest(UnconfinedTestDispatcher()) {
        val needsOverlaySlot = slot<Boolean>()
        every {
            permissionHelper.getOverlayPermissionHint(any(), capture(needsOverlaySlot))
        } returns PermissionHelper.PermissionHint(shouldShow = false)

        devicesFlow.value = listOf(device(showHomeScreen = true))

        viewModel().state.filterNotNull().first()
        needsOverlaySlot.captured shouldBe true
    }
}
