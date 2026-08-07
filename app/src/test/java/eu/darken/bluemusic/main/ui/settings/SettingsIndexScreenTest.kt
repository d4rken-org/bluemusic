package eu.darken.bluemusic.main.ui.settings

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.PreviewWrapper
import org.junit.Test
import org.robolectric.annotation.Config
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * Guards the upgraded Settings row against drifting from the composed brand title. The row used to
 * read its own per-locale resource — a hardcoded copy of the brand that disagreed with the
 * dashboard and upgrade screen in a third of the locales — so what matters here is that the
 * rendered row goes through the shared composition, not what the words happen to be.
 */
class SettingsIndexScreenTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val composedTitle: String
        get() = context.getString(
            R.string.app_name_upgraded_template,
            context.getString(R.string.app_name_short),
            context.getString(R.string.app_name_upgrade_postfix),
        )

    private fun assertUpgradedRowShowsComposedTitle() {
        composeRule.setContent {
            PreviewWrapper {
                SettingsIndexScreen(
                    state = SettingsViewModel.State(versionText = "test", isUpgraded = true),
                    snackbarHostState = SnackbarHostState(),
                    onNavigateUp = {},
                    onNavigateTo = {},
                    onOpenUrl = {},
                    onCopyVersion = {},
                )
            }
        }
        composeRule.waitForIdle()

        // The row's subtitle is the unique anchor; the title text alone also matches the top bar.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(context.getString(R.string.settings_upgrade_status_desc)))

        // Exactly two surfaces carry the composed title: the top bar and the upgrade row. A row
        // that regressed to its own literal would leave the top bar as the only match.
        composeRule.onAllNodesWithText(composedTitle).assertCountEquals(2)
    }

    @Test
    fun `the upgraded settings row renders the composed brand title`() {
        assertUpgradedRowShowsComposedTitle()
    }

    // German is a locale whose brand short name differs from the default, so unlike the default
    // locale (where a hardcoded English copy would coincide with the composition), this variant
    // fails if the row stops composing from the locale's own resources.
    @Test
    @Config(qualifiers = "de")
    fun `the upgraded settings row renders the composed brand title in German`() {
        assertUpgradedRowShowsComposedTitle()
    }
}
