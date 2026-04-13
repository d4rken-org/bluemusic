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

    @Nested
    inner class SnapPercentage {
        @Test
        fun `snaps arbitrary float to nearest hardware step`() {
            every { audioManager.getStreamMaxVolume(AudioStream.Id.STREAM_MUSIC.id) } returns 15
            // 0.37823 * 15 = 5.673, rounds to 6, 6/15 = 0.4
            volumeTool.snapPercentage(AudioStream.Id.STREAM_MUSIC, 0.37823275f) shouldBe 0.4f
        }

        @Test
        fun `snap is idempotent for already-aligned values`() {
            every { audioManager.getStreamMaxVolume(AudioStream.Id.STREAM_MUSIC.id) } returns 15
            val aligned = 9f / 15f
            volumeTool.snapPercentage(AudioStream.Id.STREAM_MUSIC, aligned) shouldBe aligned
        }

        @Test
        fun `snap preserves zero`() {
            every { audioManager.getStreamMaxVolume(AudioStream.Id.STREAM_MUSIC.id) } returns 15
            volumeTool.snapPercentage(AudioStream.Id.STREAM_MUSIC, 0f) shouldBe 0f
        }

        @Test
        fun `snap preserves one`() {
            every { audioManager.getStreamMaxVolume(AudioStream.Id.STREAM_MUSIC.id) } returns 15
            volumeTool.snapPercentage(AudioStream.Id.STREAM_MUSIC, 1f) shouldBe 1f
        }

        @Test
        fun `snap with small range has coarser steps`() {
            every { audioManager.getStreamMaxVolume(AudioStream.Id.STREAM_RINGTONE.id) } returns 7
            // 0.5098 * 7 = 3.5686, rounds to 4, 4/7 = 0.5714286
            volumeTool.snapPercentage(AudioStream.Id.STREAM_RINGTONE, 0.5098425f) shouldBe (4f / 7f)
        }
    }

    @Nested
    inner class StreamScopedAdjusting {
        @Test
        fun `wasUs returns false for unrelated stream after write completes`() = runTest {
            volumeTool.changeVolume(AudioStream.Id.STREAM_MUSIC, targetLevel = 10)

            volumeTool.wasUs(AudioStream.Id.STREAM_RINGTONE, 5) shouldBe false
            volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 10) shouldBe true
        }

        @Test
        fun `wasUs returns true for written stream during active write`() = runTest {
            // Mid-write fast-path: adjustingStream matches the queried stream
            volumeTool.changeVolume(AudioStream.Id.STREAM_MUSIC, targetLevel = 10)

            volumeTool.wasUs(AudioStream.Id.STREAM_MUSIC, 10) shouldBe true
        }

        @Test
        fun `wasUs returns true for mirrored peer during active write`() = runTest {
            volumeTool.changeVolume(AudioStream.Id.STREAM_VOICE_CALL, targetLevel = 8)

            volumeTool.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 8) shouldBe true
        }

        @Test
        fun `wasUs returns false for non-mirrored stream after write`() = runTest {
            volumeTool.changeVolume(AudioStream.Id.STREAM_MUSIC, targetLevel = 10)

            volumeTool.wasUs(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 10) shouldBe false
            volumeTool.wasUs(AudioStream.Id.STREAM_ALARM, 10) shouldBe false
        }

        @Test
        fun `adjustingStream is null after write completes`() = runTest {
            volumeTool.changeVolume(AudioStream.Id.STREAM_MUSIC, targetLevel = 10)

            // After write, falls through to per-stream TTL check
            // Unknown stream with no TTL entry returns false
            volumeTool.wasUs(AudioStream.Id.STREAM_NOTIFICATION, 99) shouldBe false
        }
    }
}
