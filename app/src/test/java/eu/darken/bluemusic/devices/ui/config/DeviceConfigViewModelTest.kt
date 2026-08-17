package eu.darken.bluemusic.devices.ui.config

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.upgrade.FakeUpgradeInfo
import testhelpers.upgrade.fakeUpgradeInfos
import testhelpers.upgrade.mockUpgradeRepo

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceConfigViewModelTest : BaseTest() {

    private val address = "AA:BB:CC:DD:EE:FF"

    private val device = ManagedDevice(
        isConnected = true,
        device = SourceDeviceWrapper(
            address = address,
            alias = "TestDevice",
            name = "TestDevice",
            deviceType = SourceDevice.Type.HEADPHONES,
            isConnected = true,
        ),
        config = DeviceConfigEntity(address = address, isEnabled = true),
    )

    private lateinit var deviceRepo: DeviceRepo
    private lateinit var navCtrl: NavigationController

    private fun TestScope.viewModel(infos: MutableStateFlow<UpgradeRepo.Info>): DeviceConfigViewModel {
        deviceRepo = mockk<DeviceRepo>(relaxed = true).apply {
            every { devices } returns MutableStateFlow(listOf(device))
            coEvery { isManaged(address) } returns true
        }
        navCtrl = mockk(relaxed = true)
        return DeviceConfigViewModel(
            deviceAddress = address,
            deviceRepo = deviceRepo,
            volumeTool = mockk(relaxed = true),
            upgradeRepo = mockUpgradeRepo(infos),
            appRepo = mockk<eu.darken.bluemusic.common.apps.AppRepo>(relaxed = true).apply {
                every { apps } returns MutableStateFlow(emptySet())
            },
            dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
            navCtrl = navCtrl,
            permissionHelper = mockk(relaxed = true),
        )
    }

    @Test
    fun `a settled free user hits the pro wall on a gated toggle`() = runTest {
        val vm = viewModel(fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = true)))

        val event = async { vm.events.first() }
        runCurrent()
        vm.handleAction(ConfigAction.OnToggleAutoPlay)
        advanceUntilIdle()

        event.await() shouldBe ConfigEvent.RequiresPro
        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
    }

    @Test
    fun `a pro user is not walled on a gated toggle while billing is still settling`() = runTest {
        val infos = fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = false))
        val vm = viewModel(infos)

        vm.handleAction(ConfigAction.OnToggleAutoPlay)
        // Suspend inside the gate's wait window without burning its timeout.
        runCurrent()
        infos.value = FakeUpgradeInfo(isPro = true, isSettled = true)
        advanceUntilIdle()

        coVerify { deviceRepo.updateDevice(address, any()) }
    }

    @Test
    fun `the launch-app route is gated too`() = runTest {
        val vm = viewModel(fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = true)))

        val event = async { vm.events.first() }
        runCurrent()
        vm.handleAction(ConfigAction.OnLaunchAppClicked)
        advanceUntilIdle()

        event.await() shouldBe ConfigEvent.RequiresPro
        verify(exactly = 0) { navCtrl.goTo(Nav.Main.AppSelection(address), any(), any()) }
    }

    @Test
    fun `the equalizer route is gated too`() = runTest {
        val vm = viewModel(fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = true)))

        val event = async { vm.events.first() }
        runCurrent()
        vm.handleAction(ConfigAction.OnEqClicked)
        advanceUntilIdle()

        event.await() shouldBe ConfigEvent.RequiresPro
        verify(exactly = 0) { navCtrl.goTo(Nav.Main.DeviceEq(address), any(), any()) }
    }

    @Test
    fun `a pro user reaches the equalizer screen`() = runTest {
        val vm = viewModel(fakeUpgradeInfos(FakeUpgradeInfo(isPro = true, isSettled = true)))

        vm.handleAction(ConfigAction.OnEqClicked)
        advanceUntilIdle()

        verify { navCtrl.goTo(Nav.Main.DeviceEq(address), any(), any()) }
    }

    @Test
    fun `the volume lock toggle routes a late-settling pro user through`() = runTest {
        val infos = fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = false))
        val vm = viewModel(infos)

        vm.handleAction(ConfigAction.OnToggleVolumeLock)
        runCurrent()
        infos.value = FakeUpgradeInfo(isPro = true, isSettled = true)
        advanceUntilIdle()

        coVerify { deviceRepo.updateDevice(address, any()) }
    }
}
