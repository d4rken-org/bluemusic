package eu.darken.bluemusic.devices.ui.dashboard.rows.device

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeBand
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class VolumeControlTest : BaseComposeRobolectricTest() {

    private val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun render(volume: Float?, band: VolumeBand?) {
        composeRule.setContent {
            PreviewWrapper {
                VolumeControl(
                    streamType = AudioStream.Type.MUSIC,
                    label = "Music",
                    volume = volume,
                    onVolumeChange = {},
                    band = band,
                )
            }
        }
    }

    @Test
    fun `a band with travel leaves the slider usable`() {
        render(volume = 0.5f, band = VolumeBand(min = 0.2f, max = 0.8f))

        composeRule.onNode(slider).assertIsEnabled()
    }

    @Test
    fun `a band whose bounds meet disables the slider`() {
        render(volume = 0.75f, band = VolumeBand(min = 0.4f, max = 0.4f))

        composeRule.onNode(slider).assertIsNotEnabled()
        // Still pinned to the band, and still readable as a percentage.
        composeRule.onNodeWithText("40%").assertExists()
    }

    @Test
    fun `an unbounded stream leaves the slider usable`() {
        render(volume = 0.5f, band = null)

        composeRule.onNode(slider).assertIsEnabled()
    }
}
