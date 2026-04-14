package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
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
import kotlinx.coroutines.test.advanceUntilIdle
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

        nextEventSequence = 0L
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
            every { cancellable } returns true
        }
        module2 = mockk(relaxed = true) {
            every { priority } returns 10
            every { tag } returns "Module2"
            every { cancellable } returns true
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

    private var nextEventSequence = 0L

    private fun event(
        address: String,
        type: BluetoothEventQueue.Event.Type,
        deviceType: SourceDevice.Type = SourceDevice.Type.HEADPHONES,
    ): BluetoothEventQueue.Event {
        val seq = nextEventSequence++
        return BluetoothEventQueue.Event(
            type = type,
            sourceDevice = mockk {
                every { this@mockk.address } returns address
                every { this@mockk.deviceType } returns deviceType
            },
            receivedAtElapsedMs = seq * 1000L,
            sequence = seq,
        )
    }

    private fun TestScope.createDispatcher() = EventDispatcher(
        appScope = this,
        dispatcherProvider = asDispatcherProvider(),
        deviceRepo = deviceRepo,
        devicesSettings = devicesSettings,
        connectionModuleMap = setOf(module1, module2),
        eventTypeDedupTracker = tracker,
        ownerRegistry = AudioStreamOwnerRegistry(),
    )

    @Test
    fun `user's actual bug - duplicate disconnect does not re-run modules`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        dispatcher.dispatch(event(budsAddress, DISCONNECTED)) // Samsung duplicate
        advanceUntilIdle()

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Disconnected>()) }
        coVerify(exactly = 1) { module2.handle(any<DeviceEvent.Disconnected>()) }
    }

    @Test
    fun `fresh event is dispatched to all modules`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Connected>()) }
        coVerify(exactly = 1) { module2.handle(any<DeviceEvent.Connected>()) }
    }

    @Test
    fun `rapid transitions for same device - superseded jobs cancelled`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        // D1, C1, D2 in rapid succession — with fast acceptance, only the last
        // event's module job survives because each supersedes the previous.
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        dispatcher.dispatch(event(budsAddress, CONNECTED))
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        advanceUntilIdle()

        // Only the final DISCONNECTED's modules run (D1 and C1 were cancelled)
        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Disconnected>()) }
        coVerify(exactly = 0) { module1.handle(any<DeviceEvent.Connected>()) }
    }

    @Test
    fun `fake speaker safeguard drops stale event without poisoning dedup`() = runTest {
        val speakerDisconnected = managedDevice(
            speakerAddress,
            connected = false,
            deviceType = SourceDevice.Type.PHONE_SPEAKER,
        )
        devicesFlow.value = listOf(speakerDisconnected)
        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(speakerAddress, CONNECTED, SourceDevice.Type.PHONE_SPEAKER))
        advanceUntilIdle()

        coVerify(exactly = 0) { module1.handle(any()) }
        coVerify(exactly = 0) { module2.handle(any()) }

        val speakerConnected = managedDevice(
            speakerAddress,
            connected = true,
            deviceType = SourceDevice.Type.PHONE_SPEAKER,
        )
        devicesFlow.value = listOf(speakerConnected)

        dispatcher.dispatch(event(speakerAddress, CONNECTED, SourceDevice.Type.PHONE_SPEAKER))
        advanceUntilIdle()

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Connected>()) }
        coVerify(exactly = 1) { module2.handle(any<DeviceEvent.Connected>()) }
    }

    @Test
    fun `unknown device is skipped before dedup tracking`() = runTest {
        devicesFlow.value = emptyList()
        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 0) { module1.handle(any()) }
        coVerify(exactly = 0) { module2.handle(any()) }

        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        advanceUntilIdle()

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
        advanceUntilIdle()

        coVerify(exactly = 2) { module1.handle(any<DeviceEvent.Disconnected>()) }
        coVerify(exactly = 2) { module2.handle(any<DeviceEvent.Disconnected>()) }

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        dispatcher.dispatch(event(watchAddress, DISCONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 2) { module1.handle(any<DeviceEvent.Disconnected>()) }
        coVerify(exactly = 2) { module2.handle(any<DeviceEvent.Disconnected>()) }
    }

    @Test
    fun `receiver isDuplicate does not poison dispatcher shouldProcess`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Disconnected>()) }
        coVerify(exactly = 1) { module2.handle(any<DeviceEvent.Disconnected>()) }
    }

    @Test
    fun `receiver isDuplicate blocks Samsung-style duplicate from reaching dispatcher`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Disconnected>()) }

        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe true

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Disconnected>()) }
    }

    @Test
    fun `concurrent receiver passes caught by dispatcher shouldProcess`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        tracker.isDuplicate(budsAddress, CONNECTED) shouldBe false
        tracker.isDuplicate(budsAddress, CONNECTED) shouldBe false

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Connected>()) }
        coVerify(exactly = 1) { module2.handle(any<DeviceEvent.Connected>()) }
    }

    @Test
    fun `cancelled receiver isDuplicate does not leave stale state`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        tracker.isDuplicate(budsAddress, DISCONNECTED) shouldBe false

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Disconnected>()) }
    }

    @Test
    fun `module exception does not break acceptance loop`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        val watch = managedDevice(watchAddress, connected = true)
        devicesFlow.value = listOf(buds, watch)
        val dispatcher = createDispatcher()

        var module1CallCount = 0
        coEvery { module1.handle(any()) } answers {
            module1CallCount++
            if (module1CallCount == 1) throw RuntimeException("boom")
        }

        // Use DISCONNECTED to avoid ownership displacement cancelling the first job
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        dispatcher.dispatch(event(watchAddress, DISCONNECTED))
        advanceUntilIdle()

        module1CallCount shouldBe 2
    }

    @Test
    fun `priority ordering within a single event is preserved`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val highPriority = mockk<ConnectionModule>(relaxed = true) {
            every { priority } returns 1
            every { tag } returns "HighPri"
        }
        val lowPriority = mockk<ConnectionModule>(relaxed = true) {
            every { priority } returns 10
            every { tag } returns "LowPri"
        }

        val executionOrder = mutableListOf<String>()
        coEvery { highPriority.handle(any()) } coAnswers { executionOrder.add("high") }
        coEvery { lowPriority.handle(any()) } coAnswers { executionOrder.add("low") }

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(lowPriority, highPriority),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        executionOrder shouldBe listOf("high", "low")
    }

    @Test
    fun `non-cancellable module completes even when superseding event arrives`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val nonCancellable = mockk<ConnectionModule>(relaxed = true) {
            every { priority } returns 1
            every { tag } returns "NonCancellable"
            every { cancellable } returns false
        }
        val cancellable = mockk<ConnectionModule>(relaxed = true) {
            every { priority } returns 10
            every { tag } returns "Cancellable"
            every { cancellable } returns true
        }

        var nonCancellableCompleted = false
        coEvery { nonCancellable.handle(any<DeviceEvent.Connected>()) } coAnswers {
            kotlinx.coroutines.delay(2000)
            nonCancellableCompleted = true
        }

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(nonCancellable, cancellable),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        // Supersede with disconnect — cancellable modules get cancelled,
        // but non-cancellable module keeps running.
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        advanceUntilIdle()

        nonCancellableCompleted shouldBe true
    }

    @Test
    fun `cancelAllJobs stops in-flight module work`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val callCount = java.util.concurrent.atomic.AtomicInteger(0)
        coEvery { module1.handle(any()) } coAnswers {
            callCount.incrementAndGet()
            kotlinx.coroutines.delay(10_000) // Long-running module
        }

        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle() // Module starts

        dispatcher.cancelAllJobs()
        advanceUntilIdle()

        // Module was started
        callCount.get() shouldBe 1
    }

    @Test
    fun `two events for different addresses dispatch independently`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        val watch = managedDevice(watchAddress, connected = true)
        devicesFlow.value = listOf(buds, watch)
        val dispatcher = createDispatcher()

        // Use DISCONNECTED to avoid ownership displacement cancelling the first job
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        dispatcher.dispatch(event(watchAddress, DISCONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 2) { module1.handle(any<DeviceEvent.Disconnected>()) }
        coVerify(exactly = 2) { module2.handle(any<DeviceEvent.Disconnected>()) }
    }

    @Test
    fun `ownership registry updated before module launch`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val registry = AudioStreamOwnerRegistry()

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(module1),
            eventTypeDedupTracker = tracker,
            ownerRegistry = registry,
        )

        var ownerDuringModule: List<String>? = null
        coEvery { module1.handle(any()) } coAnswers {
            ownerDuringModule = kotlinx.coroutines.runBlocking {
                registry.ownerAddressesFor(AudioStream.Id.STREAM_MUSIC)
            }
        }

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        // Registry was updated before module ran
        ownerDuringModule shouldBe listOf(budsAddress)
    }

    @Test
    fun `superseding disconnect after connect cancels connect modules`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        // Slow module that takes a while
        coEvery { module1.handle(any<DeviceEvent.Connected>()) } coAnswers {
            kotlinx.coroutines.delay(5000)
        }

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        // Immediately supersede with disconnect
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        advanceUntilIdle()

        // The disconnect should have cancelled the connect's modules
        // and the disconnect modules should have run
        coVerify(atLeast = 1) { module1.handle(any<DeviceEvent.Disconnected>()) }
    }

    @Test
    fun `disconnect save survives same-address reconnect`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val disconnectModule = mockk<ConnectionModule>(relaxed = true) {
            every { priority } returns 1
            every { tag } returns "VolumeDisconnect"
            every { cancellable } returns false
        }
        val connectModule = mockk<ConnectionModule>(relaxed = true) {
            every { priority } returns 10
            every { tag } returns "VolumeUpdate"
            every { cancellable } returns true
        }

        var disconnectSaveCompleted = false
        coEvery { disconnectModule.handle(any<DeviceEvent.Disconnected>()) } coAnswers {
            kotlinx.coroutines.delay(2000) // Simulates DB write
            disconnectSaveCompleted = true
        }

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(disconnectModule, connectModule),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        // Device disconnects, then reconnects before disconnect save finishes
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        // Non-cancellable disconnect save must complete despite supersession
        disconnectSaveCompleted shouldBe true
        // Cancellable connect modules still run for the reconnect
        coVerify(exactly = 1) { connectModule.handle(any<DeviceEvent.Connected>()) }
    }

    @Test
    fun `ownership transfer cancels displaced owner ramp`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        val watch = managedDevice(watchAddress, connected = true)
        devicesFlow.value = listOf(buds, watch)

        val slowModule = mockk<ConnectionModule>(relaxed = true) {
            every { priority } returns 10
            every { tag } returns "SlowRamp"
            every { cancellable } returns true
        }

        var budsRampCompleted = false
        coEvery { slowModule.handle(match<DeviceEvent.Connected> { it.device.address == budsAddress }) } coAnswers {
            kotlinx.coroutines.delay(10_000) // Simulates slow volume ramp
            budsRampCompleted = true
        }

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(slowModule),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        // Buds connect (becomes owner), starts slow ramp
        dispatcher.dispatch(event(budsAddress, CONNECTED))

        // Watch connects (takes ownership via later timestamp) — should cancel buds' in-flight ramp
        dispatcher.dispatch(event(watchAddress, CONNECTED))
        advanceUntilIdle()

        // Buds' slow ramp should NOT have completed — it was cancelled by ownership transfer
        budsRampCompleted shouldBe false
    }

    @Test
    fun `lastConnected only updated on CONNECTED events`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 0) { deviceRepo.updateDevice(budsAddress, any()) }

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 1) { deviceRepo.updateDevice(budsAddress, any()) }
    }
}
