package eu.darken.bluemusic.monitor.core.audio

import android.media.AudioManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class VolumeToolTest : BaseTest() {

    private lateinit var audioManager: AudioManager
    private lateinit var volumeTool: VolumeTool
    private var fakeTime = 0L

    @BeforeEach
    fun setup() {
        fakeTime = 1000L
        audioManager = mockk(relaxed = true)
        every { audioManager.getStreamMaxVolume(any()) } returns 15
        every { audioManager.getStreamVolume(any()) } returns 0

        volumeTool = VolumeTool(audioManager).apply {
            clock = { fakeTime }
        }
    }

    @Nested
    inner class MirroredPeer {
        @Test
        fun `VOICE_CALL write marks BLUETOOTH_HANDSFREE as us`() = runTest {
            volumeTool.changeVolume(AudioStream.Id.STREAM_VOICE_CALL, targetLevel = 10)

            volumeTool.wasUs(AudioStream.Id.STREAM_VOICE_CALL, 10) shouldBe true
            volumeTool.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 10) shouldBe true
        }

        @Test
        fun `BLUETOOTH_HANDSFREE write marks VOICE_CALL as us`() = runTest {
            volumeTool.changeVolume(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, targetLevel = 7)

            volumeTool.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 7) shouldBe true
            volumeTool.wasUs(AudioStream.Id.STREAM_VOICE_CALL, 7) shouldBe true
        }

        @Test
        fun `MUSIC write does not mirror to other streams`() = runTest {
            volumeTool.changeVolume(AudioStream.Id.STREAM_MUSIC, targetLevel = 12)

            volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 12) shouldBe true
            volumeTool.wasUs(AudioStream.Id.STREAM_VOICE_CALL, 12) shouldBe false
            volumeTool.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 12) shouldBe false
            volumeTool.wasUs(AudioStream.Id.STREAM_ALARM, 12) shouldBe false
        }
    }

    @Nested
    inner class WriteExpiry {
        @Test
        fun `wasUs returns true within TTL`() = runTest {
            volumeTool.changeVolume(AudioStream.Id.STREAM_MUSIC, targetLevel = 5)

            fakeTime += 400 // 400ms < 500ms TTL
            volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 5) shouldBe true
        }

        @Test
        fun `wasUs returns false after TTL expires`() = runTest {
            volumeTool.changeVolume(AudioStream.Id.STREAM_MUSIC, targetLevel = 5)

            fakeTime += 600 // 600ms > 500ms TTL
            volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 5) shouldBe false
        }

        @Test
        fun `mirrored entry also expires`() = runTest {
            volumeTool.changeVolume(AudioStream.Id.STREAM_VOICE_CALL, targetLevel = 8)

            fakeTime += 600
            volumeTool.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 8) shouldBe false
        }
    }

    @Nested
    inner class AlreadyAtTarget {
        @Test
        fun `already at target records direct stream only, not mirror`() = runTest {
            every { audioManager.getStreamVolume(AudioStream.Id.STREAM_VOICE_CALL.id) } returns 10

            volumeTool.changeVolume(AudioStream.Id.STREAM_VOICE_CALL, targetLevel = 10)

            volumeTool.wasUs(AudioStream.Id.STREAM_VOICE_CALL, 10) shouldBe true
            volumeTool.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 10) shouldBe false
        }
    }

    @Nested
    inner class WasUsBasics {
        @Test
        fun `wasUs returns false for unknown stream`() {
            volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 5) shouldBe false
        }

        @Test
        fun `wasUs returns false when volume does not match`() = runTest {
            volumeTool.changeVolume(AudioStream.Id.STREAM_MUSIC, targetLevel = 5)

            volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 3) shouldBe false
        }
    }
}
