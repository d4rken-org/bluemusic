package eu.darken.bluemusic.common.error

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.bluemusic.common.ca.toCaString
import eu.darken.bluemusic.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * The dialog's fix/info dispatch runs arbitrary third-party code: an action that blows up must
 * never take the UI - or the dialog's exit - down with it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ComposeErrorDialogGuardTest : BaseTest() {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var dismissals = 0

    private class ThrowingFixError(private val onFix: (Activity) -> Unit) : Exception(), HasLocalizedError {
        override fun getLocalizedError() = LocalizedError(
            throwable = this,
            label = ERROR_TITLE.toCaString(),
            description = ERROR_BODY.toCaString(),
            fixActionLabel = FIX_LABEL.toCaString(),
            fixAction = onFix,
        )
    }

    private class ThrowingInfoError(private val onInfo: (Activity) -> Unit) : Exception(), HasLocalizedError {
        override fun getLocalizedError() = LocalizedError(
            throwable = this,
            label = ERROR_TITLE.toCaString(),
            description = ERROR_BODY.toCaString(),
            infoActionLabel = INFO_LABEL.toCaString(),
            infoAction = onInfo,
        )
    }

    private fun show(error: Throwable) {
        composeRule.setContent {
            PreviewWrapper {
                ErrorDialog(
                    throwable = error,
                    onDismiss = { dismissals++ },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a throwing fix action still closes the dialog`() {
        var invoked = false
        show(
            ThrowingFixError {
                // Flag first: the assertion below has to distinguish "action ran and threw" from
                // "action was never dispatched".
                invoked = true
                throw IllegalStateException("fix action exploded")
            }
        )

        composeRule.onNodeWithText(FIX_LABEL).performClick()
        composeRule.waitForIdle()

        invoked shouldBe true
        // Exactly one dismissal: the throw must neither swallow it nor double it.
        dismissals shouldBe 1
    }

    @Test
    fun `a throwing info action still closes the dialog`() {
        var invocations = 0
        show(
            ThrowingInfoError {
                invocations++
                throw IllegalStateException("info action exploded")
            }
        )

        composeRule.onNodeWithText(INFO_LABEL).performClick()
        composeRule.waitForIdle()

        invocations shouldBe 1
        // The info action dismisses too: a latched dialog would greet the user again on the way back.
        dismissals shouldBe 1
    }
}

private const val ERROR_TITLE = "Test error title"
private const val ERROR_BODY = "Test error description"
private const val FIX_LABEL = "Fix it"
private const val INFO_LABEL = "Tell me more"
