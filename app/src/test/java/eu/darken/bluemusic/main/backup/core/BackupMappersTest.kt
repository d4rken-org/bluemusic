package eu.darken.bluemusic.main.backup.core

import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.alert.AlertType
import eu.darken.bluemusic.monitor.core.audio.DndMode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BackupMappersTest : BaseTest() {

    @Test
    fun `entity to backup round-trip preserves all fields`() {
        val entity = DeviceConfigEntity(
            address = "AA:BB:CC:DD:EE:FF",
            customName = "Test Device",
            lastConnected = 1713270600000L,
            actionDelay = 500L,
            adjustmentDelay = 200L,
            monitoringDuration = 30000L,
            musicVolume = 0.75f,
            callVolume = 0.5f,
            ringVolume = 0.3f,
            notificationVolume = 0.4f,
            alarmVolume = 0.6f,
            volumeLock = true,
            volumeObserving = true,
            volumeRateLimiter = true,
            volumeRateLimitIncreaseMs = 100L,
            volumeRateLimitDecreaseMs = 200L,
            volumeSaveOnDisconnect = true,
            keepAwake = true,
            nudgeVolume = true,
            autoplay = true,
            launchPkgs = listOf("com.example"),
            showHomeScreen = true,
            autoplayKeycodes = listOf(126),
            isEnabled = false,
            visibleAdjustments = false,
            dndMode = DndMode.PRIORITY_ONLY,
            connectionAlertType = AlertType.SOUND,
            connectionAlertSoundUri = "content://test/123",
        )

        val backup = entity.toBackup()
        val restored = backup.toEntity()

        restored shouldBe entity
    }

    @Test
    fun `entity to backup maps DndMode to key string`() {
        val entity = DeviceConfigEntity(address = "test", dndMode = DndMode.TOTAL_SILENCE)
        val backup = entity.toBackup()
        backup.dndMode shouldBe "total_silence"
    }

    @Test
    fun `entity to backup maps null DndMode to null`() {
        val entity = DeviceConfigEntity(address = "test", dndMode = null)
        val backup = entity.toBackup()
        backup.dndMode shouldBe null
    }

    @Test
    fun `entity to backup maps AlertType to key string`() {
        val entity = DeviceConfigEntity(address = "test", connectionAlertType = AlertType.BOTH)
        val backup = entity.toBackup()
        backup.connectionAlertType shouldBe "both"
    }

    @Test
    fun `backup to entity maps unknown DndMode key to null`() {
        val backup = DeviceConfigBackup(address = "test", dndMode = "future_mode")
        val entity = backup.toEntity()
        entity.dndMode shouldBe null
    }

    @Test
    fun `backup to entity maps unknown AlertType key to NONE`() {
        val backup = DeviceConfigBackup(address = "test", connectionAlertType = "hologram")
        val entity = backup.toEntity()
        entity.connectionAlertType shouldBe AlertType.NONE
    }

    @Test
    fun `detectUnknownEnums returns empty for known values`() {
        val backup = DeviceConfigBackup(
            address = "test",
            dndMode = "priority_only",
            connectionAlertType = "sound",
        )
        backup.detectUnknownEnums().shouldBeEmpty()
    }

    @Test
    fun `detectUnknownEnums detects unknown DndMode`() {
        val backup = DeviceConfigBackup(address = "AA:BB", dndMode = "future_mode")
        val warnings = backup.detectUnknownEnums()
        warnings shouldHaveSize 1
        warnings[0] shouldContain "future_mode"
        warnings[0] shouldContain "AA:BB"
    }

    @Test
    fun `detectUnknownEnums detects unknown AlertType`() {
        val backup = DeviceConfigBackup(address = "CC:DD", connectionAlertType = "hologram")
        val warnings = backup.detectUnknownEnums()
        warnings shouldHaveSize 1
        warnings[0] shouldContain "hologram"
        warnings[0] shouldContain "CC:DD"
    }

    @Test
    fun `detectUnknownEnums ignores null DndMode`() {
        val backup = DeviceConfigBackup(address = "test", dndMode = null)
        backup.detectUnknownEnums().shouldBeEmpty()
    }

    @Test
    fun `minimal entity round-trips correctly`() {
        val entity = DeviceConfigEntity(address = "FF:EE:DD:CC:BB:AA")
        val restored = entity.toBackup().toEntity()
        restored shouldBe entity
    }
}
