package eu.darken.bluemusic.upgrade.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * Resolves the real flavor resources rather than a sample pattern, so this also proves the two
 * markers survive Android's format path and never reach the user.
 *
 * Flavor-agnostic on purpose: it asserts against whatever this variant's qualifier resource says
 * ("Pro" on GPLAY, "FOSS" on FOSS) so the one test guards both. The resources are flavor-owned, so
 * a variant that compiles proves nothing about the other.
 */
class BrandTitleTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val name: String
        get() = context.getString(R.string.app_name_short)

    private val qualifier: String
        get() = context.getString(R.string.app_name_upgrade_postfix)

    private val composed: String
        get() = context.getString(R.string.app_name_upgraded_template, name, qualifier)

    private fun capture(block: @Composable () -> AnnotatedString): AnnotatedString {
        lateinit var captured: AnnotatedString
        composeRule.setContent {
            PreviewWrapper { captured = block() }
        }
        composeRule.waitForIdle()
        return captured
    }

    @Test
    fun `without the qualifier the title is the bare app name`() {
        val result = capture { brandTitle(includeQualifier = false, highlightQualifier = false) }

        result.text shouldBe name
        result.spanStyles.size shouldBe 0
    }

    // The regression guard for the two-flag split: this is the form the plain-String callers get,
    // which needs the qualifier present but NOT colored. Collapsing the flags drops it;
    // highlighting on `includeQualifier` alone colors it. Both would still produce
    // plausible-looking text, so the span count is the assertion that matters.
    @Test
    fun `an included but unhighlighted qualifier is present and carries no span`() {
        val result = capture { brandTitle(includeQualifier = true, highlightQualifier = false) }

        result.text shouldBe composed
        result.text.contains(qualifier) shouldBe true
        result.spanStyles.size shouldBe 0
    }

    @Test
    fun `a highlighted qualifier carries exactly one span covering the qualifier only`() {
        lateinit var result: AnnotatedString
        var tertiary = Color.Unspecified
        composeRule.setContent {
            PreviewWrapper {
                result = brandTitle(includeQualifier = true, highlightQualifier = true)
                tertiary = MaterialTheme.colorScheme.tertiary
            }
        }
        composeRule.waitForIdle()

        result.text shouldBe composed
        result.spanStyles.size shouldBe 1
        val span = result.spanStyles.single()
        // Not just "a span exists" — the bug class this replaces put the highlight on the app name
        // while rendering perfectly correct text.
        result.text.substring(span.start, span.end) shouldBe qualifier
        span.item.color shouldBe tertiary
    }

    // The markers are injected as format arguments, so a template or formatter that mangled them
    // would leak U+FFFC / U+FFF9 into the toolbar.
    @Test
    fun `neither splice marker survives into the rendered title`() {
        val result = capture { brandTitle(includeQualifier = true, highlightQualifier = true) }

        result.text shouldNotContain BRAND_TITLE_MARKER
        result.text shouldNotContain BRAND_QUALIFIER_MARKER
    }

    @Test
    fun `the string form matches the annotated form`() {
        val result = capture { AnnotatedString(brandTitleText(includeQualifier = true)) }

        result.text shouldBe composed
    }

    // The Settings upgrade row composes its title through brandTitleText, the upgrade screen
    // through upgradeScreenTitle. The row used to read its own resource key — a hardcoded
    // per-locale copy of the brand that had drifted from the composed title — so the agreement
    // between the two surfaces is the property to guard, not any literal.
    @Test
    fun `the settings row title agrees with the upgrade screen title`() {
        lateinit var settingsRow: String
        lateinit var upgradeFree: AnnotatedString
        lateinit var upgradeUpgraded: AnnotatedString
        composeRule.setContent {
            PreviewWrapper {
                settingsRow = brandTitleText(includeQualifier = true)
                upgradeFree = upgradeScreenTitle(upgraded = false)
                upgradeUpgraded = upgradeScreenTitle(upgraded = true)
            }
        }
        composeRule.waitForIdle()

        settingsRow shouldBe upgradeFree.text
        settingsRow shouldBe upgradeUpgraded.text
        settingsRow shouldBe composed
        // Exactly once: a doubled qualifier would still "agree" across surfaces.
        Regex(Regex.escape(qualifier)).findAll(settingsRow).count() shouldBe 1
    }

    // The two-tone top-bar title. Both colors are asserted by role, and both ranges by content —
    // the top bars are the surface the original bug shipped on, and a regression that flattened
    // them to one tone (or swapped which part carries which role) would still render correct text.
    @Test
    fun `the two-tone title colors the name primary and the qualifier tertiary`() {
        lateinit var result: AnnotatedString
        var primary = Color.Unspecified
        var tertiary = Color.Unspecified
        composeRule.setContent {
            PreviewWrapper {
                result = brandTitleTwoTone()
                primary = MaterialTheme.colorScheme.primary
                tertiary = MaterialTheme.colorScheme.tertiary
            }
        }
        composeRule.waitForIdle()

        result.text shouldBe composed
        result.spanStyles.size shouldBe 2

        // Outer span first: it is pushed before the inner qualifier span is appended.
        val base = result.spanStyles[0]
        base.item.color shouldBe primary
        base.start shouldBe 0
        base.end shouldBe result.text.length

        val qualifierSpan = result.spanStyles[1]
        qualifierSpan.item.color shouldBe tertiary
        result.text.substring(qualifierSpan.start, qualifierSpan.end) shouldBe qualifier

        // The two roles must actually differ, or "two-tone" is vacuous.
        (primary == tertiary) shouldBe false
    }
}
