package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration

class BaseVolumeModuleTest : BaseTest() {

    private val streamId = AudioStream.Id.STREAM_MUSIC
    private val maxLevel = 15

    private lateinit var volumeTool: VolumeTool
    private lateinit var device: ManagedDevice
    private lateinit var module: TestVolumeModule

    /**
     * Concrete subclass that exposes [monitor] for direct testing without
     * going through [handle]'s actionDelay / setInitial overhead.
     */
    private class TestVolumeModule(volumeTool: VolumeTool) : BaseVolumeModule(volumeTool) {
        override val type = AudioStream.Type.MUSIC
        override val priority = 10

        suspend fun callMonitor(device: ManagedDevice, volumeMode: VolumeMode) {
            monitor(device, volumeMode)
        }
    }

    @BeforeEach
    fun setup() {
        volumeTool = mockk(relaxed = true)
        device = mockk(relaxed = true)
        module = TestVolumeModule(volumeTool)

        every { device.getStreamId(AudioStream.Type.MUSIC) } returns streamId
        every { volumeTool.getMaxVolume(streamId) } returns maxLevel
    }

    // --- monitor() wasUs-based abort ---

    /**
     * The monitor loop should run to completion when wasUs(target) stays true.
     * Using a 10ms wall-clock duration: with mocked delay returning instantly,
     * the loop runs many iterations. We just verify it ran more than once and
     * called changeVolume on each pass.
     */
    @Test
    fun `monitor loop runs to completion when wasUs stays true`() = runTest {
        every { device.monitoringDuration } returns Duration.ofMillis(10)
        every { volumeTool.wasUs(streamId, any()) } returns true
        var changeVolumeCount = 0
        coEvery { volumeTool.changeVolume(streamId, any<Float>()) } answers {
            changeVolumeCount++
            true
        }

        module.callMonitor(device, VolumeMode.Normal(0.44f))

        changeVolumeCount shouldBeGreaterThan 1
    }

    /**
     * When another VolumeTool caller writes a different level (e.g. user
     * drags the slider via AdjustVolume), wasUs(targetLevel) returns false.
     * The loop should abort immediately, calling changeVolume at most once
     * (from the first iteration before the abort triggered on the next tick).
     */
    @Test
    fun `monitor loop aborts when wasUs returns false - user slider drag`() = runTest {
        // 10s duration: without the abort, the test would take 10s of wall time
        every { device.monitoringDuration } returns Duration.ofSeconds(10)
        var wasUsCallCount = 0
        every { volumeTool.wasUs(streamId, any()) } answers {
            wasUsCallCount++
            wasUsCallCount <= 1  // true on 1st call, false on 2nd
        }
        var changeVolumeCount = 0
        coEvery { volumeTool.changeVolume(streamId, any<Float>()) } answers {
            changeVolumeCount++
            true
        }

        module.callMonitor(device, VolumeMode.Normal(0.44f))

        // First iteration: wasUs=true → changeVolume called.
        // Second iteration: wasUs=false → return before changeVolume.
        changeVolumeCount shouldBe 1
    }

    /**
     * When Android's platform writes change the stream level during the BT
     * routing transition, wasUs(target) stays true (Android doesn't go through
     * VolumeTool) and the loop correctly re-enforces by calling changeVolume.
     */
    @Test
    fun `monitor loop re-enforces against external platform writes`() = runTest {
        every { device.monitoringDuration } returns Duration.ofMillis(10)
        // wasUs always true: the loop believes it's still in control
        every { volumeTool.wasUs(streamId, any()) } returns true
        // changeVolume returns true: means it actually wrote (hardware had drifted)
        var changeVolumeCount = 0
        coEvery { volumeTool.changeVolume(streamId, any<Float>()) } answers {
            changeVolumeCount++
            true
        }

        module.callMonitor(device, VolumeMode.Normal(0.44f))

        // Multiple writes happened = the loop kept re-enforcing
        changeVolumeCount shouldBeGreaterThan 1
    }

    /**
     * Non-Normal VolumeMode (Silent, Vibrate) → monitor returns immediately
     * without entering the loop.
     */
    @Test
    fun `monitor returns immediately for non-Normal volumeMode`() = runTest {
        every { device.monitoringDuration } returns Duration.ofSeconds(10)

        module.callMonitor(device, VolumeMode.Silent)

        verify(exactly = 0) { volumeTool.wasUs(any(), any()) }
        coVerify(exactly = 0) { volumeTool.changeVolume(any(), any<Float>()) }
    }

    // --- handle() integration ---

    /**
     * Disconnected events are ignored by BaseVolumeModule (it only handles
     * Connected events).
     */
    @Test
    fun `handle ignores disconnected events`() = runTest {
        val event = DeviceEvent.Disconnected(device)
        module.handle(event)

        coVerify(exactly = 0) { volumeTool.changeVolume(any(), any<Float>()) }
    }

    /**
     * If the device has no configured volume for this stream type,
     * handle() returns early.
     */
    @Test
    fun `handle returns early for unconfigured stream`() = runTest {
        every { device.getVolume(AudioStream.Type.MUSIC) } returns null
        every { device.actionDelay } returns Duration.ZERO

        val event = DeviceEvent.Connected(device)
        module.handle(event)

        coVerify(exactly = 0) { volumeTool.changeVolume(any(), any<Float>()) }
    }
}
