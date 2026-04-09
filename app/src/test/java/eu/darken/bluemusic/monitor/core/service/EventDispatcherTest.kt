package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.service.BluetoothEventQueue.Event.Type.CONNECTED
import eu.darken.bluemusic.monitor.core.service.BluetoothEventQueue.Event.Type.DISCONNECTED
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.asDispatcherProvider
import testhelpers.time.FakeMonotonicClock

class EventDispatcherTest : BaseTest() {

    private val budsAddress = "34:E3:FB:94:C2:AF"
    private val speakerAddress = "self:speaker:main"
    private val watchAddress = "11:22:33:44:55:66"

    private lateinit var deviceRepo: DeviceRepo
    private lateinit var tracker: EventTypeDedupTracker
    private lateinit var devicesSettings: DevicesSettings
    private lateinit var enabledStateFlow: MutableStateFlow<DevicesSettings.EnabledState>
    private lateinit var currentEnabledState: DevicesSettings.EnabledState
    private lateinit var clock: FakeMonotonicClock
    private lateinit var trackerScope: CoroutineScope
    private lateinit var module1: ConnectionModule
    private lateinit var module2: ConnectionModule
    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>

    @BeforeEach
    fun setup() {
        devicesFlow = MutableStateFlow(emptyList())
        deviceRepo = mockk(relaxed = true)
        every { deviceRepo.devices } returns devicesFlow
        coEvery { deviceRepo.updateDevice(any(), any()) } returns Unit

        devicesSettings = mockk()
        currentEnabledState = DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 0L)
        enabledStateFlow = MutableStateFlow(currentEnabledState)
        every { devicesSettings.enabledState } returns enabledStateFlow
        coEvery { devicesSettings.currentEnabledState() } answers { currentEnabledState }

        clock = FakeMonotonicClock(now = 0L)
        trackerScope = CoroutineScope(Dispatchers.Unconfined + Job())

