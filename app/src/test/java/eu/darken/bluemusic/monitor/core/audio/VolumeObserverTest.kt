package eu.darken.bluemusic.monitor.core.audio

import android.content.ContentResolver
import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

@OptIn(ExperimentalCoroutinesApi::class)
class VolumeObserverTest : BaseTest() {

    private lateinit var context: Context
    private lateinit var audioLevels: MutableMap<AudioStream.Id, Int>
    private lateinit var volumeTool: VolumeTool

    private var fakeTime = 0L

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        fakeTime = 1000L

        val audioManager = mockk<android.media.AudioManager>(relaxed = true)
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

        events.single() shouldBe VolumeEvent(
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

        events.single() shouldBe VolumeEvent(
            streamId = AudioStream.Id.STREAM_MUSIC,
            oldVolume = 2,
            newVolume = 11,
            self = false,
        )
    }

    private fun toStreamId(id: Int): AudioStream.Id {
        return AudioStream.Id.entries.first { it.id == id }
    }
}
