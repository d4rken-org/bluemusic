package eu.darken.bluemusic.eq.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class EqBoostGainTest : BaseTest() {

    @Test
    fun `a gain inside the range is kept`() {
        resolveBoostGain(0) shouldBe 0
        resolveBoostGain(450) shouldBe 450
        resolveBoostGain(EqEffectController.MAX_BOOST_GAIN_MB) shouldBe EqEffectController.MAX_BOOST_GAIN_MB
    }

    @Test
    fun `a negative gain is clamped to none`() {
        resolveBoostGain(-1) shouldBe 0
        resolveBoostGain(Int.MIN_VALUE) shouldBe 0
    }

    @Test
    fun `an oversized gain is clamped to the maximum`() {
        resolveBoostGain(EqEffectController.MAX_BOOST_GAIN_MB + 1) shouldBe EqEffectController.MAX_BOOST_GAIN_MB
        resolveBoostGain(Int.MAX_VALUE) shouldBe EqEffectController.MAX_BOOST_GAIN_MB
    }
}
