package eu.darken.bluemusic.monitor.core.audio

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration

class VolumeModeToolTest : BaseTest() {

    private val volumeTool = mockk<VolumeTool>()
    private val ringerTool = mockk<RingerTool>()
    private val tool = VolumeModeTool(
        volumeTool = volumeTool,
        ringerTool = ringerTool,
    )

    @Test
    fun `apply normal ringtone restores normal ringer mode before updating ring stream`() = runTest {
        val calls = mutableListOf<String>()

        every { ringerTool.setRingerMode(RingerMode.NORMAL) } answers {
            calls += "ringer-normal"
            true
        }
        coEvery {
            volumeTool.changeVolume(
                AudioStream.Id.STREAM_RINGTONE,
                0.5f,
                true,
                Duration.ZERO,
            )
        } coAnswers {
            calls += "ring-volume"
            false
        }

        val changed = tool.apply(
            streamId = AudioStream.Id.STREAM_RINGTONE,
            streamType = AudioStream.Type.RINGTONE,
            volumeMode = VolumeMode.Normal(0.5f),
            visible = true,
        )

        changed shouldBe false
        calls shouldBe listOf("ringer-normal", "ring-volume")
    }

    @Test
    fun `apply normal non-ringtone updates stream without touching ringer mode`() = runTest {
        coEvery {
            volumeTool.changeVolume(
                AudioStream.Id.STREAM_MUSIC,
                0.25f,
                true,
                Duration.ZERO,
            )
        } returns true

        val changed = tool.apply(
            streamId = AudioStream.Id.STREAM_MUSIC,
            streamType = AudioStream.Type.MUSIC,
            volumeMode = VolumeMode.Normal(0.25f),
            visible = true,
        )

        changed shouldBe true
        coVerify(exactly = 1) { volumeTool.changeVolume(AudioStream.Id.STREAM_MUSIC, 0.25f, true, Duration.ZERO) }
        verify(exactly = 0) { ringerTool.setRingerMode(any()) }
    }

    @Test
    fun `alignSystemState applies silent for ringtone only`() = runTest {
        every { ringerTool.setRingerMode(RingerMode.SILENT) } returns true

        tool.alignSystemState(AudioStream.Type.RINGTONE, VolumeMode.Silent) shouldBe true
        tool.alignSystemState(AudioStream.Type.MUSIC, VolumeMode.Silent) shouldBe false
    }

    @Test
    fun `alignSystemState applies vibrate for ringtone only`() = runTest {
        every { ringerTool.setRingerMode(RingerMode.VIBRATE) } returns true

        tool.alignSystemState(AudioStream.Type.RINGTONE, VolumeMode.Vibrate) shouldBe true
        tool.alignSystemState(AudioStream.Type.ALARM, VolumeMode.Vibrate) shouldBe false
    }
}
