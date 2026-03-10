package eu.darken.bluemusic.devices.core.database

import eu.darken.bluemusic.monitor.core.alert.AlertType
import eu.darken.bluemusic.monitor.core.audio.DndMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class TypeConverterTest : BaseTest() {

    private val stringListConverter = StringListTypeConverter()
    private val intListConverter = IntListTypeConverter()
    private val dndModeConverter = DndModeTypeConverter()
    private val alertTypeConverter = AlertTypeConverter()

    // region StringListTypeConverter

    @Test
    fun `StringList - round trip`() {
        val original = listOf("alpha", "beta", "gamma")
        val json = stringListConverter.fromStringList(original)
        stringListConverter.toStringList(json) shouldBe original
    }

    @Test
    fun `StringList - empty list round trip`() {
        val json = stringListConverter.fromStringList(emptyList())
        stringListConverter.toStringList(json) shouldBe emptyList()
    }

    @Test
    fun `StringList - null input returns empty list`() {
        stringListConverter.toStringList(null) shouldBe emptyList()
    }

    @Test
    fun `StringList - empty string returns empty list`() {
        stringListConverter.toStringList("") shouldBe emptyList()
    }

    @Test
    fun `StringList - malformed JSON returns empty list`() {
        stringListConverter.toStringList("not valid json") shouldBe emptyList()
    }

    // endregion

    // region IntListTypeConverter

    @Test
    fun `IntList - round trip`() {
        val original = listOf(1, 2, 3)
        val json = intListConverter.fromIntList(original)
        intListConverter.toIntList(json) shouldBe original
    }

    @Test
    fun `IntList - empty list round trip`() {
        val json = intListConverter.fromIntList(emptyList())
        intListConverter.toIntList(json) shouldBe emptyList()
    }

    @Test
    fun `IntList - null input returns empty list`() {
        intListConverter.toIntList(null) shouldBe emptyList()
    }

    @Test
    fun `IntList - empty string returns empty list`() {
        intListConverter.toIntList("") shouldBe emptyList()
    }

    @Test
    fun `IntList - malformed JSON returns empty list`() {
        intListConverter.toIntList("garbage") shouldBe emptyList()
    }

    // endregion

    // region DndModeTypeConverter

    @Test
    fun `DndMode - all values round trip`() {
        DndMode.entries.forEach { mode ->
            val key = dndModeConverter.fromDndMode(mode)
            dndModeConverter.toDndMode(key) shouldBe mode
        }
    }

    @Test
    fun `DndMode - null mode to null key`() {
        dndModeConverter.fromDndMode(null) shouldBe null
    }

    @Test
    fun `DndMode - null key to null mode`() {
        dndModeConverter.toDndMode(null) shouldBe null
    }

    @Test
    fun `DndMode - unknown key to null`() {
        dndModeConverter.toDndMode("invalid_key") shouldBe null
    }

    // endregion

    // region AlertTypeConverter

    @Test
    fun `AlertType - all values round trip`() {
        AlertType.entries.forEach { type ->
            val key = alertTypeConverter.fromAlertType(type)
            alertTypeConverter.toAlertType(key!!) shouldBe type
        }
    }

    @Test
    fun `AlertType - null type to null key`() {
        alertTypeConverter.fromAlertType(null) shouldBe null
    }

    @Test
    fun `AlertType - null key to NONE`() {
        alertTypeConverter.toAlertType(null) shouldBe AlertType.NONE
    }

    @Test
    fun `AlertType - unknown key to NONE`() {
        alertTypeConverter.toAlertType("invalid_key") shouldBe AlertType.NONE
    }

    // endregion
}
