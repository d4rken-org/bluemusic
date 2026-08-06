package eu.darken.bluemusic.devices.ui.dashboard.rows

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * The card only disappears once the next state emission arrives, so its two tap targets need a
 * latch: a dismiss after a review would overwrite the completed-review bookkeeping with a snooze,
 * a review after a dismiss would re-open what the user just closed. The latch is asymmetric —
 * repeated review taps stay allowed, because a Play request can fail without persisting anything,
 * which leaves the card on screen and in need of a retry.
 */
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
    fun `a dismissed card ignores a later review tap`() {
        var reviewed = 0
        var dismissed = 0
        composeRule.setContent {
            PreviewWrapper {
                ReviewCard(onReview = { reviewed++ }, onDismiss = { dismissed++ })
            }
        }

        composeRule.onNodeWithText(dismissAction).performClick()
        composeRule.runOnIdle { dismissed shouldBe 1 }

        composeRule.onNodeWithText(reviewAction).assertIsNotEnabled()
        composeRule.onNodeWithText(reviewAction).performClick()

        composeRule.runOnIdle {
            reviewed shouldBe 0
            dismissed shouldBe 1
        }
    }

    @Test
    fun `a reviewed card ignores a later dismiss tap`() {
        var reviewed = 0
        var dismissed = 0
        composeRule.setContent {
            PreviewWrapper {
                ReviewCard(onReview = { reviewed++ }, onDismiss = { dismissed++ })
            }
        }

        composeRule.onNodeWithText(reviewAction).performClick()
        composeRule.runOnIdle { reviewed shouldBe 1 }

        composeRule.onNodeWithText(dismissAction).assertIsNotEnabled()
        composeRule.onNodeWithText(dismissAction).performClick()

        composeRule.runOnIdle {
            reviewed shouldBe 1
            dismissed shouldBe 0
        }
    }

    @Test
    fun `repeated review taps are not absorbed by the card`() {
        var reviewed = 0
        composeRule.setContent {
            PreviewWrapper {
                ReviewCard(onReview = { reviewed++ }, onDismiss = {})
            }
        }

        composeRule.onNodeWithText(reviewAction).performClick()
        composeRule.runOnIdle { reviewed shouldBe 1 }

        // A failed Play request persists nothing and leaves the card up, so the retry has to work.
        // Duplicates are the tool's problem, it holds a single-flight lock for exactly this.
        composeRule.onNodeWithText(reviewAction).assertIsEnabled()
        composeRule.onNodeWithText(reviewAction).performClick()

        composeRule.runOnIdle { reviewed shouldBe 2 }
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
        composeRule.onNodeWithText(reviewAction).performClick()

        // Nothing was handed to the caller, so the dismiss latch must not have been consumed.
        composeRule.onNodeWithText(dismissAction).assertIsEnabled()
        composeRule.onNodeWithText(dismissAction).performClick()

        composeRule.runOnIdle { dismissed shouldBe 1 }
    }
}
