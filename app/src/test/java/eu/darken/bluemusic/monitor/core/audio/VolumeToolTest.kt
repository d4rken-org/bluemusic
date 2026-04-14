package eu.darken.bluemusic.monitor.core.audio

import android.media.AudioManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration

class VolumeToolTest : BaseTest() {

    private lateinit var audioManager: AudioManager
    private lateinit var volumeTool: VolumeTool
    private lateinit var audioLevels: MutableMap<AudioStream.Id, Int>
    private var fakeTime = 0L

    @BeforeEach
    fun setup() {
        fakeTime = 1000L
        audioManager = mockk(relaxed = true)
        audioLevels = AudioStream.Id.entries.associateWith { 0 }.toMutableMap()
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
    fun `changeVolume writes to AudioManager and marks observed level as self once`() = runTest {
        volumeTool.changeVolume(AudioStream.Id.STREAM_MUSIC, targetLevel = 10)

        verify(exactly = 1) {
            audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, 10, 0)
        }
        volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 10) shouldBe true
        volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 10) shouldBe false
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_MUSIC, 10) shouldBe true
    }

    @Test
    fun `voice call write mirrors observer self classification to handsfree`() = runTest {
        volumeTool.changeVolume(AudioStream.Id.STREAM_VOICE_CALL, targetLevel = 8)

        volumeTool.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 8) shouldBe true
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 8) shouldBe true
    }

    @Test
    fun `already at target remembers recent target without pending observer write`() = runTest {
        audioLevels[AudioStream.Id.STREAM_VOICE_CALL] = 10

        volumeTool.changeVolume(AudioStream.Id.STREAM_VOICE_CALL, targetLevel = 10)

        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_VOICE_CALL, 10) shouldBe true
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 10) shouldBe false
        volumeTool.wasUs(AudioStream.Id.STREAM_VOICE_CALL, 10) shouldBe false
        verify(exactly = 0) {
            audioManager.setStreamVolume(AudioStream.Id.STREAM_VOICE_CALL.id, 10, any())
        }
    }

    @Test
    fun `delayed stepped writes classify intermediate levels as self and retain final recent target`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 2
        val selfClassifications = mutableListOf<Pair<Int, Boolean>>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            selfClassifications += level to volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, level)
        }

        volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = 4,
            delay = Duration.ofMillis(1),
        )

        selfClassifications shouldBe listOf(
            2 to true,
            3 to true,
            4 to true,
        )
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_MUSIC, 4) shouldBe true
    }

    private fun toStreamId(id: Int): AudioStream.Id {
        return AudioStream.Id.entries.first { it.id == id }
    }
}
