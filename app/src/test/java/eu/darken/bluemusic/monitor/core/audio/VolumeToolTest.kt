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
        every { audioManager.getStreamVolume(AudioStream.Id.STREAM_VOICE_CALL.id) } returns 10

        volumeTool.changeVolume(AudioStream.Id.STREAM_VOICE_CALL, targetLevel = 10)

        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_VOICE_CALL, 10) shouldBe true
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE, 10) shouldBe false
        volumeTool.wasUs(AudioStream.Id.STREAM_VOICE_CALL, 10) shouldBe false
        verify(exactly = 0) {
            audioManager.setStreamVolume(AudioStream.Id.STREAM_VOICE_CALL.id, 10, any())
        }
    }
}
