package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.WakeLockManager
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

@OptIn(ExperimentalCoroutinesApi::class)
class KeepAwakeModuleTest : BaseTest() {

    private val testAddress = "AA:BB:CC:DD:EE:FF"
    private val testSourceDevice = SourceDeviceWrapper(
        address = testAddress,
        alias = "TestDevice",
        name = "TestDevice",
        deviceType = SourceDevice.Type.HEADPHONES,
        isConnected = true,
    )

    private lateinit var wakeLockManager: WakeLockManager
    private lateinit var deviceRepo: DeviceRepo
    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>

    @BeforeEach
    fun setup() {
        wakeLockManager = mockk(relaxed = true)
        deviceRepo = mockk(relaxed = true)
        devicesFlow = MutableStateFlow(emptyList())
        every { deviceRepo.devices } returns devicesFlow
    }

    private fun device(
        keepAwake: Boolean = true,
        actionDelayMs: Long = 4000L,
        connected: Boolean = true,
        enabled: Boolean = true,
    ): ManagedDevice = ManagedDevice(
        isConnected = connected,
        device = testSourceDevice,
        config = DeviceConfigEntity(
            address = testAddress,
            keepAwake = keepAwake,
            actionDelay = actionDelayMs,
            isEnabled = enabled,
        ),
    )

    @Test
    fun `Connected with keepAwake disabled does nothing`() = runTest(UnconfinedTestDispatcher()) {
        val module = KeepAwakeModule(deviceRepo, wakeLockManager)
        val dev = device(keepAwake = false)

        module.handle(DeviceEvent.Connected(dev))

        coVerify(exactly = 0) { wakeLockManager.setWakeLock(any()) }
        coVerify(exactly = 0) { wakeLockManager.wakeScreenNow() }
    }

    @Test
    fun `Connected acquires wakelock and wakes screen immediately without reaction delay`() =
        runTest(UnconfinedTestDispatcher()) {
            val module = KeepAwakeModule(deviceRepo, wakeLockManager)
            // Use a long action delay - if KeepAwake mistakenly waits for it,
            // the verify-at-time-zero would fail.
            val dev = device(keepAwake = true, actionDelayMs = 60_000L)
            devicesFlow.value = listOf(dev)

            val job = launch { module.handle(DeviceEvent.Connected(dev)) }

            // Advance virtual clock by zero — only immediate (non-delayed) work runs.
            runCurrent()

            // Both calls must have happened *before* any reaction-delay would have completed.
            coVerifyOrder {
                wakeLockManager.setWakeLock(true)
                wakeLockManager.wakeScreenNow()
            }
            // No delay() was awaited on the connect path.
            coVerify(exactly = 0) { wakeLockManager.setWakeLock(false) }

            job.join()
        }

    @Test
    fun `Disconnected releases wakelock when no other keepAwake devices are active`() =
        runTest(UnconfinedTestDispatcher()) {
            val module = KeepAwakeModule(deviceRepo, wakeLockManager)
            val dev = device(keepAwake = true, actionDelayMs = 0L, connected = false)
            devicesFlow.value = listOf(dev)

            module.handle(DeviceEvent.Disconnected(dev))

            coVerify(exactly = 1) { wakeLockManager.setWakeLock(false) }
            coVerify(exactly = 0) { wakeLockManager.setWakeLock(true) }
            coVerify(exactly = 0) { wakeLockManager.wakeScreenNow() }
        }

    @Test
    fun `Disconnected keeps wakelock when another keepAwake device is still active`() =
        runTest(UnconfinedTestDispatcher()) {
            val module = KeepAwakeModule(deviceRepo, wakeLockManager)
            val disconnecting = device(keepAwake = true, actionDelayMs = 0L, connected = false)
            val stillActive = ManagedDevice(
                isConnected = true,
                device = SourceDeviceWrapper(
                    address = "11:22:33:44:55:66",
                    alias = "Other",
                    name = "Other",
                    deviceType = SourceDevice.Type.HEADPHONES,
                    isConnected = true,
                ),
                config = DeviceConfigEntity(
                    address = "11:22:33:44:55:66",
                    keepAwake = true,
                    isEnabled = true,
                ),
            )
            devicesFlow.value = listOf(disconnecting, stillActive)

            module.handle(DeviceEvent.Disconnected(disconnecting))

            coVerify(exactly = 0) { wakeLockManager.setWakeLock(false) }
            coVerify(exactly = 0) { wakeLockManager.setWakeLock(true) }
            coVerify(exactly = 0) { wakeLockManager.wakeScreenNow() }
        }

