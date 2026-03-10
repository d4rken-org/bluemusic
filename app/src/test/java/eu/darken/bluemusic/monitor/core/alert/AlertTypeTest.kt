package eu.darken.bluemusic.monitor.core.alert

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AlertTypeTest : BaseTest() {

    @Test
    fun `fromKey returns correct type for each key`() {
        AlertType.fromKey("none") shouldBe AlertType.NONE
        AlertType.fromKey("sound") shouldBe AlertType.SOUND
        AlertType.fromKey("vibration") shouldBe AlertType.VIBRATION
        AlertType.fromKey("both") shouldBe AlertType.BOTH
    }

    @Test
    fun `fromKey null returns NONE`() {
        AlertType.fromKey(null) shouldBe AlertType.NONE
    }

    @Test
    fun `fromKey unknown returns NONE`() {
        AlertType.fromKey("unknown") shouldBe AlertType.NONE
        AlertType.fromKey("") shouldBe AlertType.NONE
    }

    @Test
    fun `key property matches expected strings`() {
        AlertType.NONE.key shouldBe "none"
        AlertType.SOUND.key shouldBe "sound"
        AlertType.VIBRATION.key shouldBe "vibration"
        AlertType.BOTH.key shouldBe "both"
    }

    @Test
    fun `round trip for all entries`() {
        AlertType.entries.forEach { type ->
            AlertType.fromKey(type.key) shouldBe type
        }
    }
}
