package eu.darken.bluemusic.devices.ui.dashboard.rows

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class ReviewCardTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val body: String
        get() = context.getString(R.string.review_app_body)
    private val reviewAction: String
        get() = context.getString(R.string.review_app_review_action)
    private val dismissAction: String
        get() = context.getString(R.string.review_app_dismiss_action)

    @Test
    fun `the card renders the ask and both actions`() {
        composeRule.setContent {
            PreviewWrapper {
                ReviewCard(onReview = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithText(body).assertIsDisplayed()
        composeRule.onNodeWithText(reviewAction).assertIsDisplayed()
        composeRule.onNodeWithText(dismissAction).assertIsDisplayed()
    }

    @Test
    fun `both actions report back to the caller`() {
        var reviewed = 0
        var dismissed = 0
        composeRule.setContent {
            PreviewWrapper {
                ReviewCard(onReview = { reviewed++ }, onDismiss = { dismissed++ })
            }
        }

        composeRule.onNodeWithText(reviewAction).performClick()
        composeRule.onNodeWithText(dismissAction).performClick()

        reviewed shouldBe 1
        dismissed shouldBe 1
    }

    @Test
    fun `the review action is disabled without a hosting activity`() {
        var dismissed = 0
        composeRule.setContent {
            PreviewWrapper {
                ReviewCard(onReview = null, onDismiss = { dismissed++ })
            }
        }

        // Play's flow can't be launched without an Activity, but the card still has to be dismissable.
        composeRule.onNodeWithText(reviewAction).assertIsNotEnabled()
        composeRule.onNodeWithText(dismissAction).performClick()

        dismissed shouldBe 1
    }
}
