package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeObservationGate
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class BaseVolumeModuleTest : BaseTest() {

    private val streamId = AudioStream.Id.STREAM_MUSIC
    private val maxLevel = 15
    private val targetPercentage = 0.44f
    private val targetLevel = 7 // (0.44 * 15).roundToInt()

    private lateinit var volumeTool: VolumeTool
    private lateinit var volumeEvents: MutableSharedFlow<VolumeEvent>
    private lateinit var volumeObserver: VolumeObserver
    private lateinit var observationGate: VolumeObservationGate
    private lateinit var device: ManagedDevice
    private lateinit var module: TestVolumeModule

    private class TestVolumeModule(
        volumeTool: VolumeTool,
        volumeObserver: VolumeObserver,
        observationGate: VolumeObservationGate,
        ownerRegistry: AudioStreamOwnerRegistry,
        deviceRepo: DeviceRepo,
    ) : BaseVolumeModule(volumeTool, volumeObserver, observationGate, ownerRegistry, deviceRepo) {
        override val type = AudioStream.Type.MUSIC
        override val priority = 10

        suspend fun callMonitor(device: ManagedDevice, volumeMode: VolumeMode) {
            monitor(device, volumeMode)
        }
    }

    @BeforeEach
    fun setup() {
        volumeTool = mockk(relaxed = true)
        volumeEvents = MutableSharedFlow()
        volumeObserver = mockk<VolumeObserver>().also {
            every { it.volumes } returns volumeEvents
        }
        observationGate = VolumeObservationGate()
        device = mockk(relaxed = true)
        module = TestVolumeModule(
            volumeTool, volumeObserver, observationGate,
            AudioStreamOwnerRegistry(),
            mockk(relaxed = true),
        )

        every { device.getStreamId(AudioStream.Type.MUSIC) } returns streamId
        every { device.monitoringDuration } returns Duration.ofSeconds(4)
        every { volumeTool.getMaxVolume(streamId) } returns maxLevel
    }

    // --- Observer-driven monitor loop ---

    @Test
    fun `monitor completes on timeout when no events arrive`() = runTest(UnconfinedTestDispatcher()) {
        every { volumeTool.wasUs(streamId, any()) } returns true

        val job = launch { module.callMonitor(device, VolumeMode.Normal(targetPercentage)) }

        advanceTimeBy(4_001)
        job.join()

        // No volume events emitted → no re-enforcement calls
        coVerify(exactly = 0) { volumeTool.changeVolume(streamId, any<Float>()) }
    }

    @Test
    fun `monitor re-enforces when external platform write changes the level`() = runTest(UnconfinedTestDispatcher()) {
        every { volumeTool.wasUs(streamId, targetLevel) } returns true
        coEvery { volumeTool.changeVolume(streamId, targetPercentage) } returns true

        val job = launch { module.callMonitor(device, VolumeMode.Normal(targetPercentage)) }

        // Simulate Android route-transition resetting the volume to 0
        volumeEvents.emit(VolumeEvent(streamId, targetLevel, 0, self = false))

        advanceTimeBy(4_001)
        job.join()

        coVerify(atLeast = 1) { volumeTool.changeVolume(streamId, targetPercentage) }
    }

    @Test
    fun `monitor ignores events where our write landed at target`() = runTest(UnconfinedTestDispatcher()) {
        every { volumeTool.wasUs(streamId, targetLevel) } returns true

        val job = launch { module.callMonitor(device, VolumeMode.Normal(targetPercentage)) }

        // Our re-enforcement write completed — newVolume matches target
        volumeEvents.emit(VolumeEvent(streamId, 0, targetLevel, self = false))

        advanceTimeBy(4_001)
        job.join()

        // Should not try to re-enforce when we're already at target
        coVerify(exactly = 0) { volumeTool.changeVolume(streamId, any<Float>()) }
    }

    @Test
    fun `monitor yields when another VolumeTool caller writes a different level`() = runTest(UnconfinedTestDispatcher()) {
        // wasUs returns false = another BVM path (user slider) wrote via VolumeTool
        every { volumeTool.wasUs(streamId, targetLevel) } returns false

        val job = launch { module.callMonitor(device, VolumeMode.Normal(targetPercentage)) }

        // External write arrives — but wasUs says we're not in control anymore
        volumeEvents.emit(VolumeEvent(streamId, targetLevel, 10, self = false))

        // The monitor should exit before the timeout
        job.join()

        // Should NOT re-enforce — yield to the external writer
        coVerify(exactly = 0) { volumeTool.changeVolume(streamId, any<Float>()) }
    }

    @Test
    fun `monitor returns immediately for non-Normal volumeMode`() = runTest(UnconfinedTestDispatcher()) {
        module.callMonitor(device, VolumeMode.Silent)

        verify(exactly = 0) { volumeTool.wasUs(any(), any()) }
        coVerify(exactly = 0) { volumeTool.changeVolume(any(), any<Float>()) }
    }

    @Test
    fun `monitor ignores events for other streams`() = runTest(UnconfinedTestDispatcher()) {
        every { volumeTool.wasUs(streamId, targetLevel) } returns true

        val job = launch { module.callMonitor(device, VolumeMode.Normal(targetPercentage)) }

        // Event for a different stream
        volumeEvents.emit(VolumeEvent(AudioStream.Id.STREAM_ALARM, 5, 0, self = false))

        advanceTimeBy(4_001)
        job.join()

        // Should not react to ALARM events when monitoring MUSIC
        coVerify(exactly = 0) { volumeTool.changeVolume(any(), any<Float>()) }
    }

    // --- handle() integration ---

    @Test
    fun `handle ignores disconnected events`() = runTest {
        val event = DeviceEvent.Disconnected(device)
        module.handle(event)

        coVerify(exactly = 0) { volumeTool.changeVolume(any(), any<Float>()) }
    }

    @Test
    fun `handle returns early for unconfigured stream`() = runTest {
        every { device.getVolume(AudioStream.Type.MUSIC) } returns null
        every { device.actionDelay } returns Duration.ZERO

        val event = DeviceEvent.Connected(device)
        module.handle(event)

        coVerify(exactly = 0) { volumeTool.changeVolume(any(), any<Float>()) }
    }

    // --- Ownership generation + device re-resolve tests ---

    private val testAddress = "AA:BB:CC:DD:EE:FF"

    private val testSourceDevice = SourceDeviceWrapper(
        address = testAddress,
        alias = "TestDevice",
        name = "TestDevice",
        deviceType = SourceDevice.Type.HEADPHONES,
        isConnected = true,
    )

    private fun realDevice(
        musicVolume: Float? = 0.44f,
        actionDelayMs: Long = 2000L,
        monitoringDurationMs: Long = 4000L,
    ): ManagedDevice = ManagedDevice(
        isConnected = true,
        device = testSourceDevice,
        config = DeviceConfigEntity(
            address = testAddress,
            musicVolume = musicVolume,
            actionDelay = actionDelayMs,
            monitoringDuration = monitoringDurationMs,
            isEnabled = true,
        ),
    )

    private fun createModuleWithDeps(
        registry: AudioStreamOwnerRegistry = AudioStreamOwnerRegistry(),
        devicesFlow: MutableStateFlow<List<ManagedDevice>> = MutableStateFlow(emptyList()),
    ): Pair<TestVolumeModule, AudioStreamOwnerRegistry> {
        val deviceRepo = mockk<DeviceRepo>(relaxed = true)
        every { deviceRepo.devices } returns devicesFlow
        val mod = TestVolumeModule(volumeTool, volumeObserver, observationGate, registry, deviceRepo)
        return mod to registry
    }

    @Nested
    inner class OwnershipGeneration {
        @Test
        fun `generation changes during actionDelay - yields before setInitial`() = runTest(UnconfinedTestDispatcher()) {
            val devicesFlow = MutableStateFlow<List<ManagedDevice>>(emptyList())
            val registry = AudioStreamOwnerRegistry()
            val (mod, _) = createModuleWithDeps(registry, devicesFlow)
            val dev = realDevice(actionDelayMs = 2000L)
            devicesFlow.value = listOf(dev)

            registry.onDeviceConnected(testAddress, "TestDevice", SourceDevice.Type.HEADPHONES, 1000L, 0L)

            every { volumeTool.getMaxVolume(streamId) } returns maxLevel

            val job = launch { mod.handle(DeviceEvent.Connected(dev)) }

            // During actionDelay, a new device takes ownership
            advanceTimeBy(1000)
            registry.onDeviceConnected("NEW:ADDR:00:00:00:01", "NewDevice", SourceDevice.Type.HEADPHONES, 5000L, 1L)

            advanceTimeBy(1500) // past the delay
            job.join()

            // setInitial should NOT have been called because generation changed
            coVerify(exactly = 0) { volumeTool.changeVolume(streamId, any<Float>()) }
        }

        @Test
        fun `generation stable during actionDelay - proceeds to setInitial`() = runTest(UnconfinedTestDispatcher()) {
            val devicesFlow = MutableStateFlow<List<ManagedDevice>>(emptyList())
            val registry = AudioStreamOwnerRegistry()
            val (mod, _) = createModuleWithDeps(registry, devicesFlow)
            val dev = realDevice(actionDelayMs = 1000L)
            devicesFlow.value = listOf(dev)

            registry.onDeviceConnected(testAddress, "TestDevice", SourceDevice.Type.HEADPHONES, 1000L, 0L)

            every { volumeTool.getMaxVolume(streamId) } returns maxLevel
            every { volumeTool.wasUs(streamId, any()) } returns true
            coEvery { volumeTool.changeVolume(streamId, any<Float>(), any(), any()) } returns true

            val job = launch { mod.handle(DeviceEvent.Connected(dev)) }

            advanceTimeBy(1500) // past actionDelay
            advanceTimeBy(5000) // past monitoring
            job.join()

            // setInitial SHOULD have been called
            coVerify(atLeast = 1) { volumeTool.changeVolume(streamId, any<Float>(), any(), any()) }
        }

        @Test
        fun `generation changes during monitor - yields early`() = runTest(UnconfinedTestDispatcher()) {
            val devicesFlow = MutableStateFlow<List<ManagedDevice>>(emptyList())
            val registry = AudioStreamOwnerRegistry()
            val (mod, _) = createModuleWithDeps(registry, devicesFlow)
            val dev = realDevice(actionDelayMs = 0L, monitoringDurationMs = 10000L)
            devicesFlow.value = listOf(dev)

            registry.onDeviceConnected(testAddress, "TestDevice", SourceDevice.Type.HEADPHONES, 1000L, 0L)

            every { volumeTool.getMaxVolume(streamId) } returns maxLevel
            every { volumeTool.wasUs(streamId, any()) } returns true
            coEvery { volumeTool.changeVolume(streamId, any<Float>(), any(), any()) } returns true

            val job = launch { mod.handle(DeviceEvent.Connected(dev)) }

            advanceTimeBy(100) // In monitor now

            // Change ownership
            registry.onDeviceConnected("NEW:ADDR:00:00:00:01", "NewDevice", SourceDevice.Type.HEADPHONES, 5000L, 1L)

            // Emit a volume event to trigger the collect loop — generation check happens here
            volumeEvents.emit(VolumeEvent(streamId, targetLevel, 0, self = false))

            advanceTimeBy(100)
            job.join()

            // Should NOT re-enforce because generation changed → yield
            coVerify(exactly = 0) { volumeTool.changeVolume(streamId, any<Float>()) }
        }

        @Test
        fun `generation check does not interfere with wasUs yield`() = runTest(UnconfinedTestDispatcher()) {
            // Generation stable but wasUs returns false → should still yield
            val devicesFlow = MutableStateFlow<List<ManagedDevice>>(emptyList())
            val registry = AudioStreamOwnerRegistry()
            val (mod, _) = createModuleWithDeps(registry, devicesFlow)
            val dev = realDevice(actionDelayMs = 0L, monitoringDurationMs = 10000L)
            devicesFlow.value = listOf(dev)

            registry.onDeviceConnected(testAddress, "TestDevice", SourceDevice.Type.HEADPHONES, 1000L, 0L)

            every { volumeTool.getMaxVolume(streamId) } returns maxLevel
            // wasUs true for initial check, false when monitor checks
            every { volumeTool.wasUs(streamId, targetLevel) } returns false
            coEvery { volumeTool.changeVolume(streamId, any<Float>(), any(), any()) } returns true

            val job = launch { mod.handle(DeviceEvent.Connected(dev)) }

            advanceTimeBy(100) // In monitor

            // Emit event — wasUs returns false → yield even though generation unchanged
            volumeEvents.emit(VolumeEvent(streamId, targetLevel, 10, self = false))

            job.join()

            // Should NOT re-enforce — wasUs yield
            coVerify(exactly = 0) { volumeTool.changeVolume(streamId, any<Float>()) }
        }
    }

    @Nested
    inner class ObservationGateLifecycle {
        @Test
        fun `observation gate unsuppressed on ownership yield`() = runTest(UnconfinedTestDispatcher()) {
            val devicesFlow = MutableStateFlow<List<ManagedDevice>>(emptyList())
            val registry = AudioStreamOwnerRegistry()
            val (mod, _) = createModuleWithDeps(registry, devicesFlow)
            val dev = realDevice(actionDelayMs = 1000L)
            devicesFlow.value = listOf(dev)

            registry.onDeviceConnected(testAddress, "TestDevice", SourceDevice.Type.HEADPHONES, 1000L, 0L)

            every { volumeTool.getMaxVolume(streamId) } returns maxLevel

            val job = launch { mod.handle(DeviceEvent.Connected(dev)) }

            // Stream should be suppressed during handle
            advanceTimeBy(500)
            observationGate.isSuppressed(streamId) shouldBe true

            // Change ownership to trigger yield
            registry.onDeviceConnected("NEW:ADDR:00:00:00:01", "NewDevice", SourceDevice.Type.HEADPHONES, 5000L, 1L)
            advanceTimeBy(1500)
            job.join()

            // After yield, gate must be unsuppressed (finally block)
            observationGate.isSuppressed(streamId) shouldBe false
        }

        @Test
        fun `two concurrent runs on same stream - gate stays suppressed until both finish`() = runTest(UnconfinedTestDispatcher()) {
            val devicesFlow = MutableStateFlow<List<ManagedDevice>>(emptyList())
            val registry = AudioStreamOwnerRegistry()
            val deviceRepo = mockk<DeviceRepo>(relaxed = true)
            every { deviceRepo.devices } returns devicesFlow
            // Two independent module instances sharing the same gate
            val mod1 = TestVolumeModule(volumeTool, volumeObserver, observationGate, registry, deviceRepo)
            val mod2 = TestVolumeModule(volumeTool, volumeObserver, observationGate, registry, deviceRepo)

            val dev = realDevice(actionDelayMs = 0L, monitoringDurationMs = 2000L)
            devicesFlow.value = listOf(dev)
            registry.onDeviceConnected(testAddress, "TestDevice", SourceDevice.Type.HEADPHONES, 1000L, 0L)

            every { volumeTool.getMaxVolume(streamId) } returns maxLevel
            every { volumeTool.wasUs(streamId, any()) } returns true
            coEvery { volumeTool.changeVolume(streamId, any<Float>(), any(), any()) } returns true

            // Both start monitoring
            val job1 = launch { mod1.handle(DeviceEvent.Connected(dev)) }
            val job2 = launch { mod2.handle(DeviceEvent.Connected(dev)) }

            advanceTimeBy(500) // Both in monitor, gate suppressed by both
            observationGate.isSuppressed(streamId) shouldBe true

            advanceTimeBy(2000) // Both finish
            job1.join()
            job2.join()

            // Only after both finish is the gate unsuppressed
            observationGate.isSuppressed(streamId) shouldBe false
        }
    }

    @Nested
    inner class DeviceReResolve {
        @Test
        fun `re-resolves device from DeviceRepo before setInitial`() = runTest(UnconfinedTestDispatcher()) {
            val devicesFlow = MutableStateFlow<List<ManagedDevice>>(emptyList())
            val registry = AudioStreamOwnerRegistry()
            val (mod, _) = createModuleWithDeps(registry, devicesFlow)

            val initialDev = realDevice(musicVolume = 0.44f, actionDelayMs = 1000L)
            val updatedDev = realDevice(musicVolume = 0.8f, actionDelayMs = 1000L)
            devicesFlow.value = listOf(initialDev)

            registry.onDeviceConnected(testAddress, "TestDevice", SourceDevice.Type.HEADPHONES, 1000L, 0L)

            every { volumeTool.getMaxVolume(streamId) } returns maxLevel
            every { volumeTool.wasUs(streamId, any()) } returns true
            coEvery { volumeTool.changeVolume(streamId, any<Float>(), any(), any()) } returns true

            val job = launch { mod.handle(DeviceEvent.Connected(initialDev)) }

            // During actionDelay, user changes target volume
            advanceTimeBy(500)
            devicesFlow.value = listOf(updatedDev)

            advanceTimeBy(1500) // Past delay
            advanceTimeBy(5000) // Past monitor
            job.join()

            // setInitial should use the re-resolved volume (0.8), not original (0.44)
            coVerify(atLeast = 1) { volumeTool.changeVolume(streamId, 0.8f, any(), any()) }
            coVerify(exactly = 0) { volumeTool.changeVolume(streamId, 0.44f, any(), any()) }
        }

        @Test
        fun `device deleted during actionDelay - yields`() = runTest(UnconfinedTestDispatcher()) {
            val devicesFlow = MutableStateFlow<List<ManagedDevice>>(emptyList())
            val registry = AudioStreamOwnerRegistry()
            val (mod, _) = createModuleWithDeps(registry, devicesFlow)

            val dev = realDevice(actionDelayMs = 1000L)
            devicesFlow.value = listOf(dev)
            registry.onDeviceConnected(testAddress, "TestDevice", SourceDevice.Type.HEADPHONES, 1000L, 0L)

            every { volumeTool.getMaxVolume(streamId) } returns maxLevel

            val job = launch { mod.handle(DeviceEvent.Connected(dev)) }

            // Device removed during actionDelay
            advanceTimeBy(500)
            devicesFlow.value = emptyList()

            advanceTimeBy(1500)
            job.join()

            // No setInitial (device no longer exists)
            coVerify(exactly = 0) { volumeTool.changeVolume(streamId, any<Float>()) }
            coVerify(exactly = 0) { volumeTool.changeVolume(streamId, any<Float>(), any(), any()) }
            // Gate unsuppressed
            observationGate.isSuppressed(streamId) shouldBe false
        }

        @Test
        fun `volume unconfigured after delay - yields`() = runTest(UnconfinedTestDispatcher()) {
            val devicesFlow = MutableStateFlow<List<ManagedDevice>>(emptyList())
            val registry = AudioStreamOwnerRegistry()
            val (mod, _) = createModuleWithDeps(registry, devicesFlow)

            val dev = realDevice(musicVolume = 0.44f, actionDelayMs = 1000L)
            val devNoVolume = realDevice(musicVolume = null, actionDelayMs = 1000L)
            devicesFlow.value = listOf(dev)
            registry.onDeviceConnected(testAddress, "TestDevice", SourceDevice.Type.HEADPHONES, 1000L, 0L)

            every { volumeTool.getMaxVolume(streamId) } returns maxLevel

            val job = launch { mod.handle(DeviceEvent.Connected(dev)) }

            // User disables music volume during delay
            advanceTimeBy(500)
            devicesFlow.value = listOf(devNoVolume)

            advanceTimeBy(1500)
            job.join()

            coVerify(exactly = 0) { volumeTool.changeVolume(streamId, any<Float>()) }
            coVerify(exactly = 0) { volumeTool.changeVolume(streamId, any<Float>(), any(), any()) }
            observationGate.isSuppressed(streamId) shouldBe false
        }

        @Test
        fun `target volume edited during monitor - re-resolved device has new target`() = runTest(UnconfinedTestDispatcher()) {
            val devicesFlow = MutableStateFlow<List<ManagedDevice>>(emptyList())
            val registry = AudioStreamOwnerRegistry()
            val (mod, _) = createModuleWithDeps(registry, devicesFlow)

            // Note: monitor() uses the device passed in from handle, NOT a re-resolved one.
            // The re-resolve only affects setInitial. This test documents that behavior.
            val dev = realDevice(musicVolume = 0.44f, actionDelayMs = 0L, monitoringDurationMs = 4000L)
            devicesFlow.value = listOf(dev)
            registry.onDeviceConnected(testAddress, "TestDevice", SourceDevice.Type.HEADPHONES, 1000L, 0L)

            every { volumeTool.getMaxVolume(streamId) } returns maxLevel
            every { volumeTool.wasUs(streamId, targetLevel) } returns true
            coEvery { volumeTool.changeVolume(streamId, any<Float>(), any(), any()) } returns true

            val job = launch { mod.handle(DeviceEvent.Connected(dev)) }

            advanceTimeBy(100) // In monitor

            // External write — re-enforcement uses the target from handle's re-resolve
            volumeEvents.emit(VolumeEvent(streamId, targetLevel, 0, self = false))

            advanceTimeBy(4500)
            job.join()

            // Should re-enforce with the original target (0.44f)
            coVerify(atLeast = 1) { volumeTool.changeVolume(streamId, 0.44f) }
        }
    }
}
