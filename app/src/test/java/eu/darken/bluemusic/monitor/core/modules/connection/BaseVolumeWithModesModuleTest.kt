package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDeviceWrapper
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerModeEvent
import eu.darken.bluemusic.monitor.core.audio.RingerModeObserver
import eu.darken.bluemusic.monitor.core.audio.RingerTool
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeModeTool
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeObservationGate
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class BaseVolumeWithModesModuleTest : BaseTest() {

    private val testAddress = "AA:BB:CC:DD:EE:FF"

    private lateinit var volumeTool: VolumeTool
    private lateinit var volumeObserver: VolumeObserver
    private lateinit var volumeModeTool: VolumeModeTool
    private lateinit var ringerTool: RingerTool
    private lateinit var ringerModeObserver: RingerModeObserver
    private lateinit var observationGate: VolumeObservationGate
    private lateinit var ringerEvents: MutableSharedFlow<RingerModeEvent>

    private val testSourceDevice = SourceDeviceWrapper(
        address = testAddress,
        alias = "TestDevice",
        name = "TestDevice",
        deviceType = SourceDevice.Type.HEADPHONES,
        isConnected = true,
    )

    @BeforeEach
    fun setup() {
        volumeTool = mockk(relaxed = true)
        volumeObserver = mockk(relaxed = true)
        volumeModeTool = mockk(relaxed = true)
        ringerTool = mockk(relaxed = true)
        ringerModeObserver = mockk(relaxed = true)
        observationGate = VolumeObservationGate()

        ringerEvents = MutableSharedFlow()
        every { ringerModeObserver.ringerMode } returns ringerEvents
        every { volumeTool.getMaxVolume(any()) } returns 15
        coEvery { volumeModeTool.alignSystemState(any(), any()) } returns true
    }

    private fun realDevice(
        actionDelayMs: Long = 0L,
        monitoringDurationMs: Long = 10000L,
        ringVolume: Float? = VolumeMode.LEGACY_SILENT_VALUE,
    ): ManagedDevice = ManagedDevice(
        isConnected = true,
        device = testSourceDevice,
        config = DeviceConfigEntity(
            address = testAddress,
            ringVolume = ringVolume,
            actionDelay = actionDelayMs,
            monitoringDuration = monitoringDurationMs,
            isEnabled = true,
        ),
    )

    private fun createModule(
        registry: AudioStreamOwnerRegistry = AudioStreamOwnerRegistry(),
        devicesFlow: MutableStateFlow<List<ManagedDevice>> = MutableStateFlow(emptyList()),
    ): Pair<BaseVolumeWithModesModule, AudioStreamOwnerRegistry> {
        val deviceRepo = mockk<DeviceRepo>(relaxed = true)
        every { deviceRepo.devices } returns devicesFlow
        val module = object : BaseVolumeWithModesModule(
            volumeTool, volumeObserver, observationGate, registry, deviceRepo,
            volumeModeTool, ringerTool, ringerModeObserver,
        ) {
            override val type = AudioStream.Type.RINGTONE
            override val priority = 10
        }
        return module to registry
    }

    @Test
    fun `ringer mode monitor yields on ownership change`() = runTest(UnconfinedTestDispatcher()) {
        val devicesFlow = MutableStateFlow<List<ManagedDevice>>(emptyList())
        val registry = AudioStreamOwnerRegistry()
        val (module, _) = createModule(registry, devicesFlow)
        val dev = realDevice(actionDelayMs = 0L, monitoringDurationMs = 10000L)
        devicesFlow.value = listOf(dev)

        registry.onDeviceConnected(testAddress, "TestDevice", SourceDevice.Type.HEADPHONES, 1000L, 0L)

        val job = launch { module.handle(DeviceEvent.Connected(dev)) }

        advanceTimeBy(100) // In monitor now

        // Change ownership
        registry.onDeviceConnected("NEW:ADDR:00:00:00:01", "NewDevice", SourceDevice.Type.HEADPHONES, 5000L, 1L)

        // Emit a ringer mode event to trigger the collect loop
        ringerEvents.emit(RingerModeEvent(RingerMode.SILENT, RingerMode.NORMAL))

        advanceTimeBy(100)
        job.join()

        // Should NOT re-enforce because ownership changed
        coVerify(exactly = 0) { ringerTool.setRingerMode(any()) }
    }

    @Test
    fun `ringer mode monitor re-enforces when ownership stable`() = runTest(UnconfinedTestDispatcher()) {
        val devicesFlow = MutableStateFlow<List<ManagedDevice>>(emptyList())
        val registry = AudioStreamOwnerRegistry()
        val (module, _) = createModule(registry, devicesFlow)
        val dev = realDevice(actionDelayMs = 0L, monitoringDurationMs = 10000L)
        devicesFlow.value = listOf(dev)

        registry.onDeviceConnected(testAddress, "TestDevice", SourceDevice.Type.HEADPHONES, 1000L, 0L)

        // ringerTool.wasUs returns true so the module re-enforces instead of yielding
        every { ringerTool.wasUs(RingerMode.SILENT) } returns true

        val job = launch { module.handle(DeviceEvent.Connected(dev)) }

        advanceTimeBy(100)

        // Emit event — ownership stable, wasUs=true → re-enforce
        ringerEvents.emit(RingerModeEvent(RingerMode.SILENT, RingerMode.NORMAL))

        advanceTimeBy(100)

        coVerify(atLeast = 1) { ringerTool.setRingerMode(RingerMode.SILENT) }

        // Let monitor time out
        advanceTimeBy(11000)
        job.join()
    }
}
