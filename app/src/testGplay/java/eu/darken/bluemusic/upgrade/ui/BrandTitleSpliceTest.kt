package eu.darken.bluemusic.upgrade.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * The brand is spliced into the already-formatted translation, so the styled postfix has to land on
 * the right offsets no matter where the pattern put the placeholder.
 */
class BrandTitleSpliceTest : BaseTest() {

    private val brandColor = Color.Red

    // "BVM Pro" with the postfix (4..7) colored, like upgradeScreenTitle(upgraded = true).
    private val brand: AnnotatedString = buildAnnotatedString {
        append("BVM ")
        pushStyle(SpanStyle(color = brandColor))
        append("Pro")
        pop()
    }

    @Test
    fun `marker in the middle shifts the styled postfix by the prefix`() {
        val result = spliceBrandTitle("Upgrade to $BRAND_TITLE_MARKER", brand)

        result.text shouldBe "Upgrade to BVM Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().item.color shouldBe brandColor
        result.spanStyles.single().start shouldBe 15
        result.spanStyles.single().end shouldBe 18
        result.text.substring(15, 18) shouldBe "Pro"
    }

    @Test
    fun `marker at the start keeps the postfix offsets inside the brand`() {
        val result = spliceBrandTitle("$BRAND_TITLE_MARKER holen", brand)

        result.text shouldBe "BVM Pro holen"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().start shouldBe 4
        result.spanStyles.single().end shouldBe 7
        result.text.substring(4, 7) shouldBe "Pro"
    }

    @Test
    fun `a duplicated marker renders the brand twice`() {
        val result = spliceBrandTitle("$BRAND_TITLE_MARKER und $BRAND_TITLE_MARKER", brand)

        result.text shouldBe "BVM Pro und BVM Pro"
        result.spanStyles.size shouldBe 2
        result.spanStyles[0].start shouldBe 4
        result.spanStyles[0].end shouldBe 7
        result.spanStyles[1].start shouldBe 16
        result.spanStyles[1].end shouldBe 19
        result.text.substring(16, 19) shouldBe "Pro"
    }

    @Test
    fun `a translation that lost the placeholder still shows the brand`() {
        val result = spliceBrandTitle("Upgrade to Pro", brand)

        result.text shouldBe "Upgrade to Pro BVM Pro"
        result.spanStyles.size shouldBe 1
        result.spanStyles.single().item.color shouldBe brandColor
        result.spanStyles.single().start shouldBe 19
        result.spanStyles.single().end shouldBe 22
    }
}
