package eu.darken.bluemusic.monitor.core.audio

import eu.darken.bluemusic.common.BuildWrap
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DndModeTest : BaseTest() {

    @AfterEach
    fun teardown() {
        unmockkObject(BuildWrap.VERSION)
    }

    private fun fakeSdk(level: Int) {
        mockkObject(BuildWrap.VERSION)
        every { BuildWrap.VERSION.SDK_INT } returns level
        every { BuildWrap.VERSION.CODENAME } returns "REL"
    }

    @Test
    fun `canTurnDndOff is true below API 35`() {
        fakeSdk(34)
        DndMode.canTurnDndOff() shouldBe true
    }

    @Test
    fun `canTurnDndOff is false on API 35+`() {
        fakeSdk(35)
        DndMode.canTurnDndOff() shouldBe false
        fakeSdk(36)
        DndMode.canTurnDndOff() shouldBe false
    }

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
