package eu.darken.bluemusic.devices.ui.dashboard.rows.device

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class OptionIndicatorsTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val rangeLabel: String
        get() = context.getString(R.string.devices_indicator_volume_range)

    private fun render(configure: DeviceConfigEntity.() -> DeviceConfigEntity) {
        val base: ManagedDevice = MockDevice().toManagedDevice()
        val device = base.copy(config = base.config.configure())
        composeRule.setContent {
            PreviewWrapper {
                OptionIndicators(device = device)
            }
        }
    }

    @Test
    fun `a bounded stream shows the range chip`() {
        render { copy(volumeLimit = true, musicVolumeMin = 0.2f) }

        composeRule.onNodeWithText(rangeLabel).assertIsDisplayed()
    }

    // The toggle alone is a normal state: the bounds are only offered once it is on, so a device
    // that has it on and nothing bounded is not limited by anything.
    @Test
    fun `the toggle without a bound shows no range chip`() {
        render {
            copy(
                volumeLimit = true,
                musicVolumeMin = null,
                musicVolumeMax = null,
                callVolumeMin = null,
                callVolumeMax = null,
                ringVolumeMin = null,
                ringVolumeMax = null,
                notificationVolumeMin = null,
                notificationVolumeMax = null,
                alarmVolumeMin = null,
                alarmVolumeMax = null,
            )
        }

        composeRule.onNodeWithText(rangeLabel).assertDoesNotExist()
    }

    @Test
    fun `a stored bound with the toggle off shows no range chip`() {
        render { copy(volumeLimit = false, musicVolumeMin = 0.2f) }

        composeRule.onNodeWithText(rangeLabel).assertDoesNotExist()
    }
}
