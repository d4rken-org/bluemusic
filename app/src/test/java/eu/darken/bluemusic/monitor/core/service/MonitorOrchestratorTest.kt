package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.audio.RingerModeEvent
import eu.darken.bluemusic.monitor.core.audio.RingerModeObserver
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration

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

    private lateinit var devicesFlow: MutableStateFlow<List<ManagedDevice>>
    private lateinit var stateFlow: MutableStateFlow<BluetoothRepo.State>

    @BeforeEach
    fun setup() {
        stateFlow = MutableStateFlow(
            BluetoothRepo.State(isEnabled = true, hasPermission = true, devices = emptySet())
        )
        bluetoothRepo = mockk { every { state } returns stateFlow }

        devicesFlow = MutableStateFlow(emptyList())
        deviceRepo = mockk(relaxed = true) {
            every { devices } returns devicesFlow
        }

        volumeObserver = mockk { every { volumes } returns MutableSharedFlow() }
        ringerModeObserver = mockk { every { ringerMode } returns MutableSharedFlow() }
        bluetoothEventQueue = mockk { every { events } returns MutableSharedFlow() }
        eventDispatcher = mockk(relaxed = true)
        ringerModeTransitionHandler = mockk(relaxed = true)
        ownerRegistry = mockk(relaxed = true)
        volumeEventDispatcher = mockk(relaxed = true)
    }

    private fun createOrchestrator() = MonitorOrchestrator(
        bluetoothRepo = bluetoothRepo,
        deviceRepo = deviceRepo,
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
        requiresMonitor: Boolean = false,
        monitoringDuration: Duration = Duration.ZERO,
    ): ManagedDevice = mockk(relaxed = true) {
        every { this@mockk.address } returns address
        every { isActive } returns active
        every { this@mockk.requiresMonitor } returns requiresMonitor
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
    fun `requiresMonitor devices keep monitoring alive`() = runTest {
        val device = managedDevice("AA:BB:CC:DD:EE:FF", active = true, requiresMonitor = true)
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
    fun `active devices without requiresMonitor - stops after grace period`() = runTest {
        val monitoringDuration = Duration.ofSeconds(5)
        val device = managedDevice(
            "AA:BB:CC:DD:EE:FF",
            active = true,
            requiresMonitor = false,
            monitoringDuration = monitoringDuration,
        )
        devicesFlow.value = listOf(device)

        val orchestrator = createOrchestrator()
        var monitorReturned = false

        val job = launch {
            orchestrator.monitor(this@runTest) {}
            monitorReturned = true
        }

        // 15s grace + 5s monitoring = 20s (throttleLatest emits first value immediately)
        advanceTimeBy(19_000)
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
        val device1 = managedDevice("AA:BB:CC:DD:EE:FF", active = true, requiresMonitor = true)
        devicesFlow.value = listOf(device1)

        val callbackInvocations = mutableListOf<List<ManagedDevice>>()
        val orchestrator = createOrchestrator()

        val job = launch {
            orchestrator.monitor(this@runTest) { callbackInvocations.add(it) }
        }

        advanceTimeBy(5_000)

        val device2 = managedDevice("11:22:33:44:55:66", active = true, requiresMonitor = true)
        devicesFlow.value = listOf(device1, device2)
        advanceTimeBy(5_000)

        // Initial callback + at least one update
        (callbackInvocations.size >= 2) shouldBe true

        job.cancel()
    }

    // --- Failure isolation ---

    @Test
    fun `ringerMode handler crash does not break device monitoring`() = runTest {
        val ringerFlow = MutableSharedFlow<RingerModeEvent>()
        ringerModeObserver = mockk { every { ringerMode } returns ringerFlow }

        coEvery { ringerModeTransitionHandler.handle(any()) } throws RuntimeException("boom")

        val device = managedDevice("AA:BB:CC:DD:EE:FF", active = true, requiresMonitor = true)
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

        val device = managedDevice("AA:BB:CC:DD:EE:FF", active = true, requiresMonitor = true)
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

        val device = managedDevice("AA:BB:CC:DD:EE:FF", active = true, requiresMonitor = true)
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
