package eu.darken.bluemusic.monitor.core.audio

import android.media.AudioManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)

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
    fun `delayed stepped writes skip no-op start step and retain final recent target`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 2
        val writes = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            writes += level
        }

        volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = 4,
            delay = Duration.ofMillis(1),
        )

        // Old behavior wrote 2,3,4 (no-op start). New behavior writes only 3,4.
        writes shouldBe listOf(3, 4)
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_MUSIC, 4) shouldBe true
    }

    @Test
    fun `ramp from 9 to 20 writes exactly 11 levels with no trailing delay`() = runTest {
        every { audioManager.getStreamMaxVolume(any()) } returns 25
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 9
        val writes = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            writes += level
        }

        val started = currentTime
        volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = 20,
            delay = Duration.ofMillis(500),
        )
        val elapsed = currentTime - started

        writes shouldBe (10..20).toList()
        // Per setVolume(): each call adds 10ms before + 10ms after = 20ms.
        // 11 writes with 10 inter-write delays of 500ms (no trailing delay):
        // 11 * 20 (write overhead) + 10 * 500 (inter-write delays) = 220 + 5000 = 5220
        elapsed shouldBe 5220L
    }

    @Test
    fun `ramp downwards to target writes step-by-step skipping current level`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 10
        val writes = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            writes += level
        }

        volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = 7,
            delay = Duration.ofMillis(1),
        )

        // 10 → 7 should write 9, 8, 7 (skip 10)
        writes shouldBe listOf(9, 8, 7)
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_MUSIC, 7) shouldBe true
    }

    @Test
    fun `min greater than max returns false without writing or recording target`() = runTest {
        // Defensive guard: if the platform reports degenerate stream bounds (min > max),
        // VolumeTool aborts before calling coerceIn (which would throw IllegalArgumentException).
        every { audioManager.getStreamMaxVolume(any()) } returns -1
        // getMinVolume returns 0 in unit tests (Build.VERSION.SDK_INT==0 path), so 0 > -1.
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 0
        val writes = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            writes += level
        }

        val result = volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = 5,
            delay = Duration.ofMillis(1),
        )

        result shouldBe false
        writes shouldBe emptyList()
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_MUSIC, 5) shouldBe false
    }

    @Test
    fun `target level below min is clamped to min`() = runTest {
        // In unit tests Build.VERSION.SDK_INT is 0 (no Robolectric), so getMinVolume returns 0.
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 3
        val writes = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            writes += level
        }

        volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = -2,
            delay = Duration.ofMillis(1),
        )

        // Clamped to min=0. Should ramp down 3 → 2 → 1 → 0.
        writes shouldBe listOf(2, 1, 0)
        volumeTool.hasRecentTarget(AudioStream.Id.STREAM_MUSIC, 0) shouldBe true
    }

    @Test
    fun `ramp with visible=false uses flag 0 for every write`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 2
        val flags = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            val flag = thirdArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            flags += flag
        }

        volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = 5,
            visible = false,
            delay = Duration.ofMillis(1),
        )

        flags shouldBe listOf(0, 0, 0)
    }

    @Test
    fun `ramp with visible=true uses FLAG_SHOW_UI for every write`() = runTest {
        audioLevels[AudioStream.Id.STREAM_MUSIC] = 2
        val flags = mutableListOf<Int>()
        every { audioManager.setStreamVolume(AudioStream.Id.STREAM_MUSIC.id, any(), any()) } answers {
            val level = secondArg<Int>()
            val flag = thirdArg<Int>()
            audioLevels[AudioStream.Id.STREAM_MUSIC] = level
            flags += flag
        }

        volumeTool.changeVolume(
            streamId = AudioStream.Id.STREAM_MUSIC,
            targetLevel = 5,
            visible = true,
            delay = Duration.ofMillis(1),
        )

        flags shouldBe listOf(
            android.media.AudioManager.FLAG_SHOW_UI,
            android.media.AudioManager.FLAG_SHOW_UI,
            android.media.AudioManager.FLAG_SHOW_UI,
        )
    }

    private fun toStreamId(id: Int): AudioStream.Id {
        return AudioStream.Id.entries.first { it.id == id }
    }
}
