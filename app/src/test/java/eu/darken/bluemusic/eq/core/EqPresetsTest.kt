package eu.darken.bluemusic.eq.core

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class EqPresetsTest : BaseTest() {

    private val presets = EqPresets()

    private fun caps(bandCount: Int, min: Int = -1500, max: Int = 1500) = EqCapabilities.Caps(
        bandCount = bandCount,
        minLevel = min,
        maxLevel = max,
        centerFrequencies = (0 until bandCount).map { 60_000 * (it + 1) },
    )

    @Test
    fun `every preset id has exactly one entry with a five point curve`() {
        presets.presets.map { it.id } shouldBe EqPresets.Id.entries
        presets.presets.forEach { it.curve.size shouldBe 5 }
    }

    @Test
    fun `flat is flat at any band count`() {
        presets.levelsFor(EqPresets.Id.FLAT, caps(3)) shouldBe listOf(0, 0, 0)
        presets.levelsFor(EqPresets.Id.FLAT, caps(5)) shouldBe listOf(0, 0, 0, 0, 0)
        presets.levelsFor(EqPresets.Id.FLAT, caps(10)) shouldBe List(10) { 0 }
    }

    @Test
    fun `a five band engine gets the curve points unchanged`() {
        presets.levelsFor(EqPresets.Id.BASS_BOOST, caps(5)) shouldBe listOf(1500, 900, 300, 0, 0)
    }

    @Test
    fun `three bands sample the curve at start middle and end`() {
        presets.levelsFor(EqPresets.Id.VOCAL, caps(5)) shouldBe listOf(-300, 450, 900, 450, -300)
        presets.levelsFor(EqPresets.Id.VOCAL, caps(3)) shouldBe listOf(-300, 900, -300)
    }

    @Test
    fun `ten bands interpolate between the curve points`() {
        val levels = presets.levelsFor(EqPresets.Id.TREBLE_BOOST, caps(10))

        levels.size shouldBe 10
        levels.first() shouldBe 0
        levels.last() shouldBe 1500
        // Monotonically rising towards the treble end
        levels.zipWithNext().forEach { (a, b) -> (b >= a) shouldBe true }
    }

    @Test
    fun `levels are scaled into the engine's own range`() {
        presets.levelsFor(EqPresets.Id.BASS_BOOST, caps(5, min = -600, max = 600)) shouldBe listOf(600, 360, 120, 0, 0)
        presets.levelsFor(EqPresets.Id.BASS_REDUCER, caps(5, min = -600, max = 600)) shouldBe
                listOf(-600, -360, -120, 0, 0)
    }

    @Test
    fun `asymmetric ranges scale each direction against its own bound`() {
        val asymmetric = caps(5, min = -300, max = 1200)

        presets.levelsFor(EqPresets.Id.BASS_BOOST, asymmetric) shouldBe listOf(1200, 720, 240, 0, 0)
        presets.levelsFor(EqPresets.Id.BASS_REDUCER, asymmetric) shouldBe listOf(-300, -180, -60, 0, 0)
    }

    @Test
    fun `levels never leave the engine's range`() {
        val tiny = caps(7, min = -100, max = 100)

        EqPresets.Id.entries.forEach { id ->
            presets.levelsFor(id, tiny).forEach { level ->
                (level in -100..100) shouldBe true
            }
        }
    }

    @Test
    fun `a curve beyond the normalized range is clamped`() {
        val clamped = presets.levelsFor(listOf(5.0, 0.0, 0.0, 0.0, -5.0), caps(5))

        clamped.first() shouldBe 1500
        clamped.last() shouldBe -1500
    }

    @Test
    fun `a single band engine samples the middle of the curve`() {
        presets.levelsFor(EqPresets.Id.VOCAL, caps(1)) shouldBe listOf(900)
    }

    @Test
    fun `no bands means no levels`() {
        presets.levelsFor(EqPresets.Id.BASS_BOOST, caps(0)).shouldBeEmpty()
    }

    @Test
    fun `loudness lifts both ends and leaves the middle alone`() {
        val levels = presets.levelsFor(EqPresets.Id.LOUDNESS, caps(5))

        levels.first() shouldBeGreaterThan 0
        levels.last() shouldBeGreaterThan 0
        levels[2] shouldBe 0
        levels[1] shouldBeLessThan levels.first()
    }
}
