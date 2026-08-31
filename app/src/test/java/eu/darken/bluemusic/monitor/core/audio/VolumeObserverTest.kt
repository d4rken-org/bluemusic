package eu.darken.bluemusic.monitor.core.audio

import android.content.ContentResolver
import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeObserverTest : BaseTest() {

    private lateinit var context: Context
    private lateinit var audioManager: android.media.AudioManager
    private lateinit var audioLevels: MutableMap<AudioStream.Id, Int>
    private lateinit var volumeTool: VolumeTool

    private var fakeTime = 0L

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        fakeTime = 1000L

        audioManager = mockk(relaxed = true)
        audioLevels = AudioStream.Id.entries.associateWith { 0 }.toMutableMap().apply {
            this[AudioStream.Id.STREAM_MUSIC] = 2
        }

        every { context.contentResolver } returns contentResolver

        every { audioManager.getStreamMaxVolume(any()) } returns 15
        every { audioManager.getStreamVolume(any()) } answers {
            audioLevels[toStreamId(firstArg())] ?: 0
        }
        every { audioManager.setStreamVolume(any(), any(), any()) } answers {
            audioLevels[toStreamId(firstArg())] = secondArg()
        }

        volumeTool = VolumeTool(audioManager).apply {
            clock = { fakeTime }
        }
    }

    @Test
    fun `observer emits self true for delayed own write even when selfChange is false`() = runTest {
        val observer = VolumeObserver(
            context = context,
            appScope = backgroundScope,
            volumeTool = volumeTool,
        )

        observer.primeCache()

        volumeTool.changeVolume(AudioStream.Id.STREAM_MUSIC, targetLevel = 11)
        fakeTime += 600

        val events = mutableListOf<VolumeEvent>()
        observer.dispatchVolumeChanges(false) { events += it }

        events.single().copy(route = null) shouldBe VolumeEvent(
            streamId = AudioStream.Id.STREAM_MUSIC,
            oldVolume = 2,
            newVolume = 11,
            self = true,
        )
    }

    @Test
    fun `observer emits self false for external write even when selfChange is true`() = runTest {
        val observer = VolumeObserver(
            context = context,
            appScope = backgroundScope,
            volumeTool = volumeTool,
        )

        observer.primeCache()

        audioLevels[AudioStream.Id.STREAM_MUSIC] = 11

        val events = mutableListOf<VolumeEvent>()
        observer.dispatchVolumeChanges(true) { events += it }

        events.single().copy(route = null) shouldBe VolumeEvent(
            streamId = AudioStream.Id.STREAM_MUSIC,
            oldVolume = 2,
            newVolume = 11,
            self = false,
        )
    }

    @Test
    fun `the route is queried once per dispatch and attached to every event`() = runTest {
        val mockedTool = mockk<VolumeTool>(relaxed = true)
        val route = VolumeTool.MediaRoute(
            isBluetooth = true,
            addresses = setOf("AA:BB:CC:DD:EE:FF"),
            description = "predicted=[BT_A2DP#8]",
        )
        every { mockedTool.wasUs(any(), any()) } returns false
        every { mockedTool.queryActiveMediaRoute() } returns route
        every { mockedTool.getCurrentVolume(any()) } returns 3

        val observer = VolumeObserver(
            context = context,
            appScope = backgroundScope,
            volumeTool = mockedTool,
        )
        observer.primeCache()

        every { mockedTool.getCurrentVolume(any()) } returns 7

        val events = mutableListOf<VolumeEvent>()
        observer.dispatchVolumeChanges(false) { events += it }

        events.size shouldBe AudioStream.Id.entries.size
        events.map { it.route }.toSet() shouldBe setOf(route)
        verify(exactly = 1) { mockedTool.queryActiveMediaRoute() }
    }

    @Test
    fun `a failed route query still emits the volume change`() = runTest {
        every { audioManager.getDevices(any()) } throws SecurityException("nope")

        val observer = VolumeObserver(
            context = context,
            appScope = backgroundScope,
            volumeTool = volumeTool,
        )
        observer.primeCache()

        audioLevels[AudioStream.Id.STREAM_MUSIC] = 11

        val events = mutableListOf<VolumeEvent>()
        observer.dispatchVolumeChanges(false) { events += it }

        val event = events.single()
        event.newVolume shouldBe 11
        event.route?.isBluetooth shouldBe null
    }

    private fun toStreamId(id: Int): AudioStream.Id {
        return AudioStream.Id.entries.first { it.id == id }
    }
}
