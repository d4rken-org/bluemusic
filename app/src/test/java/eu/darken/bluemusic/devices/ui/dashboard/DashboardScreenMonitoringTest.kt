package eu.darken.bluemusic.devices.ui.dashboard

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class DashboardScreenMonitoringTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val cardTitle: String
        get() = context.getString(R.string.title_monitoring_is_off)
    private val enableAction: String
        get() = context.getString(R.string.action_enable_monitoring)

    private fun dashboardState(isMonitoringEnabled: Boolean) = DashboardViewModel.State(
        devicesWithApps = listOf(
            DashboardViewModel.DeviceWithApps(MockDevice().toManagedDevice(isConnected = true), emptyList())
        ),
        isBluetoothEnabled = true,
        hasBluetoothPermission = true,
        isMonitoringEnabled = isMonitoringEnabled,
    )

    @Test
    fun `the card shows while monitoring is off`() {
        composeRule.setContent {
            PreviewWrapper {
                DashboardScreenUnderTest(
                    isMonitoringEnabled = false,
                    onDeviceAction = {},
                )
            }
        }

        composeRule.onNodeWithText(cardTitle).assertIsDisplayed()
    }

    @Test
    fun `the card stays hidden while monitoring is on`() {
        composeRule.setContent {
            PreviewWrapper {
                DashboardScreenUnderTest(
                    isMonitoringEnabled = true,
                    onDeviceAction = {},
                )
            }
        }

        composeRule.onNodeWithText(cardTitle).assertDoesNotExist()
    }

    @Test
    fun `the enable action reports the EnableMonitoring action`() {
        val actions = mutableListOf<DashboardAction>()
        composeRule.setContent {
            PreviewWrapper {
                DashboardScreenUnderTest(
                    isMonitoringEnabled = false,
                    onDeviceAction = { actions.add(it) },
                )
            }
        }

        composeRule.onNodeWithText(enableAction).performClick()

        actions shouldBe listOf(DashboardAction.EnableMonitoring)
    }

    @Composable
    private fun DashboardScreenUnderTest(
        isMonitoringEnabled: Boolean,
        onDeviceAction: (DashboardAction) -> Unit,
    ) {
        DevicesScreen(
            state = dashboardState(isMonitoringEnabled),
            onAddDevice = {},
            onDeviceConfig = {},
            onDeviceAction = onDeviceAction,
            onNavigateToSettings = {},
            onNavigateToUpgrade = {},
            onRequestBluetoothPermission = {},
        )
    }
}
