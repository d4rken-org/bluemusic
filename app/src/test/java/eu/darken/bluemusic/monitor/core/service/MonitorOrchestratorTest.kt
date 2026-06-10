package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.audio.RingerModeEvent
import eu.darken.bluemusic.monitor.core.audio.RingerModeObserver
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

class MonitorOrchestratorTest : BaseTest() {

    private lateinit var bluetoothRepo: BluetoothRepo
    private lateinit var deviceRepo: DeviceRepo
    private lateinit var volumeObserver: VolumeObserver
    private lateinit var ringerModeObserver: RingerModeObserver
    private lateinit var bluetoothEventQueue: BluetoothEventQueue
    private lateinit var eventDispatcher: EventDispatcher
    private lateinit var ringerModeTransitionHandler: RingerModeTransitionHandler
    private lateinit var ownerRegistry: AudioStreamOwnerRegistry
    private lateinit var volumeEventDispatcher: VolumeEventDispatcher

    private lateinit var devicesSettings: DevicesSettings
    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>
    private lateinit var stateFlow: MutableStateFlow<BluetoothRepo.State>
    private lateinit var enabledFlow: MutableStateFlow<DevicesSettings.EnabledState>
    private lateinit var idleFlow: MutableStateFlow<Boolean>
    private lateinit var workGen: AtomicLong

    @BeforeEach
    fun setup() {
        stateFlow = MutableStateFlow(
            BluetoothRepo.State(isEnabled = true, hasPermission = true, devices = emptySet())
        )
        bluetoothRepo = mockk { every { state } returns stateFlow }

        enabledFlow = MutableStateFlow(DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 0L))
        devicesSettings = mockk {
            every { enabledState } returns enabledFlow
            coEvery { currentEnabledState() } answers { enabledFlow.value }
        }

        devicesFlow = MutableStateFlow(emptyList())
        deviceRepo = mockk(relaxed = true) {
            every { devices } returns devicesFlow
        }

