package eu.darken.bluemusic.main.backup.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class BackupDataTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    private val jsonCompact = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun createMaximalFixture() = AppBackup(
        formatVersion = 1,
        appVersion = "3.3.1",
        appVersionCode = 33100L,
        createdAt = "2026-04-16T14:30:00Z",
        deviceConfigs = listOf(
            DeviceConfigBackup(
                address = "AA:BB:CC:DD:EE:FF",
                customName = "My Headphones",
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
                launchPkgs = listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
                showHomeScreen = true,
                autoplayKeycodes = listOf(126, 85),
                isEnabled = true,
                visibleAdjustments = false,
                dndMode = "priority_only",
                connectionAlertType = "sound",
                connectionAlertSoundUri = "content://media/external/audio/123",
            ),
            DeviceConfigBackup(
                address = "11:22:33:44:55:66",
            ),
        ),
        devicesSettings = DevicesSettingsBackup(
            isEnabled = false,
            restoreOnBoot = false,
            lockedDevices = setOf("AA:BB:CC:DD:EE:FF"),
        ),
        generalSettings = GeneralSettingsBackup(
            themeMode = "DARK",
            themeStyle = "MATERIAL_YOU",
            themeColor = "SUNSET",
            isOnboardingCompleted = true,
            isBatteryOptimizationHintDismissed = true,
            isAndroid10AppLaunchHintDismissed = true,
            isNotificationPermissionHintDismissed = true,
        ),
    )

    /**
     * Golden fixture test: if the JSON output changes (field renamed, reordered, removed, type changed),
     * this test fails. This protects against accidental format breakage.
     *
     * Do NOT update the expected JSON unless you are intentionally changing the backup format
     * (which requires bumping formatVersion).
     */
    @Test
    fun `serialized format matches golden fixture`() {
        val backup = createMaximalFixture()
        val actualJson = jsonCompact.encodeToString(AppBackup.serializer(), backup)

        actualJson.toComparableJson() shouldBe """
            {
                "formatVersion": 1,
                "appVersion": "3.3.1",
                "appVersionCode": 33100,
                "createdAt": "2026-04-16T14:30:00Z",
                "deviceConfigs": [
                    {
                        "address": "AA:BB:CC:DD:EE:FF",
                        "customName": "My Headphones",
                        "lastConnected": 1713270600000,
                        "actionDelay": 500,
                        "adjustmentDelay": 200,
                        "monitoringDuration": 30000,
                        "musicVolume": 0.75,
                        "callVolume": 0.5,
                        "ringVolume": 0.3,
                        "notificationVolume": 0.4,
                        "alarmVolume": 0.6,
                        "volumeLock": true,
                        "volumeObserving": true,
                        "volumeRateLimiter": true,
                        "volumeRateLimitIncreaseMs": 100,
                        "volumeRateLimitDecreaseMs": 200,
                        "volumeSaveOnDisconnect": true,
                        "keepAwake": true,
                        "nudgeVolume": true,
                        "autoplay": true,
                        "launchPkgs": [
                            "com.spotify.music",
                            "com.google.android.apps.youtube.music"
                        ],
                        "showHomeScreen": true,
                        "autoplayKeycodes": [
                            126,
                            85
                        ],
                        "isEnabled": true,
                        "visibleAdjustments": false,
                        "dndMode": "priority_only",
                        "connectionAlertType": "sound",
                        "connectionAlertSoundUri": "content://media/external/audio/123"
                    },
                    {
                        "address": "11:22:33:44:55:66",
                        "lastConnected": 0,
                        "volumeLock": false,
                        "volumeObserving": false,
                        "volumeRateLimiter": false,
                        "volumeSaveOnDisconnect": false,
                        "keepAwake": false,
                        "nudgeVolume": false,
                        "autoplay": false,
                        "launchPkgs": [],
                        "showHomeScreen": false,
                        "autoplayKeycodes": [],
                        "isEnabled": true,
                        "visibleAdjustments": true,
                        "connectionAlertType": "none"
                    }
                ],
                "devicesSettings": {
                    "isEnabled": false,
                    "restoreOnBoot": false,
                    "lockedDevices": [
                        "AA:BB:CC:DD:EE:FF"
                    ]
                },
                "generalSettings": {
                    "themeMode": "DARK",
                    "themeStyle": "MATERIAL_YOU",
                    "themeColor": "SUNSET",
                    "isOnboardingCompleted": true,
                    "isBatteryOptimizationHintDismissed": true,
                    "isAndroid10AppLaunchHintDismissed": true,
                    "isNotificationPermissionHintDismissed": true
                }
            }
        """.trimIndent()
    }

    @Test
    fun `round-trip serialization preserves all fields`() {
        val original = createMaximalFixture()
        val jsonString = json.encodeToString(AppBackup.serializer(), original)
        val restored = json.decodeFromString(AppBackup.serializer(), jsonString)
        restored shouldBe original
    }

    @Test
    fun `round-trip with minimal device config`() {
        val original = AppBackup(
            formatVersion = 1,
            appVersion = "1.0.0",
            createdAt = "2026-01-01T00:00:00Z",
            deviceConfigs = listOf(DeviceConfigBackup(address = "AA:BB:CC:DD:EE:FF")),
        )
        val jsonString = json.encodeToString(AppBackup.serializer(), original)
        val restored = json.decodeFromString(AppBackup.serializer(), jsonString)
        restored shouldBe original
    }

    @Test
    fun `deserialization tolerates unknown fields`() {
        val jsonString = """
        {
            "formatVersion": 1,
            "appVersion": "3.3.1",
            "createdAt": "2026-04-16T14:30:00Z",
            "unknownField": "should be ignored",
            "deviceConfigs": [{
                "address": "AA:BB:CC:DD:EE:FF",
                "futureField": 42
            }],
            "devicesSettings": {"unknownSetting": true},
            "generalSettings": {"newThemeThing": "fancy"}
        }
        """.trimIndent()

        val backup = json.decodeFromString(AppBackup.serializer(), jsonString)
        backup.formatVersion shouldBe 1
        backup.deviceConfigs.size shouldBe 1
        backup.deviceConfigs[0].address shouldBe "AA:BB:CC:DD:EE:FF"
    }

    @Test
    fun `deserialization with missing optional fields uses defaults`() {
        val jsonString = """
        {
            "formatVersion": 1,
            "appVersion": "1.0.0",
            "createdAt": "2026-01-01T00:00:00Z"
        }
        """.trimIndent()

        val backup = json.decodeFromString(AppBackup.serializer(), jsonString)
        backup.deviceConfigs shouldBe emptyList()
        backup.devicesSettings shouldBe DevicesSettingsBackup()
        backup.generalSettings shouldBe GeneralSettingsBackup()
    }

    @Test
    fun `DeviceConfigBackup defaults match entity defaults`() {
        val defaults = DeviceConfigBackup(address = "test")
        defaults.volumeLock shouldBe false
        defaults.volumeObserving shouldBe false
        defaults.isEnabled shouldBe true
        defaults.connectionAlertType shouldBe "none"
        defaults.launchPkgs shouldBe emptyList()
        defaults.autoplayKeycodes shouldBe emptyList()
        defaults.showHomeScreen shouldBe false
        defaults.visibleAdjustments shouldBe true
    }

    @Test
    fun `GeneralSettingsBackup defaults match settings defaults`() {
        val defaults = GeneralSettingsBackup()
        defaults.themeMode shouldBe "SYSTEM"
        defaults.themeStyle shouldBe "DEFAULT"
        defaults.themeColor shouldBe "BLUE"
        defaults.isOnboardingCompleted shouldBe false
    }

    @Test
    fun `DevicesSettingsBackup defaults match settings defaults`() {
        val defaults = DevicesSettingsBackup()
        defaults.isEnabled shouldBe true
        defaults.restoreOnBoot shouldBe true
        defaults.lockedDevices shouldBe emptySet()
    }

    @Test
    fun `serialized output contains all expected top-level keys`() {
        val backup = createMaximalFixture()
        val jsonString = json.encodeToString(AppBackup.serializer(), backup)
        jsonString shouldContain "\"formatVersion\""
        jsonString shouldContain "\"appVersion\""
        jsonString shouldContain "\"createdAt\""
        jsonString shouldContain "\"deviceConfigs\""
        jsonString shouldContain "\"devicesSettings\""
        jsonString shouldContain "\"generalSettings\""
    }

    @Test
    fun `enum string fields survive unknown values during deserialization`() {
        val jsonString = """
        {
            "formatVersion": 1,
            "appVersion": "1.0.0",
            "createdAt": "2026-01-01T00:00:00Z",
            "deviceConfigs": [{
                "address": "AA:BB:CC:DD:EE:FF",
                "dndMode": "future_silence_mode",
                "connectionAlertType": "hologram"
            }]
        }
        """.trimIndent()

        val backup = json.decodeFromString(AppBackup.serializer(), jsonString)
        backup.deviceConfigs[0].dndMode shouldBe "future_silence_mode"
        backup.deviceConfigs[0].connectionAlertType shouldBe "hologram"
    }
}
