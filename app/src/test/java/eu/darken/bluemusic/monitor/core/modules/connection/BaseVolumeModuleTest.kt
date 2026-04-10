package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
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
    private lateinit var device: ManagedDevice
    private lateinit var module: TestVolumeModule

    private class TestVolumeModule(
        volumeTool: VolumeTool,
        volumeObserver: VolumeObserver,
    ) : BaseVolumeModule(volumeTool, volumeObserver) {
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
        device = mockk(relaxed = true)
        module = TestVolumeModule(volumeTool, volumeObserver)

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
}
