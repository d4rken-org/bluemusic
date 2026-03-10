package eu.darken.bluemusic.monitor.core.audio

import eu.darken.bluemusic.monitor.core.audio.VolumeMode.Companion.toFloat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class VolumeModeTest : BaseTest() {

    @Test
    fun `fromFloat null returns null`() {
        VolumeMode.fromFloat(null) shouldBe null
    }

    @Test
    fun `fromFloat silent value returns Silent`() {
        VolumeMode.fromFloat(-2f) shouldBe VolumeMode.Silent
    }

    @Test
    fun `fromFloat vibrate value returns Vibrate`() {
        VolumeMode.fromFloat(-3f) shouldBe VolumeMode.Vibrate
    }

    @Test
    fun `fromFloat zero returns Normal(0)`() {
        VolumeMode.fromFloat(0f) shouldBe VolumeMode.Normal(0f)
    }

    @Test
    fun `fromFloat mid range returns Normal`() {
        VolumeMode.fromFloat(0.5f) shouldBe VolumeMode.Normal(0.5f)
    }

    @Test
    fun `fromFloat one returns Normal(1)`() {
        VolumeMode.fromFloat(1f) shouldBe VolumeMode.Normal(1f)
    }

    @Test
    fun `fromFloat out of range returns null`() {
        VolumeMode.fromFloat(-1f) shouldBe null
        VolumeMode.fromFloat(1.1f) shouldBe null
        VolumeMode.fromFloat(-100f) shouldBe null
    }

    @Test
    fun `toFloat null returns null`() {
        val mode: VolumeMode? = null
        mode.toFloat() shouldBe null
    }

    @Test
    fun `toFloat Silent returns -2f`() {
        VolumeMode.Silent.toFloat() shouldBe -2f
    }

    @Test
    fun `toFloat Vibrate returns -3f`() {
        VolumeMode.Vibrate.toFloat() shouldBe -3f
    }

    @Test
    fun `toFloat Normal returns percentage`() {
        VolumeMode.Normal(0.5f).toFloat() shouldBe 0.5f
        VolumeMode.Normal(0f).toFloat() shouldBe 0f
        VolumeMode.Normal(1f).toFloat() shouldBe 1f
    }

    @Test
    fun `Normal init rejects values outside 0-1`() {
        shouldThrow<IllegalArgumentException> { VolumeMode.Normal(-0.1f) }
        shouldThrow<IllegalArgumentException> { VolumeMode.Normal(1.1f) }
    }

    @Test
    fun `round trip for all modes`() {
        val modes = listOf(
            VolumeMode.Silent,
            VolumeMode.Vibrate,
            VolumeMode.Normal(0f),
            VolumeMode.Normal(0.5f),
            VolumeMode.Normal(1f),
        )
        modes.forEach { mode ->
            VolumeMode.fromFloat(mode.toFloat()) shouldBe mode
        }
    }
}