        tracker = EventTypeDedupTracker(
            appScope = trackerScope,
            devicesSettings = devicesSettings,
            clock = clock,
        )
        module1 = mockk(relaxed = true) {
            every { priority } returns 10
            every { tag } returns "Module1"
        }
        module2 = mockk(relaxed = true) {
            every { priority } returns 10
            every { tag } returns "Module2"
        }
    }

    @AfterEach
    fun teardown() {
        trackerScope.cancel()
    }

    private fun managedDevice(
        address: String,
        connected: Boolean = true,
        deviceType: SourceDevice.Type = SourceDevice.Type.HEADPHONES,
    ): ManagedDevice = mockk(relaxed = true) {
        every { this@mockk.address } returns address
        every { isConnected } returns connected
        every { device } returns mockk(relaxed = true) {
            every { this@mockk.deviceType } returns deviceType
        }
    }

    private fun event(
        address: String,
        type: BluetoothEventQueue.Event.Type,
        deviceType: SourceDevice.Type = SourceDevice.Type.HEADPHONES,
    ): BluetoothEventQueue.Event = BluetoothEventQueue.Event(
        type = type,
        sourceDevice = mockk {
            every { this@mockk.address } returns address
            every { this@mockk.deviceType } returns deviceType
        },
    )

    private fun TestScope.createDispatcher() = EventDispatcher(
        dispatcherProvider = asDispatcherProvider(),
        deviceRepo = deviceRepo,
        devicesSettings = devicesSettings,
        connectionModuleMap = setOf(module1, module2),
        eventTypeDedupTracker = tracker,
    )

    /**
     * The user's bug from scott's log (`bluemusic_3.2.4-rc0_20260408T123558Z`):
     * buds disconnect, speaker takes over, buds re-emit a stale DISCONNECTED
     * ~10s later. Without the dedup, the second Disconnected runs the module
     * pipeline and `VolumeDisconnectModule` captures mid-ramp (near-zero) system
     * volumes. With the dedup, the second dispatch is a no-op for modules.
     */
    @Test
    fun `user's actual bug - duplicate disconnect does not re-run modules`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        dispatcher.dispatch(event(budsAddress, DISCONNECTED)) // Samsung duplicate

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Disconnected>()) }
        coVerify(exactly = 1) { module2.handle(any<DeviceEvent.Disconnected>()) }
    }

    @Test
    fun `fresh event is dispatched to all modules`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(budsAddress, CONNECTED))

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Connected>()) }
        coVerify(exactly = 1) { module2.handle(any<DeviceEvent.Connected>()) }
    }

    @Test
    fun `real transition D1 C1 D2 dispatches all three`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        dispatcher.dispatch(event(budsAddress, CONNECTED))
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))

        coVerify(exactly = 2) { module1.handle(any<DeviceEvent.Disconnected>()) }
        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Connected>()) }
        coVerify(exactly = 2) { module2.handle(any<DeviceEvent.Disconnected>()) }
        coVerify(exactly = 1) { module2.handle(any<DeviceEvent.Connected>()) }
    }

    /**
     * When a stale fake speaker CONNECTED arrives while the speaker isn't the
     * active device, the safeguard early-returns. It must NOT populate the
     * dedup map — otherwise a subsequent legit fake speaker CONNECTED would be
     * silently swallowed.
     */
    @Test
    fun `fake speaker safeguard drops stale event without poisoning dedup`() = runTest {
        val speakerDisconnected = managedDevice(
            speakerAddress,
            connected = false,
            deviceType = SourceDevice.Type.PHONE_SPEAKER,
        )
        devicesFlow.value = listOf(speakerDisconnected)
        val dispatcher = createDispatcher()

        // Stale fake speaker CONNECTED — safeguard drops it
        dispatcher.dispatch(event(speakerAddress, CONNECTED, SourceDevice.Type.PHONE_SPEAKER))

        coVerify(exactly = 0) { module1.handle(any()) }
        coVerify(exactly = 0) { module2.handle(any()) }

        // Now the speaker is active and a legit CONNECTED arrives — must process
        val speakerConnected = managedDevice(
            speakerAddress,
            connected = true,
            deviceType = SourceDevice.Type.PHONE_SPEAKER,
        )
        devicesFlow.value = listOf(speakerConnected)

        dispatcher.dispatch(event(speakerAddress, CONNECTED, SourceDevice.Type.PHONE_SPEAKER))

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Connected>()) }
        coVerify(exactly = 1) { module2.handle(any<DeviceEvent.Connected>()) }
    }

    @Test
    fun `unknown device is skipped before dedup tracking`() = runTest {
        devicesFlow.value = emptyList() // No managed devices
        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))

        coVerify(exactly = 0) { module1.handle(any()) }
        coVerify(exactly = 0) { module2.handle(any()) }

        // If the device later becomes managed, the first DISCONNECTED must still process
        // (dedup state for the unknown-device address should not be poisoned)
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Disconnected>()) }
        coVerify(exactly = 1) { module2.handle(any<DeviceEvent.Disconnected>()) }
    }

    @Test
    fun `events for different devices are independent`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        val watch = managedDevice(watchAddress, connected = true)
        devicesFlow.value = listOf(buds, watch)
        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        dispatcher.dispatch(event(watchAddress, DISCONNECTED))

        // Both processed
        coVerify(exactly = 2) { module1.handle(any<DeviceEvent.Disconnected>()) }
        coVerify(exactly = 2) { module2.handle(any<DeviceEvent.Disconnected>()) }

        // Duplicates of each are blocked
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        dispatcher.dispatch(event(watchAddress, DISCONNECTED))

        coVerify(exactly = 2) { module1.handle(any<DeviceEvent.Disconnected>()) }
        coVerify(exactly = 2) { module2.handle(any<DeviceEvent.Disconnected>()) }
    }

    // --- Integration tests (W3): shared real tracker between receiver-side and
    // dispatcher-side call sites. These would have caught the C1 double-dedup bug.

    /**
     * Regression test for the C1 double-dedup bug. A receiver-side `isDuplicate`
     * call must be a pure read — it must not prevent the dispatcher's
     * `shouldProcess` from accepting the same event as a first occurrence.
     */
    @Test
    fun `receiver isDuplicate does not poison dispatcher shouldProcess`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        // Simulate receiver-side pre-filter check — first event is not a duplicate.
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false

        // Dispatcher commits the event — modules MUST run.
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Disconnected>()) }
        coVerify(exactly = 1) { module2.handle(any<DeviceEvent.Disconnected>()) }
    }

    /**
     * Full receiver→dispatcher flow for a Samsung-style duplicate. The
     * receiver-side `isDuplicate` check catches the duplicate before the
     * dispatcher runs.
     */
    @Test
    fun `receiver isDuplicate blocks Samsung-style duplicate from reaching dispatcher`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        // First event: receiver passes, dispatcher commits.
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Disconnected>()) }

        // Samsung duplicate arrives ~10s later: receiver-side check catches it.
        // (In production the receiver would `return` before reaching the dispatcher.)
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true

        // Modules still called exactly once.
        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Disconnected>()) }
    }

    /**
     * Race case: two concurrent receiver coroutines on Dispatchers.Default both
     * see the map as empty, both pass `isDuplicate`, both submit the event to
     * the queue. The dispatcher (single-threaded consumer) catches the duplicate
     * via `shouldProcess` and runs modules exactly once.
     */
    @Test
    fun `concurrent receiver passes caught by dispatcher shouldProcess`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        // Both receiver "coroutines" see empty map and pass.
        tracker.isDuplicate(budsAddress, CONNECTED) shouldBe false
        tracker.isDuplicate(budsAddress, CONNECTED) shouldBe false

        // Both reach the dispatcher (simulating the queue delivering both events).
        dispatcher.dispatch(event(budsAddress, CONNECTED))
        dispatcher.dispatch(event(budsAddress, CONNECTED))

        // Dispatcher caught the second → modules run exactly once.
        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Connected>()) }
        coVerify(exactly = 1) { module2.handle(any<DeviceEvent.Connected>()) }
    }

    /**
     * W1 resolution check: a cancelled receiver coroutine that called
     * `isDuplicate` and then died (before reaching the dispatcher) must not
     * leave stale state that blocks the next real event.
     */
    @Test
    fun `cancelled receiver isDuplicate does not leave stale state`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        // Simulated receiver coroutine reads state and then "dies" before
        // reaching eventQueue.submit. No dispatcher call for this event.
        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false

        // Next real event arrives later. Dispatcher should accept it as first
        // occurrence — receiver's read-only check didn't poison anything.
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Disconnected>()) }
    }
}
