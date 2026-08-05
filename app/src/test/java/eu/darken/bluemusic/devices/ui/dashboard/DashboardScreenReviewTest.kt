package eu.darken.bluemusic.devices.ui.dashboard

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class DashboardScreenReviewTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val reviewAction: String
        get() = context.getString(R.string.review_app_review_action)
    private val dismissAction: String
        get() = context.getString(R.string.review_app_dismiss_action)

    private fun reviewState() = DashboardViewModel.State(
        devicesWithApps = listOf(
            DashboardViewModel.DeviceWithApps(MockDevice().toManagedDevice(isConnected = true), emptyList())
        ),
        isBluetoothEnabled = true,
        hasBluetoothPermission = true,
        showReviewCard = true,
    )

    @Test
    fun `both review actions reach the screen callbacks`() {
        var reviewed = 0
        var dismissed = 0
        composeRule.setContent {
            PreviewWrapper {
                DashboardScreenUnderTest(
                    onReview = { reviewed++ },
                    onReviewDismiss = { dismissed++ },
                )
            }
        }

        composeRule.onNodeWithText(reviewAction).performClick()
        composeRule.onNodeWithText(dismissAction).performClick()

        reviewed shouldBe 1
        dismissed shouldBe 1
    }

    @Test
    fun `an Activity host enables the review action`() {
        var reviewed = 0
        composeRule.setContent {
            // Same idiom the Host uses to obtain the Activity Play's flow needs.
            val activity = LocalContext.current as? Activity
            PreviewWrapper {
                DashboardScreenUnderTest(
                    onReview = activity?.let { { reviewed++ } },
                    onReviewDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(reviewAction).assertIsEnabled().performClick()

        reviewed shouldBe 1
    }

    @Composable
    private fun DashboardScreenUnderTest(
        onReview: (() -> Unit)?,
        onReviewDismiss: () -> Unit,
    ) {
        DevicesScreen(
            state = reviewState(),
            onAddDevice = {},
            onDeviceConfig = {},
            onDeviceAction = {},
            onNavigateToSettings = {},
            onNavigateToUpgrade = {},
            onRequestBluetoothPermission = {},
            onReview = onReview,
            onReviewDismiss = onReviewDismiss,
        )
    }
}
