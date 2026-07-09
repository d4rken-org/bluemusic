package eu.darken.bluemusic.monitor.core.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * describeActiveMediaRoute's API 33+ branch needs a real SDK_INT, so it runs
 * under Robolectric. The pre-API33 fallback and exception paths are covered in
 * the pure-JVM [VolumeToolTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class VolumeToolRouteTest {

    private fun device(type: Int, product: String?): AudioDeviceInfo = mockk {
        every { getType() } returns type
        every { productName } returns product
    }

    private fun volumeTool(audioManager: AudioManager) = VolumeTool(audioManager)

    @Test
    fun `predicted route lists device type, raw id and product name`() {
        val audioManager = mockk<AudioManager>(relaxed = true) {
            every { isBluetoothA2dpOn } returns true
            every { isBluetoothScoOn } returns false
            every { getAudioDevicesForAttributes(any()) } returns listOf(
                device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone Speaker")
            )
        }

        val attrsSlot = slot<AudioAttributes>()

        val result = volumeTool(audioManager).describeActiveMediaRoute()

        result shouldContain "predicted=[SPEAKER#${AudioDeviceInfo.TYPE_BUILTIN_SPEAKER} 'Phone Speaker']"
        result shouldContain "a2dpOn=true"
        result shouldContain "scoOn=false"

        // The route must be queried for media playback, not some other usage.
        verify { audioManager.getAudioDevicesForAttributes(capture(attrsSlot)) }
        attrsSlot.captured.usage shouldBe AudioAttributes.USAGE_MEDIA
        attrsSlot.captured.contentType shouldBe AudioAttributes.CONTENT_TYPE_MUSIC
    }

    @Test
    fun `predicted route reports none for an empty active route`() {
        val audioManager = mockk<AudioManager>(relaxed = true) {
            every { isBluetoothA2dpOn } returns false
            every { isBluetoothScoOn } returns false
            every { getAudioDevicesForAttributes(any()) } returns emptyList()
        }

        volumeTool(audioManager).describeActiveMediaRoute() shouldContain "predicted=[none]"
    }

    @Test
    fun `bluetooth a2dp route is labelled BT_A2DP`() {
        val audioManager = mockk<AudioManager>(relaxed = true) {
            every { isBluetoothA2dpOn } returns true
            every { isBluetoothScoOn } returns false
            every { getAudioDevicesForAttributes(any()) } returns listOf(
                device(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Kona")
            )
        }

        volumeTool(audioManager).describeActiveMediaRoute() shouldContain "BT_A2DP#${AudioDeviceInfo.TYPE_BLUETOOTH_A2DP} 'Kona'"
    }

    @Test
    fun `route query failure is swallowed`() {
        val audioManager = mockk<AudioManager>(relaxed = true) {
            every { isBluetoothA2dpOn } returns false
            every { isBluetoothScoOn } returns false
            every { getAudioDevicesForAttributes(any()) } throws IllegalStateException("boom")
        }

        volumeTool(audioManager).describeActiveMediaRoute() shouldBe "route-query-failed: IllegalStateException: boom"
    }
}
