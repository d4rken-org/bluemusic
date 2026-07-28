package eu.darken.bluemusic.bluetooth.ui.discover

import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.NewDeviceCreator
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
class DiscoverViewModelTest : BaseTest() {

    private val discoverable = SourceDeviceWrapper(
        address = "11:22:33:44:55:66",
        alias = "New",
        name = "New",
        deviceType = SourceDevice.Type.HEADPHONES,
        isConnected = false,
    )

    private fun managed(address: String): ManagedDevice = ManagedDevice(
        isConnected = true,
        device = SourceDeviceWrapper(
            address = address,
            alias = address,
            name = address,
            deviceType = SourceDevice.Type.HEADPHONES,
            isConnected = true,
        ),
        config = DeviceConfigEntity(address = address, isEnabled = true),
    )

    private fun TestScope.viewModel(
        infos: MutableStateFlow<UpgradeRepo.Info>,
        managedCount: Int,
        creator: NewDeviceCreator = mockk(relaxed = true),
        navCtrl: NavigationController = mockk(relaxed = true),
    ): DiscoverViewModel {
        val deviceRepo = mockk<DeviceRepo>(relaxed = true).apply {
            every { devices } returns MutableStateFlow(
                (0 until managedCount).map { managed("AA:BB:CC:DD:EE:0$it") }
            )
        }
        val bluetoothRepo = mockk<BluetoothRepo>(relaxed = true).apply {
            every { state } returns MutableStateFlow(
                BluetoothRepo.State(isEnabled = true, hasPermission = true, devices = setOf(discoverable))
            )
        }
        return DiscoverViewModel(
            deviceRepo = deviceRepo,
            bluetoothSource = bluetoothRepo,
            upgradeRepo = mockUpgradeRepo(infos),
            dispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
            navCtrl = navCtrl,
            deviceCreator = creator,
        )
    }

    @Test
    fun `a free user under the limit adds the device without consulting the gate`() = runTest {
        val creator = mockk<NewDeviceCreator>(relaxed = true)
        val vm = viewModel(
            infos = fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = false)),
            managedCount = 0,
            creator = creator,
        )

        vm.onDeviceSelected(discoverable)
        advanceUntilIdle()

        coVerify { creator.createNewdevice(discoverable.address) }
    }

    @Test
    fun `a settled free user at the limit is sent to the upgrade screen`() = runTest {
        val creator = mockk<NewDeviceCreator>(relaxed = true)
        val vm = viewModel(
            infos = fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = true)),
            managedCount = 2,
            creator = creator,
        )

        val event = async { vm.events.first() }
        runCurrent()
        vm.onDeviceSelected(discoverable)
        advanceUntilIdle()

        event.await() shouldBe DiscoverEvent.RequiresUpgrade
        coVerify(exactly = 0) { creator.createNewdevice(any()) }
    }

    @Test
    fun `a pro user at the limit is not blocked while billing is still settling`() = runTest {
        val creator = mockk<NewDeviceCreator>(relaxed = true)
        val infos = fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = false))
        val vm = viewModel(infos = infos, managedCount = 2, creator = creator)

        vm.onDeviceSelected(discoverable)
        // Suspend inside the gate's wait window without burning its timeout.
        runCurrent()
        infos.value = FakeUpgradeInfo(isPro = true, isSettled = true)
        advanceUntilIdle()

        coVerify { creator.createNewdevice(discoverable.address) }
    }
}
