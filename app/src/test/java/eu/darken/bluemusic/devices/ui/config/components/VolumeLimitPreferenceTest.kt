package eu.darken.bluemusic.devices.ui.config.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.MusicNote
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import eu.darken.bluemusic.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class VolumeLimitPreferenceTest : BaseComposeRobolectricTest() {

    private var lastLimit: Pair<Float?, Float?>? = null

    private fun render(min: Float?, max: Float?, stepCount: Int? = null) {
        composeRule.setContent {
            PreviewWrapper {
                VolumeLimitPreference(
                    title = "Limit for Music",
                    description = "No limit set",
                    icon = Icons.TwoTone.MusicNote,
                    min = min,
                    max = max,
                    stepCount = stepCount,
                    onLimitChange = { newMin, newMax -> lastLimit = newMin to newMax },
                )
            }
        }
    }

    private val thumbMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    /** Index 0 is the start thumb, index 1 the end thumb. */
    private fun thumb(index: Int): SemanticsNodeInteraction = composeRule.onAllNodes(thumbMatcher)[index]

    private fun thumbValues(): List<Float> = composeRule
        .onAllNodes(thumbMatcher)
        .fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.ProgressBarRangeInfo].current }

    @Test
    fun `stored bounds seed the thumbs`() {
        render(min = 0.2f, max = 0.6f)

        thumbValues() shouldBe listOf(0.2f, 0.6f)
    }

    @Test
    fun `an absent bound seeds its thumb at the extreme`() {
        render(min = null, max = 0.6f)

        thumbValues() shouldBe listOf(0f, 0.6f)
    }

    @Test
    fun `an unlimited stream seeds both thumbs at the extremes`() {
        render(min = null, max = null)

        thumbValues() shouldBe listOf(0f, 1f)
    }

    // 0% is not a floor, it's the stream's own minimum. Storing it as a value instead of null would
    // leave the device looking bounded and keep the foreground service alive for nothing.
    @Test
    fun `releasing the start thumb at the bottom clears the minimum`() {
        render(min = 0.2f, max = 0.6f)

        thumb(0).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }

        lastLimit shouldBe (null to 0.6f)
    }

    @Test
    fun `releasing the end thumb at the top clears the maximum`() {
        render(min = 0.2f, max = 0.6f)

        thumb(1).performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }

        lastLimit shouldBe (0.2f to null)
    }

    @Test
    fun `a moved thumb keeps its value`() {
        render(min = 0.2f, max = 0.6f)

        thumb(1).performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }

        lastLimit shouldBe (0.2f to 0.8f)
    }

    // A 16 level stream has 15 steps; the slider counts the 14 values sitting between its two ends.
    @Test
    fun `a known step count reaches the slider`() {
        render(min = null, max = null, stepCount = 15)

        composeRule
            .onAllNodes(thumbMatcher)
            .fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.ProgressBarRangeInfo].steps } shouldBe listOf(14, 14)
    }
}
