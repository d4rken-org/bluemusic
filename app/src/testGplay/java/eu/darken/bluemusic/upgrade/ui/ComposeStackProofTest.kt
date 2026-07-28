package eu.darken.bluemusic.upgrade.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

// Minimal proof that the Robolectric + Compose + ui-test-junit4 stack renders and queries under the
// project's Robolectric version. If this fails, the three ported UI test classes can't run either.
class ComposeStackProofTest : BaseComposeRobolectricTest() {

    @Test
    fun `compose test harness renders and queries`() {
        composeRule.setContent {
            Text(text = "compose-stack-ok")
        }
        composeRule.onNodeWithText("compose-stack-ok").assertIsDisplayed()
    }
}
