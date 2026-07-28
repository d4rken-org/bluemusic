package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.alert.AlertTool
import eu.darken.bluemusic.monitor.core.alert.AlertType
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.upgrade.FakeUpgradeInfo
import testhelpers.upgrade.fakeUpgradeInfos
import testhelpers.upgrade.mockUpgradeRepo

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionAlertModuleTest : BaseTest() {

    private val testAddress = "AA:BB:CC:DD:EE:FF"

    private fun device(): ManagedDevice = ManagedDevice(
        isConnected = true,
        device = SourceDeviceWrapper(
            address = testAddress,
            alias = "TestDevice",
            name = "TestDevice",
            deviceType = SourceDevice.Type.HEADPHONES,
            isConnected = true,
        ),
        config = DeviceConfigEntity(
            address = testAddress,
            isEnabled = true,
            connectionAlertType = AlertType.VIBRATION,
        ),
    )

    @Test
    fun `a settled non-pro user gets no alert`() = runTest {
        val alertTool = mockk<AlertTool>(relaxed = true)
        val repo = mockUpgradeRepo(fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = true)))
        val module = ConnectionAlertModule(alertTool, repo)

        module.handle(DeviceEvent.Connected(device()))
        advanceUntilIdle()

        verify(exactly = 0) { alertTool.playAlert(any(), any()) }
    }

    @Test
    fun `a pro user whose entitlement lands late still gets the alert`() = runTest {
        val alertTool = mockk<AlertTool>(relaxed = true)
        val infos = fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = false))
        val module = ConnectionAlertModule(alertTool, mockUpgradeRepo(infos))

        val handling = launch { module.handle(DeviceEvent.Connected(device())) }
        // runCurrent, not advanceUntilIdle: let the gate suspend inside the reconciliation window
        // WITHOUT burning through its timeout, otherwise this would pass via the fail-open path.
        runCurrent()
        infos.value = FakeUpgradeInfo(isPro = true, isSettled = true)
        handling.join()

        verify { alertTool.playAlert(AlertType.VIBRATION, any()) }
    }

    @Test
    fun `an unsettled window that never resolves does not block the alert`() = runTest {
        val alertTool = mockk<AlertTool>(relaxed = true)
        // Never settles: isProSettled must fail open rather than silently drop a paying user's alert.
        val repo = mockUpgradeRepo(fakeUpgradeInfos(FakeUpgradeInfo(isPro = false, isSettled = false)))
        val module = ConnectionAlertModule(alertTool, repo)

        module.handle(DeviceEvent.Connected(device()))
        advanceUntilIdle()

        verify { alertTool.playAlert(AlertType.VIBRATION, any()) }
    }
}
