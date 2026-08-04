package eu.darken.bluemusic.upgrade.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.PreviewWrapper
import org.junit.Assert.assertTrue
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class FossUpgradeScreenTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // The build-branded title: the short app name plus the flavor postfix, exactly what the shared
    // title helper composes. Asserted from resources, never as a hardcoded literal.
    private val composedFlavorTitle: String
        get() = "${context.getString(R.string.app_name_short)} ${context.getString(R.string.app_name_upgrade_postfix)}"

    @Test
    fun `renders the pitch content without a duplicated app bar title`() {
        composeRule.setUpgradeContent {
            UpgradeScreen()
        }

        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_preamble)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_why_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(firstFeatureLine(context, R.string.upgrade_screen_why_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_sponsor_action_hint)).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(1)
    }

    @Test
    fun `sponsor button invokes callback`() {
        var clicked = false

        composeRule.setUpgradeContent {
            UpgradeScreen(onGithubSponsors = { clicked = true })
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(1)
        composeRule.onNodeWithTag(UpgradeScreenTags.FOSS_SPONSOR).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }

    @Test
    fun `free status view shows the status without any pitch content`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(view = FossUpgradeView.STATUS_FREE)
        }

        composeRule.onAllNodesWithText(composedFlavorTitle).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_STATUS_FREE).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SHOW_OPTIONS).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_preamble)).assertCountEquals(0)
    }

    @Test
    fun `upgrade options button invokes callback`() {
        var clicked = false

        composeRule.setUpgradeContent {
            UpgradeScreen(view = FossUpgradeView.STATUS_FREE, onShowUpgradeOptions = { clicked = true })
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.FOSS_SHOW_OPTIONS)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }

    @Test
    fun `upgraded status view thanks the supporter and offers a recurring donation`() {
        val since = Instant.ofEpochMilli(1_700_000_000_000L)
        composeRule.setUpgradeContent {
            UpgradeScreen(view = FossUpgradeView.STATUS_UPGRADED, supporterSince = since)
        }

        composeRule.onAllNodesWithText(composedFlavorTitle).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_STATUS_UPGRADED).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_supporter_status_body))
            .assertCountEquals(1)
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
        composeRule.onAllNodesWithText(
            context.getString(R.string.upgrade_screen_supporter_since, formatter.format(since))
        ).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_DONATE).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SHOW_OPTIONS).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(0)
    }

    @Test
    fun `the supporter-since line stays away without a date`() {
        // UpgradeRepoFoss can report an upgrade whose timestamp predates the field: no date line
        // instead of a bogus one.
        composeRule.setUpgradeContent {
            UpgradeScreen(view = FossUpgradeView.STATUS_UPGRADED, supporterSince = null)
        }

        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
        composeRule.onAllNodesWithText(
            context.getString(R.string.upgrade_screen_supporter_since, formatter.format(Instant.EPOCH))
        ).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_STATUS_UPGRADED).assertCountEquals(1)
    }

    @Test
    fun `recurring donation button invokes the unarmed sponsors callback`() {
        var armed = 0
        var unarmed = 0

        composeRule.setUpgradeContent {
            UpgradeScreen(
                view = FossUpgradeView.STATUS_UPGRADED,
                onGithubSponsors = { armed++ },
                onOpenSponsors = { unarmed++ },
            )
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.FOSS_DONATE)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertTrue(unarmed == 1)
            assertTrue(armed == 0)
        }
    }
}

private fun ComposeContentTestRule.setUpgradeContent(
    content: @Composable () -> Unit,
) {
    setContent {
        PreviewWrapper {
            content()
        }
    }
}

private fun firstFeatureLine(context: Context, resId: Int): String = context.getString(resId)
    .lineSequence()
    .map { it.trim() }
    .first { it.startsWith("•") }
    .removePrefix("•")
    .trim()
