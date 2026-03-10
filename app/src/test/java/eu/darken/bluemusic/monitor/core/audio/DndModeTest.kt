package eu.darken.bluemusic.monitor.core.audio

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DndModeTest : BaseTest() {

    @Test
    fun `fromKey returns correct mode for each key`() {
        DndMode.fromKey("off") shouldBe DndMode.OFF
        DndMode.fromKey("priority_only") shouldBe DndMode.PRIORITY_ONLY
        DndMode.fromKey("alarms_only") shouldBe DndMode.ALARMS_ONLY
        DndMode.fromKey("total_silence") shouldBe DndMode.TOTAL_SILENCE
    }

    @Test
    fun `fromKey null returns null`() {
        DndMode.fromKey(null) shouldBe null
    }

    @Test
    fun `fromKey unknown returns null`() {
        DndMode.fromKey("unknown") shouldBe null
        DndMode.fromKey("") shouldBe null
    }

    @Test
    fun `key property matches expected strings`() {
        DndMode.OFF.key shouldBe "off"
        DndMode.PRIORITY_ONLY.key shouldBe "priority_only"
        DndMode.ALARMS_ONLY.key shouldBe "alarms_only"
        DndMode.TOTAL_SILENCE.key shouldBe "total_silence"
    }

    @Test
    fun `round trip for all entries`() {
        DndMode.entries.forEach { mode ->
            DndMode.fromKey(mode.key) shouldBe mode
        }
    }
}
