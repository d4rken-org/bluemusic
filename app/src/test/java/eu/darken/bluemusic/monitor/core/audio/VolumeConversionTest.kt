package eu.darken.bluemusic.monitor.core.audio

import io.kotest.matchers.floats.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class VolumeConversionTest : BaseTest() {

    @Test
    fun `levelToPercentage at min returns 0`() {
        levelToPercentage(current = 0, min = 0, max = 15) shouldBe 0f
    }

    @Test
    fun `levelToPercentage at max returns 1`() {
        levelToPercentage(current = 15, min = 0, max = 15) shouldBe 1f
    }

    @Test
    fun `levelToPercentage mid range`() {
        levelToPercentage(current = 8, min = 0, max = 15).shouldBeWithinPercentageOf(0.533f, 1.0)
    }

    @Test
    fun `levelToPercentage with non-zero min at min returns 0`() {
        levelToPercentage(current = 1, min = 1, max = 15) shouldBe 0f
    }

    @Test
    fun `levelToPercentage with non-zero min at max returns 1`() {
        levelToPercentage(current = 15, min = 1, max = 15) shouldBe 1f
    }

    @Test
    fun `levelToPercentage below min clamps to 0`() {
        levelToPercentage(current = 0, min = 1, max = 15) shouldBe 0f
    }

    @Test
    fun `levelToPercentage above max clamps to 1`() {
        levelToPercentage(current = 20, min = 0, max = 15) shouldBe 1f
    }

    @Test
    fun `levelToPercentage zero range returns 0`() {
        levelToPercentage(current = 5, min = 5, max = 5) shouldBe 0f
    }

    @Test
    fun `levelToPercentage negative range returns 0`() {
        levelToPercentage(current = 5, min = 10, max = 5) shouldBe 0f
    }

    @Test
    fun `percentageToLevel 0 percent returns min`() {
        percentageToLevel(percentage = 0f, min = 0, max = 15) shouldBe 0
    }

    @Test
    fun `percentageToLevel 100 percent returns max`() {
        percentageToLevel(percentage = 1f, min = 0, max = 15) shouldBe 15
    }

    @Test
    fun `percentageToLevel 50 percent rounds correctly`() {
        percentageToLevel(percentage = 0.5f, min = 0, max = 15) shouldBe 8
    }

    @Test
    fun `percentageToLevel 0 percent with non-zero min returns min`() {
        percentageToLevel(percentage = 0f, min = 1, max = 15) shouldBe 1
    }

    @Test
    fun `percentageToLevel 50 percent with non-zero min`() {
        percentageToLevel(percentage = 0.5f, min = 1, max = 15) shouldBe 8
    }

    @Test
    fun `percentageToLevel 100 percent with non-zero min returns max`() {
        percentageToLevel(percentage = 1f, min = 1, max = 15) shouldBe 15
    }

    @Test
    fun `percentageToLevelFloor 0 percent returns min`() {
        percentageToLevelFloor(percentage = 0f, min = 0, max = 15) shouldBe 0
        percentageToLevelFloor(percentage = 0f, min = 1, max = 15) shouldBe 1
    }

    @Test
    fun `percentageToLevelFloor 100 percent returns max`() {
        percentageToLevelFloor(percentage = 1f, min = 0, max = 15) shouldBe 15
        percentageToLevelFloor(percentage = 1f, min = 1, max = 15) shouldBe 15
    }

    @Test
    fun `percentageToLevelCeil 0 percent returns min`() {
        percentageToLevelCeil(percentage = 0f, min = 0, max = 15) shouldBe 0
        percentageToLevelCeil(percentage = 0f, min = 1, max = 15) shouldBe 1
    }

    @Test
    fun `percentageToLevelCeil 100 percent returns max`() {
        percentageToLevelCeil(percentage = 1f, min = 0, max = 15) shouldBe 15
        percentageToLevelCeil(percentage = 1f, min = 1, max = 15) shouldBe 15
    }

    @Test
    fun `bound helpers on a degenerate stream return min`() {
        percentageToLevelFloor(percentage = 0.5f, min = 5, max = 5) shouldBe 5
        percentageToLevelCeil(percentage = 0.5f, min = 5, max = 5) shouldBe 5
        percentageToLevelFloor(percentage = 0.5f, min = 10, max = 5) shouldBe 10
        percentageToLevelCeil(percentage = 0.5f, min = 10, max = 5) shouldBe 10
    }

    @Test
    fun `a ceiling floors instead of rounding up past the bound`() {
        // roundToInt would give 8 (53.3%), which is above the 50% cap.
        percentageToLevel(percentage = 0.5f, min = 0, max = 15) shouldBe 8
        percentageToLevelFloor(percentage = 0.5f, min = 0, max = 15) shouldBe 7
    }

    @Test
    fun `a floor ceils instead of rounding down below the bound`() {
        percentageToLevelCeil(percentage = 0.5f, min = 0, max = 15) shouldBe 8
        percentageToLevelCeil(percentage = 0.1f, min = 0, max = 15) shouldBe 2
    }

    @Test
    fun `float representation error does not shift a bound by a whole step`() {
        // 0.6f * 25 evaluates to 15.000001
        percentageToLevelCeil(percentage = 0.6f, min = 0, max = 25) shouldBe 15
        percentageToLevelFloor(percentage = 0.6f, min = 0, max = 25) shouldBe 15
    }

    @Test
    fun `bound helpers with a non-zero stream minimum`() {
        percentageToLevelFloor(percentage = 0.5f, min = 1, max = 15) shouldBe 8
        percentageToLevelCeil(percentage = 0.5f, min = 1, max = 15) shouldBe 8
        percentageToLevelFloor(percentage = 0.3f, min = 1, max = 15) shouldBe 5
        percentageToLevelCeil(percentage = 0.3f, min = 1, max = 15) shouldBe 6
    }

    @Test
    fun `roundtrip preserves all levels in range`() {
        val min = 1
        val max = 15
        for (level in min..max) {
            val percent = levelToPercentage(level, min, max)
            val restored = percentageToLevel(percent, min, max)
            restored shouldBe level
        }
    }

    @Test
    fun `roundtrip preserves all levels with zero min`() {
        val min = 0
        val max = 15
        for (level in min..max) {
            val percent = levelToPercentage(level, min, max)
            val restored = percentageToLevel(percent, min, max)
            restored shouldBe level
        }
    }
}