        volumeObserver = mockk { every { volumes } returns MutableSharedFlow() }
        ringerModeObserver = mockk { every { ringerMode } returns MutableSharedFlow() }
        bluetoothEventQueue = mockk {
            every { events } returns MutableSharedFlow()
            every { clear() } just Runs
        }
        idleFlow = MutableStateFlow(true)
        workGen = AtomicLong(0)
        eventDispatcher = mockk(relaxed = true) {
            every { isIdle } returns idleFlow
            coEvery { awaitIdle() } coAnswers { idleFlow.filter { it }.first() }
            every { currentWorkGeneration() } answers { workGen.get() }
        }
        ringerModeTransitionHandler = mockk(relaxed = true)
        ownerRegistry = mockk(relaxed = true)
        volumeEventDispatcher = mockk(relaxed = true)
    }

    private fun createOrchestrator() = MonitorOrchestrator(
        bluetoothRepo = bluetoothRepo,
        deviceRepo = deviceRepo,
        devicesSettings = devicesSettings,
        volumeObserver = volumeObserver,
        ringerModeObserver = ringerModeObserver,
        bluetoothEventQueue = bluetoothEventQueue,
        eventDispatcher = eventDispatcher,
        ringerModeTransitionHandler = ringerModeTransitionHandler,
        ownerRegistry = ownerRegistry,
        volumeEventDispatcher = volumeEventDispatcher,
    )

    private fun managedDevice(
        address: String,
        active: Boolean = true,
        requiresPersistentSession: Boolean = false,
        monitoringDuration: Duration = Duration.ZERO,
    ): ManagedDevice = mockk(relaxed = true) {
        every { this@mockk.address } returns address
        every { isActive } returns active
        every { this@mockk.requiresPersistentSession } returns requiresPersistentSession
        every { this@mockk.monitoringDuration } returns monitoringDuration
        every { label } returns address
    }

    // --- Bootstrap ---

    @Test
    fun `bluetooth not ready - returns immediately`() = runTest {
        stateFlow.value = BluetoothRepo.State(isEnabled = false, hasPermission = true, devices = emptySet())

        val callbackInvocations = mutableListOf<List<ManagedDevice>>()
        val orchestrator = createOrchestrator()

        orchestrator.monitor(this) { callbackInvocations.add(it) }
        advanceUntilIdle()

        callbackInvocations shouldBe emptyList()
        coVerify(exactly = 0) { ownerRegistry.reset() }
    }

    @Test
    fun `bootstrap uses single device snapshot for reset, bootstrap, and callback`() = runTest {
        val device = managedDevice("AA:BB:CC:DD:EE:FF", active = true)
        devicesFlow.value = listOf(device)

        val callbackInvocations = mutableListOf<List<ManagedDevice>>()
        val orchestrator = createOrchestrator()

        orchestrator.monitor(this) { callbackInvocations.add(it) }
        advanceUntilIdle()

        coVerify(exactly = 1) { ownerRegistry.reset() }
        coVerify(exactly = 1) { ownerRegistry.bootstrap(listOf(device)) }
        callbackInvocations.first() shouldBe listOf(device)
    }

    // --- Shutdown heuristics ---

    @Test
    fun `requiresPersistentSession devices keep monitoring alive`() = runTest {
        val device = managedDevice("AA:BB:CC:DD:EE:FF", active = true, requiresPersistentSession = true)
        devicesFlow.value = listOf(device)

        val orchestrator = createOrchestrator()
        var monitorReturned = false

        val job = launch {
            orchestrator.monitor(this@runTest) {}
            monitorReturned = true
        }

        advanceTimeBy(60_000)
        monitorReturned shouldBe false

        job.cancel()
    }

    @Test
    fun `active devices without requiresPersistentSession - stops after idle grace period`() = runTest {
        val device = managedDevice(
            "AA:BB:CC:DD:EE:FF",
            active = true,
            requiresPersistentSession = false,
        )
        devicesFlow.value = listOf(device)

        val orchestrator = createOrchestrator()
        var monitorReturned = false

        val job = launch {
            orchestrator.monitor(this@runTest) {}
            monitorReturned = true
        }

        // Dispatcher is idle from the start. 15s grace before stopping.
        advanceTimeBy(14_000)
        monitorReturned shouldBe false

        advanceTimeBy(2_000)
        advanceUntilIdle()
        monitorReturned shouldBe true

        job.cancel()
    }

    @Test
    fun `slow handler in flight - kill timer does not fire until awaitIdle resolves`() = runTest {
        idleFlow.value = false // Dispatcher busy from start
        val device = managedDevice(
            "AA:BB:CC:DD:EE:FF",
            active = true,
            requiresPersistentSession = false,
        )
        devicesFlow.value = listOf(device)

        val orchestrator = createOrchestrator()
        var monitorReturned = false

        val job = launch {
            orchestrator.monitor(this@runTest) {}
            monitorReturned = true
        }

        // 30s in, dispatcher still busy → must not return
        advanceTimeBy(30_000)
        monitorReturned shouldBe false

        // Dispatcher becomes idle
        idleFlow.value = true
        advanceTimeBy(14_000)
        monitorReturned shouldBe false

        advanceTimeBy(2_000)
        advanceUntilIdle()
        monitorReturned shouldBe true

        job.cancel()
    }

    @Test
    fun `dispatcher going busy during 15s grace restarts cooldown`() = runTest {
        val device = managedDevice(
            "AA:BB:CC:DD:EE:FF",
            active = true,
            requiresPersistentSession = false,
        )
        devicesFlow.value = listOf(device)

        val orchestrator = createOrchestrator()
        var monitorReturned = false

        val job = launch {
            orchestrator.monitor(this@runTest) {}
            monitorReturned = true
        }

        // Idle from start; advance 10s into the first grace window.
        advanceTimeBy(10_000)
        monitorReturned shouldBe false

        // Mid-grace: dispatcher gets new work — bump generation AND flip idle to false
        // to simulate what trackJob() does in production. The cooldown checks generation
        // (not isIdle) at the end of the 15s delay, so this *will* be detected.
        workGen.incrementAndGet()
        idleFlow.value = false

        // Finish out the original 15s delay — orchestrator sees generation changed, restarts.
        advanceTimeBy(5_001)
        monitorReturned shouldBe false

        // Restart loop calls awaitIdle() which suspends because dispatcher is busy.
        advanceTimeBy(20_000)
        monitorReturned shouldBe false

        // Dispatcher becomes idle again — awaitIdle() returns, new 15s grace begins.
        idleFlow.value = true
        advanceTimeBy(14_000)
        monitorReturned shouldBe false

        advanceTimeBy(2_000)
        advanceUntilIdle()
        monitorReturned shouldBe true

        job.cancel()
    }

    @Test
    fun `work bump alone during grace restarts cooldown even without idle flip`() = runTest {
        // Edge case: a fast dispatch that completes within the 15s grace window —
        // its trackJob bumps the generation but isIdle may already be true again
        // by the time the cooldown's delay completes. Generation-based check still detects it.
        val device = managedDevice(
            "AA:BB:CC:DD:EE:FF",
            active = true,
            requiresPersistentSession = false,
        )
        devicesFlow.value = listOf(device)

        val orchestrator = createOrchestrator()
        var monitorReturned = false

        val job = launch {
            orchestrator.monitor(this@runTest) {}
            monitorReturned = true
        }

        // Mid-grace, simulate a brief dispatch that bumps generation but leaves idle=true.
        advanceTimeBy(10_000)
        workGen.incrementAndGet()

        // Finish out the original 15s delay — generation differs → restart.
        advanceTimeBy(5_001)
        monitorReturned shouldBe false

        // Second grace window with no further work.
        advanceTimeBy(14_000)
        monitorReturned shouldBe false

        advanceTimeBy(2_000)
        advanceUntilIdle()
        monitorReturned shouldBe true

        job.cancel()
    }

    @Test
    fun `no devices connected - stops after 15s`() = runTest {
        devicesFlow.value = emptyList()

        val orchestrator = createOrchestrator()
        var monitorReturned = false

        val job = launch {
            orchestrator.monitor(this@runTest) {}
            monitorReturned = true
        }

        // 15s grace (throttleLatest emits first value immediately)
        advanceTimeBy(14_000)
        monitorReturned shouldBe false

        advanceTimeBy(2_000)
        advanceUntilIdle()
        monitorReturned shouldBe true

        job.cancel()
    }

    @Test
    fun `device list changes mid-monitoring fires callback with updated list`() = runTest {
        val device1 = managedDevice("AA:BB:CC:DD:EE:FF", active = true, requiresPersistentSession = true)
        devicesFlow.value = listOf(device1)

        val callbackInvocations = mutableListOf<List<ManagedDevice>>()
        val orchestrator = createOrchestrator()

        val job = launch {
            orchestrator.monitor(this@runTest) { callbackInvocations.add(it) }
        }

        advanceTimeBy(5_000)

        val device2 = managedDevice("11:22:33:44:55:66", active = true, requiresPersistentSession = true)
        devicesFlow.value = listOf(device1, device2)
        advanceTimeBy(5_000)

        // Initial callback + at least one update
        (callbackInvocations.size >= 2) shouldBe true

        job.cancel()
    }

    // --- App enabled gating ---

    @Test
    fun `disabled at start - returns immediately and clears queued events`() = runTest {
        enabledFlow.value = DevicesSettings.EnabledState(isEnabled = false, toggleEpoch = 1L)
        val device = managedDevice("AA:BB:CC:DD:EE:FF", active = true, requiresPersistentSession = true)
        devicesFlow.value = listOf(device)

        val callbackInvocations = mutableListOf<List<ManagedDevice>>()
        val orchestrator = createOrchestrator()

        orchestrator.monitor(this) { callbackInvocations.add(it) }
        advanceUntilIdle()

        callbackInvocations shouldBe emptyList()
        coVerify(exactly = 0) { ownerRegistry.reset() }
        verify(exactly = 1) { bluetoothEventQueue.clear() }
    }

    @Test
    fun `disabling mid-monitoring stops even with persistent session device`() = runTest {
        val device = managedDevice("AA:BB:CC:DD:EE:FF", active = true, requiresPersistentSession = true)
        devicesFlow.value = listOf(device)

        val orchestrator = createOrchestrator()
        var monitorReturned = false

        val job = launch {
            orchestrator.monitor(this@runTest) {}
            monitorReturned = true
        }

        advanceTimeBy(30_000)
        monitorReturned shouldBe false

        enabledFlow.value = DevicesSettings.EnabledState(isEnabled = false, toggleEpoch = 1L)
        advanceUntilIdle()

        monitorReturned shouldBe true
        verify(atLeast = 1) { eventDispatcher.cancelAllJobs() }
        verify(atLeast = 1) { bluetoothEventQueue.clear() }

        job.cancel()
    }

    @Test
    fun `collapsed toggle cycle (epoch change while enabled) stops the session`() = runTest {
        val device = managedDevice("AA:BB:CC:DD:EE:FF", active = true, requiresPersistentSession = true)
        devicesFlow.value = listOf(device)

        val orchestrator = createOrchestrator()
        var monitorReturned = false

        val job = launch {
            orchestrator.monitor(this@runTest) {}
            monitorReturned = true
        }

        advanceTimeBy(5_000)
        monitorReturned shouldBe false

        // e.g. backup restore flips enabled off and on; observer only sees the final true
        enabledFlow.value = DevicesSettings.EnabledState(isEnabled = true, toggleEpoch = 2L)
        advanceUntilIdle()

        monitorReturned shouldBe true

        job.cancel()
    }

    // --- Failure isolation ---

    @Test
    fun `ringerMode handler crash does not break device monitoring`() = runTest {
        val ringerFlow = MutableSharedFlow<RingerModeEvent>()
        ringerModeObserver = mockk { every { ringerMode } returns ringerFlow }

        coEvery { ringerModeTransitionHandler.handle(any()) } throws RuntimeException("boom")

        val device = managedDevice("AA:BB:CC:DD:EE:FF", active = true, requiresPersistentSession = true)
        devicesFlow.value = listOf(device)

        val callbackInvocations = mutableListOf<List<ManagedDevice>>()
        val orchestrator = createOrchestrator()

        val job = launch {
            orchestrator.monitor(this@runTest) { callbackInvocations.add(it) }
        }

        advanceTimeBy(1_000)
        ringerFlow.emit(mockk(relaxed = true))
        advanceTimeBy(5_000)

        // Device monitoring still worked (initial callback fired)
        callbackInvocations.isNotEmpty() shouldBe true

        job.cancel()
    }

    @Test
    fun `volume dispatcher crash does not break device monitoring`() = runTest {
        val volumeFlow = MutableSharedFlow<VolumeEvent>()
        volumeObserver = mockk { every { volumes } returns volumeFlow }

        coEvery { volumeEventDispatcher.dispatch(any()) } throws RuntimeException("boom")

        val device = managedDevice("AA:BB:CC:DD:EE:FF", active = true, requiresPersistentSession = true)
        devicesFlow.value = listOf(device)

        val callbackInvocations = mutableListOf<List<ManagedDevice>>()
        val orchestrator = createOrchestrator()

        val job = launch {
            orchestrator.monitor(this@runTest) { callbackInvocations.add(it) }
        }

        advanceTimeBy(1_000)
        volumeFlow.emit(mockk(relaxed = true))
        advanceTimeBy(5_000)

        callbackInvocations.isNotEmpty() shouldBe true

        job.cancel()
    }

    @Test
    fun `bluetooth event dispatcher crash does not break device monitoring`() = runTest {
        val btEventFlow = MutableSharedFlow<BluetoothEventQueue.Event>()
        bluetoothEventQueue = mockk { every { events } returns btEventFlow }

        coEvery { eventDispatcher.dispatch(any()) } throws RuntimeException("boom")

        val device = managedDevice("AA:BB:CC:DD:EE:FF", active = true, requiresPersistentSession = true)
        devicesFlow.value = listOf(device)

        val callbackInvocations = mutableListOf<List<ManagedDevice>>()
        val orchestrator = createOrchestrator()

        val job = launch {
            orchestrator.monitor(this@runTest) { callbackInvocations.add(it) }
        }

        advanceTimeBy(1_000)
        btEventFlow.emit(mockk(relaxed = true))
        advanceTimeBy(5_000)

        callbackInvocations.isNotEmpty() shouldBe true

        job.cancel()
    }
}