    @Test
    fun `Disconnected with keepAwake disabled does nothing`() = runTest(UnconfinedTestDispatcher()) {
        val module = KeepAwakeModule(deviceRepo, wakeLockManager)
        val dev = device(keepAwake = false)

        module.handle(DeviceEvent.Disconnected(dev))

        coVerify(exactly = 0) { wakeLockManager.setWakeLock(any()) }
        coVerify(exactly = 0) { wakeLockManager.wakeScreenNow() }
    }

    @Test
    fun `Connected never calls setWakeLock(false) and never queries deviceRepo`() =
        runTest(UnconfinedTestDispatcher()) {
            val module = KeepAwakeModule(deviceRepo, wakeLockManager)
            val dev = device(keepAwake = true, actionDelayMs = 100L)
            devicesFlow.value = listOf(dev)

            module.handle(DeviceEvent.Connected(dev))
            advanceTimeBy(10_000)

            coVerify(exactly = 1) { wakeLockManager.setWakeLock(true) }
            coVerify(exactly = 1) { wakeLockManager.wakeScreenNow() }
            coVerify(exactly = 0) { wakeLockManager.setWakeLock(false) }
        }

    @Test
    fun `Disconnected skips wakelock release if device reconnected during dispatcher barrier`() =
        runTest(UnconfinedTestDispatcher()) {
            // The dispatcher applies the settle barrier before our Disconnected handle
            // runs. By the time we read DeviceRepo here, the device may already be
            // connected again (rapid reconnect). Releasing the wakelock would then
            // immediately get re-acquired by the new Connected handler — wasteful and
            // briefly drops the wakelock for no reason.
            val module = KeepAwakeModule(deviceRepo, wakeLockManager)
            val reconnectedDev = device(keepAwake = true, actionDelayMs = 0L, connected = true)
            devicesFlow.value = listOf(reconnectedDev)

            module.handle(DeviceEvent.Disconnected(reconnectedDev))

            coVerify(exactly = 0) { wakeLockManager.setWakeLock(any()) }
            coVerify(exactly = 0) { wakeLockManager.wakeScreenNow() }
        }

    @Test
    fun `Disconnected releases wakelock if device reconnected but keepAwake was disabled`() =
        runTest(UnconfinedTestDispatcher()) {
            // If the user toggled keepAwake off (or disabled the device) during the
            // dispatcher barrier, the reconnected device is no longer an active
            // keep-awake host — the wakelock should be released, not held forever.
            val module = KeepAwakeModule(deviceRepo, wakeLockManager)
            val reconnectedButDisabled = device(keepAwake = false, actionDelayMs = 0L, connected = true)
            devicesFlow.value = listOf(reconnectedButDisabled)

            module.handle(DeviceEvent.Disconnected(reconnectedButDisabled.copy(config = reconnectedButDisabled.config.copy(keepAwake = true))))

            coVerify(exactly = 1) { wakeLockManager.setWakeLock(false) }
            coVerify(exactly = 0) { wakeLockManager.setWakeLock(true) }
        }

    @Test
    fun `Disconnected releases wakelock if device reconnected but no longer enabled`() =
        runTest(UnconfinedTestDispatcher()) {
            // device.isActive = isConnected && config.isEnabled. A reconnected-but-disabled
            // device is not active even with keepAwake=true. Cleanup should release.
            val module = KeepAwakeModule(deviceRepo, wakeLockManager)
            val reconnectedDisabled = device(keepAwake = true, actionDelayMs = 0L, connected = true, enabled = false)
            devicesFlow.value = listOf(reconnectedDisabled)

            module.handle(DeviceEvent.Disconnected(reconnectedDisabled))

            coVerify(exactly = 1) { wakeLockManager.setWakeLock(false) }
            coVerify(exactly = 0) { wakeLockManager.setWakeLock(true) }
        }
}
