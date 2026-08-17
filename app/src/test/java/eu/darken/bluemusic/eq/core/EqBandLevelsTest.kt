package eu.darken.bluemusic.eq.core

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class EqBandLevelsTest : BaseTest() {

    @Test
    fun `matching band count is applied as stored`() {
        resolveBandLevels(listOf(300, 0, -300), bandCount = 3, minLevel = -1500, maxLevel = 1500) shouldBe
                listOf(300, 0, -300)
    }

    @Test
    fun `too few stored levels fall back to flat`() {
        resolveBandLevels(listOf(300, 0), bandCount = 5, minLevel = -1500, maxLevel = 1500) shouldBe
                listOf(0, 0, 0, 0, 0)
    }

    @Test
    fun `too many stored levels fall back to flat`() {
        resolveBandLevels(List(10) { 900 }, bandCount = 5, minLevel = -1500, maxLevel = 1500) shouldBe
                listOf(0, 0, 0, 0, 0)
    }

    @Test
    fun `unset levels are flat`() {
        resolveBandLevels(emptyList(), bandCount = 3, minLevel = -1500, maxLevel = 1500) shouldBe listOf(0, 0, 0)
    }

    @Test
    fun `levels are clamped into the engine range`() {
        resolveBandLevels(listOf(5000, -5000, 100), bandCount = 3, minLevel = -1200, maxLevel = 1200) shouldBe
                listOf(1200, -1200, 100)
    }

    @Test
    fun `an engine without bands gets nothing`() {
        resolveBandLevels(listOf(300), bandCount = 0, minLevel = -1500, maxLevel = 1500).shouldBeEmpty()
    }
}
