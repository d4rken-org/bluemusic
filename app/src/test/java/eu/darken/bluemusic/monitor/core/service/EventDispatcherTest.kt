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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.asDispatcherProvider
import testhelpers.time.FakeMonotonicClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
        module1 = mockConnectionModule(name = "Module1")
        module2 = mockConnectionModule(name = "Module2")
    }

    private fun mockConnectionModule(
        name: String,
        priority: Int = 10,
        cancellable: Boolean = true,
        appliesTo: Boolean = true,
    ): ConnectionModule = mockk(relaxed = true) {
        every { this@mockk.priority } returns priority
        every { tag } returns name
        every { this@mockk.cancellable } returns cancellable
        every { appliesTo(any()) } returns appliesTo
    }

    @AfterEach
    fun teardown() {
        trackerScope.cancel()
    }

    private fun managedDevice(
        address: String,
        connected: Boolean = true,
        deviceType: SourceDevice.Type = SourceDevice.Type.HEADPHONES,
        actionDelay: java.time.Duration = java.time.Duration.ZERO,
    ): ManagedDevice = mockk(relaxed = true) {
        every { this@mockk.address } returns address
        every { isConnected } returns connected
        every { this@mockk.actionDelay } returns actionDelay
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
    fun `event is dropped while app is disabled`() = runTest {
        currentEnabledState = DevicesSettings.EnabledState(isEnabled = false, toggleEpoch = 1L)
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 0) { module1.handle(any()) }
        coVerify(exactly = 0) { module2.handle(any()) }
        coVerify(exactly = 0) { deviceRepo.updateDevice(any(), any()) }
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

        val highPriority = mockConnectionModule(name = "HighPri", priority = 1)
        val lowPriority = mockConnectionModule(name = "LowPri", priority = 10)

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

        val nonCancellable = mockConnectionModule(name = "NonCancellable", priority = 1, cancellable = false)
        val cancellable = mockConnectionModule(name = "Cancellable", priority = 10)

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
    fun `dispatch drops events after cancelAllJobs`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        dispatcher.cancelAllJobs()

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 0) { module1.handle(any()) }
    }

    @Test
    fun `cancelAllJobs waits for in-flight dispatch and cancels what it launches`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val dispatchEntered = CompletableDeferred<Unit>()
        val releaseDispatch = CompletableDeferred<Unit>()
        coEvery { devicesSettings.currentEnabledState() } coAnswers {
            dispatchEntered.complete(Unit)
            releaseDispatch.await()
            currentEnabledState
        }

        val dispatcher = createDispatcher()
        val dispatchJob = launch { dispatcher.dispatch(event(budsAddress, CONNECTED)) }

        dispatchEntered.await()

        val cancelFinished = CountDownLatch(1)
        val cancelThread = Thread {
            dispatcher.cancelAllJobs()
            cancelFinished.countDown()
        }
        cancelThread.start()

        cancelFinished.await(100, TimeUnit.MILLISECONDS) shouldBe false

        releaseDispatch.complete(Unit)
        dispatchJob.join()

        cancelFinished.await(1, TimeUnit.SECONDS) shouldBe true
        cancelThread.join()

        advanceUntilIdle()

        coVerify(exactly = 0) { module1.handle(any()) }
        coVerify(exactly = 0) { module2.handle(any()) }
    }

    @Test
    fun `resetForNewSession re-enables dispatch after shutdown`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)
        val dispatcher = createDispatcher()

        dispatcher.cancelAllJobs()
        dispatcher.resetForNewSession()

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 1) { module1.handle(any<DeviceEvent.Connected>()) }
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

        val disconnectModule = mockConnectionModule(name = "VolumeDisconnect", priority = 1, cancellable = false)
        val connectModule = mockConnectionModule(name = "VolumeUpdate", priority = 10)

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

        val slowModule = mockConnectionModule(name = "SlowRamp", priority = 10)

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
    fun `cancelAllJobs cancels non-cancellable jobs too`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val nonCancellable = mockConnectionModule(name = "NonCancellable", priority = 1, cancellable = false)

        var jobCompleted = false
        coEvery { nonCancellable.handle(any<DeviceEvent.Disconnected>()) } coAnswers {
            kotlinx.coroutines.delay(10_000)
            jobCompleted = true
        }

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(nonCancellable),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        dispatcher.cancelAllJobs()
        advanceUntilIdle()

        jobCompleted shouldBe false
    }

    @Test
    fun `multiple non-cancellable jobs from same device all tracked`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val nonCancellable = mockConnectionModule(name = "NonCancellable", priority = 1, cancellable = false)

        var completionCount = java.util.concurrent.atomic.AtomicInteger(0)
        coEvery { nonCancellable.handle(any<DeviceEvent.Disconnected>()) } coAnswers {
            kotlinx.coroutines.delay(5000)
            completionCount.incrementAndGet()
        }

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(nonCancellable),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        // D1, C1 (clears dedup), D2 — two disconnect events for the same device
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        dispatcher.dispatch(event(budsAddress, CONNECTED))
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))

        // Cancel all before they finish
        dispatcher.cancelAllJobs()
        advanceUntilIdle()

        // Both non-cancellable disconnect jobs should have been cancelled
        completionCount.get() shouldBe 0
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

    @Test
    fun `isIdle starts true before any dispatch`() = runTest {
        val dispatcher = createDispatcher()
        dispatcher.isIdle.value shouldBe true
    }

    @Test
    fun `isIdle flips false on dispatch and back to true after cancellable job completes`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val running = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { module1.handle(any()) } coAnswers {
            running.complete(Unit)
            release.await()
        }

        val dispatcher = createDispatcher()
        dispatcher.dispatch(event(budsAddress, CONNECTED))

        running.await()
        dispatcher.isIdle.value shouldBe false

        release.complete(Unit)
        advanceUntilIdle()

        dispatcher.isIdle.value shouldBe true
    }

    @Test
    fun `isIdle flips false on dispatch and back to true after non-cancellable job completes`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val running = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val nonCancellable = mockConnectionModule(name = "NonCancellable", priority = 1, cancellable = false)
        coEvery { nonCancellable.handle(any()) } coAnswers {
            running.complete(Unit)
            release.await()
        }

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(nonCancellable),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))

        running.await()
        dispatcher.isIdle.value shouldBe false

        release.complete(Unit)
        advanceUntilIdle()

        dispatcher.isIdle.value shouldBe true
    }

    @Test
    fun `isIdle stays false until both cancellable and non-cancellable jobs complete`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val cancellableRunning = CompletableDeferred<Unit>()
        val cancellableRelease = CompletableDeferred<Unit>()
        val nonCancellableRunning = CompletableDeferred<Unit>()
        val nonCancellableRelease = CompletableDeferred<Unit>()

        val cancellableMod = mockConnectionModule(name = "Cancellable", priority = 10)
        coEvery { cancellableMod.handle(any()) } coAnswers {
            cancellableRunning.complete(Unit)
            cancellableRelease.await()
        }
        val nonCancellableMod = mockConnectionModule(name = "NonCancellable", priority = 1, cancellable = false)
        coEvery { nonCancellableMod.handle(any()) } coAnswers {
            nonCancellableRunning.complete(Unit)
            nonCancellableRelease.await()
        }

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(cancellableMod, nonCancellableMod),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )
        dispatcher.dispatch(event(budsAddress, CONNECTED))

        cancellableRunning.await()
        nonCancellableRunning.await()
        dispatcher.isIdle.value shouldBe false

        // Release only the cancellable; non-cancellable still in flight
        cancellableRelease.complete(Unit)
        advanceUntilIdle()
        dispatcher.isIdle.value shouldBe false

        // Release the non-cancellable too
        nonCancellableRelease.complete(Unit)
        advanceUntilIdle()
        dispatcher.isIdle.value shouldBe true
    }

    @Test
    fun `awaitIdle returns immediately when already idle`() = runTest {
        val dispatcher = createDispatcher()
        dispatcher.awaitIdle()
        dispatcher.isIdle.value shouldBe true
    }

    @Test
    fun `awaitIdle suspends until in-flight jobs complete`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val running = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { module1.handle(any()) } coAnswers {
            running.complete(Unit)
            release.await()
        }

        val dispatcher = createDispatcher()
        dispatcher.dispatch(event(budsAddress, CONNECTED))
        running.await()

        var awaitIdleReturned = false
        val waiter = launch {
            dispatcher.awaitIdle()
            awaitIdleReturned = true
        }

        advanceUntilIdle()
        awaitIdleReturned shouldBe false

        release.complete(Unit)
        advanceUntilIdle()
        waiter.join()
        awaitIdleReturned shouldBe true
    }

    @Test
    fun `superseded job still counts as in-flight until invokeOnCompletion runs`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val firstRunning = CompletableDeferred<Unit>()
        val secondRunning = CompletableDeferred<Unit>()
        val secondRelease = CompletableDeferred<Unit>()
        var callCount = 0
        coEvery { module1.handle(any()) } coAnswers {
            val n = ++callCount
            if (n == 1) {
                firstRunning.complete(Unit)
                kotlinx.coroutines.delay(60_000)
            } else {
                secondRunning.complete(Unit)
                secondRelease.await()
            }
        }

        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        firstRunning.await()
        dispatcher.isIdle.value shouldBe false

        // Supersede with a disconnect — this cancels the first job, but its
        // invokeOnCompletion runs eventually and untracks it.
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        secondRunning.await()
        dispatcher.isIdle.value shouldBe false

        secondRelease.complete(Unit)
        advanceUntilIdle()
        dispatcher.isIdle.value shouldBe true
    }

    @Test
    fun `cancelAllJobs followed by resetForNewSession does not strand awaitIdle`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val release = CompletableDeferred<Unit>()
        coEvery { module1.handle(any()) } coAnswers { release.await() }

        val dispatcher = createDispatcher()
        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        dispatcher.isIdle.value shouldBe false

        dispatcher.cancelAllJobs()
        advanceUntilIdle()

        dispatcher.isIdle.value shouldBe true

        dispatcher.resetForNewSession()

        var awaitIdleReturned = false
        val waiter = launch {
            dispatcher.awaitIdle()
            awaitIdleReturned = true
        }
        advanceUntilIdle()
        waiter.join()
        awaitIdleReturned shouldBe true

        // Release the deferred so the cancelled coroutine can finish unwinding cleanly.
        release.complete(Unit)
    }

    @Test
    fun `currentWorkGeneration starts at 0 and increments on dispatch`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val release = CompletableDeferred<Unit>()
        coEvery { module1.handle(any()) } coAnswers { release.await() }

        val dispatcher = createDispatcher()
        dispatcher.currentWorkGeneration() shouldBe 0L

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        // At least one trackJob ran (the cancellable launch).
        (dispatcher.currentWorkGeneration() > 0L) shouldBe true

        release.complete(Unit)
    }

    @Test
    fun `currentWorkGeneration keeps increasing across multiple dispatches`() = runTest {
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        val firstRelease = CompletableDeferred<Unit>()
        val secondRelease = CompletableDeferred<Unit>()
        var callCount = 0
        coEvery { module1.handle(any()) } coAnswers {
            val n = ++callCount
            if (n == 1) firstRelease.await() else secondRelease.await()
        }

        val dispatcher = createDispatcher()

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()
        val genAfterFirst = dispatcher.currentWorkGeneration()
        (genAfterFirst > 0L) shouldBe true

        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        advanceUntilIdle()
        val genAfterSecond = dispatcher.currentWorkGeneration()
        (genAfterSecond > genAfterFirst) shouldBe true

        firstRelease.complete(Unit)
        secondRelease.complete(Unit)
    }

    @Test
    fun `currentWorkGeneration increments even when a fast dispatch finishes before isIdle is observed`() = runTest {
        // Verifies that the orchestrator's "did anything happen during grace?" check
        // remains accurate even for blink-and-you-miss-it dispatches whose isIdle=false
        // gets conflated by StateFlow before any collector observes it.
        val buds = managedDevice(budsAddress, connected = true)
        devicesFlow.value = listOf(buds)

        // Module returns immediately — total in-flight time is microseconds.
        coEvery { module1.handle(any()) } returns Unit

        val dispatcher = createDispatcher()
        val genBefore = dispatcher.currentWorkGeneration()

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        dispatcher.isIdle.value shouldBe true
        (dispatcher.currentWorkGeneration() > genBefore) shouldBe true
    }

    // region SettlePolicy barrier

    @Test
    fun `settled modules wait actionDelay barrier once, not once per module`() = runTest {
        val testScope = this
        val buds = managedDevice(budsAddress, connected = true, actionDelay = java.time.Duration.ofSeconds(6))
        devicesFlow.value = listOf(buds)

        val handleTimes = mutableListOf<Long>()
        val m1 = mockConnectionModule(name = "M1", priority = 5)
        val m2 = mockConnectionModule(name = "M2", priority = 10)
        val m3 = mockConnectionModule(name = "M3", priority = 25)
        coEvery { m1.handle(any()) } coAnswers { handleTimes += testScope.currentTime }
        coEvery { m2.handle(any()) } coAnswers { handleTimes += testScope.currentTime }
        coEvery { m3.handle(any()) } coAnswers { handleTimes += testScope.currentTime }

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(m1, m2, m3),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        // All three modules ran after exactly one 6-second barrier — not three stacked 6s.
        handleTimes.size shouldBe 3
        handleTimes.all { it >= 6000 } shouldBe true
        handleTimes.all { it < 12000 } shouldBe true // would be >= 12s with even one extra barrier
    }

    @Test
    fun `immediate modules run before the settle barrier`() = runTest {
        val testScope = this
        val buds = managedDevice(budsAddress, connected = true, actionDelay = java.time.Duration.ofSeconds(6))
        devicesFlow.value = listOf(buds)

        val immediateTime = CompletableDeferred<Long>()
        val settledTime = CompletableDeferred<Long>()
        val immediate = mockConnectionModule(name = "Immediate", priority = 3)
        every { immediate.settlePolicy(any()) } returns eu.darken.bluemusic.monitor.core.modules.SettlePolicy.Immediate
        val settled = mockConnectionModule(name = "Settled", priority = 5)
        coEvery { immediate.handle(any()) } coAnswers { immediateTime.complete(testScope.currentTime) }
        coEvery { settled.handle(any()) } coAnswers { settledTime.complete(testScope.currentTime) }

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(immediate, settled),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        immediateTime.await() shouldBe 0L
        settledTime.await() shouldBe 6000L
    }

    @Test
    fun `AfterDeviceSettlePlus waits the extra delay after the barrier`() = runTest {
        val testScope = this
        val buds = managedDevice(budsAddress, connected = true, actionDelay = java.time.Duration.ofSeconds(4))
        devicesFlow.value = listOf(buds)

        val plusTime = CompletableDeferred<Long>()
        val plus = mockConnectionModule(name = "Plus", priority = 5)
        every { plus.settlePolicy(any()) } returns
            eu.darken.bluemusic.monitor.core.modules.SettlePolicy.AfterDeviceSettlePlus(java.time.Duration.ofSeconds(2))
        coEvery { plus.handle(any()) } coAnswers { plusTime.complete(testScope.currentTime) }

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(plus),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        // 4s barrier + 2s extra = 6s before handle runs.
        plusTime.await() shouldBe 6000L
    }

    @Test
    fun `modules with appliesTo=false are filtered out before barrier`() = runTest {
        val buds = managedDevice(budsAddress, connected = true, actionDelay = java.time.Duration.ofSeconds(6))
        devicesFlow.value = listOf(buds)

        val skipped = mockConnectionModule(name = "Skipped", priority = 5, appliesTo = false)
        val runs = mockConnectionModule(name = "Runs", priority = 5, appliesTo = true)
        coEvery { runs.handle(any()) } returns Unit

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(skipped, runs),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 0) { skipped.handle(any()) }
        coVerify(exactly = 1) { runs.handle(any()) }
    }

    @Test
    fun `only immediate modules apply - no barrier paid`() = runTest {
        val testScope = this
        val buds = managedDevice(budsAddress, connected = true, actionDelay = java.time.Duration.ofSeconds(6))
        devicesFlow.value = listOf(buds)

        val handleTime = CompletableDeferred<Long>()
        val immediate = mockConnectionModule(name = "Immediate", priority = 3)
        every { immediate.settlePolicy(any()) } returns eu.darken.bluemusic.monitor.core.modules.SettlePolicy.Immediate
        coEvery { immediate.handle(any()) } coAnswers { handleTime.complete(testScope.currentTime) }

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(immediate),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        // No settled modules → no barrier. Immediate runs at T=0.
        handleTime.await() shouldBe 0L
    }

    @Test
    fun `no modules apply - nothing runs, no barrier`() = runTest {
        val buds = managedDevice(budsAddress, connected = true, actionDelay = java.time.Duration.ofSeconds(6))
        devicesFlow.value = listOf(buds)

        val m = mockConnectionModule(name = "M", priority = 5, appliesTo = false)

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(m),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 0) { m.handle(any()) }
        // currentTime advanced only by trivial things (event delivery), not by a 6s barrier.
        (currentTime < 6000) shouldBe true
    }

    @Test
    fun `autoplay-like priority runs strictly after volume-like priority`() = runTest {
        // Safety invariant: a media-key dispatch must not start before the volume target
        // is set, otherwise the user can briefly hear playback at the OS-default level
        // (loud on headphones). Encoded as priority ordering, dispatcher must run lower
        // priority numbers first.
        val testScope = this
        val buds = managedDevice(budsAddress, connected = true, actionDelay = java.time.Duration.ofSeconds(0))
        devicesFlow.value = listOf(buds)

        val volumeFinishedAt = CompletableDeferred<Long>()
        val autoplayStartedAt = CompletableDeferred<Long>()
        val volumeLike = mockConnectionModule(name = "VolumeLike", priority = 10)
        val autoplayLike = mockConnectionModule(name = "AutoplayLike", priority = 20)
        coEvery { volumeLike.handle(any()) } coAnswers {
            // Simulate a volume ramp by virtually-sleeping 5s before completing.
            kotlinx.coroutines.delay(5000)
            volumeFinishedAt.complete(testScope.currentTime)
        }
        coEvery { autoplayLike.handle(any()) } coAnswers {
            autoplayStartedAt.complete(testScope.currentTime)
        }

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(volumeLike, autoplayLike),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        // Autoplay must NOT start before volume has finished.
        (autoplayStartedAt.await() >= volumeFinishedAt.await()) shouldBe true
    }

    @Test
    fun `supersede during settle barrier cancels handle`() = runTest {
        // A superseding dispatch for the same address cancels the in-flight cancellable
        // launch. Pre-PR4 the test relied on per-module delays inside handle — now the
        // barrier lives in the dispatcher, so the supersede must cancel the barrier delay
        // before the module's handle is ever invoked.
        val buds = managedDevice(budsAddress, connected = true, actionDelay = java.time.Duration.ofSeconds(6))
        devicesFlow.value = listOf(buds)

        val m = mockConnectionModule(name = "M", priority = 5)
        coEvery { m.handle(any()) } returns Unit

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(m),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        // No time advance — first dispatch is suspended in the 6s barrier
        dispatcher.dispatch(event(budsAddress, DISCONNECTED))
        advanceUntilIdle()

        // The CONNECTED handle never runs (cancelled mid-barrier).
        // The DISCONNECTED handle runs after its own barrier.
        coVerify(exactly = 0) { m.handle(any<DeviceEvent.Connected>()) }
        coVerify(exactly = 1) { m.handle(any<DeviceEvent.Disconnected>()) }
    }

    @Test
    fun `appliesTo throwing does not abort other modules`() = runTest {
        val buds = managedDevice(budsAddress, connected = true, actionDelay = java.time.Duration.ofSeconds(0))
        devicesFlow.value = listOf(buds)

        val thrower = mockConnectionModule(name = "Thrower", priority = 5)
        every { thrower.appliesTo(any()) } throws RuntimeException("boom")
        val survivor = mockConnectionModule(name = "Survivor", priority = 5)
        coEvery { survivor.handle(any()) } returns Unit

        val dispatcher = EventDispatcher(
            appScope = this,
            dispatcherProvider = asDispatcherProvider(),
            deviceRepo = deviceRepo,
            devicesSettings = devicesSettings,
            connectionModuleMap = setOf(thrower, survivor),
            eventTypeDedupTracker = tracker,
            ownerRegistry = AudioStreamOwnerRegistry(),
        )

        dispatcher.dispatch(event(budsAddress, CONNECTED))
        advanceUntilIdle()

        coVerify(exactly = 0) { thrower.handle(any()) }
        coVerify(exactly = 1) { survivor.handle(any()) }
    }

    // endregion
}
