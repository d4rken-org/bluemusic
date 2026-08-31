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
        formatVersion = 5,
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
                volumeLimit = true,
                musicVolumeMin = 0.1f,
                musicVolumeMax = 0.5f,
                callVolumeMin = null,
                callVolumeMax = 0.8f,
                ringVolumeMin = 0.2f,
                ringVolumeMax = null,
                notificationVolumeMin = null,
                notificationVolumeMax = null,
                alarmVolumeMin = 0.3f,
                alarmVolumeMax = 0.9f,
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
                eqEnabled = true,
                eqBandLevels = listOf(600, 300, 0, -300, -600),
                eqBoostGain = 500,
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
            isDndAccessHintDismissed = true,
            isSpeakerHintDismissed = true,
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
                "formatVersion": 5,
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
                        "volumeLimit": true,
                        "musicVolumeMin": 0.1,
                        "musicVolumeMax": 0.5,
                        "callVolumeMax": 0.8,
                        "ringVolumeMin": 0.2,
                        "alarmVolumeMin": 0.3,
                        "alarmVolumeMax": 0.9,
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
                        "connectionAlertSoundUri": "content://media/external/audio/123",
                        "eqEnabled": true,
                        "eqBandLevels": [
                            600,
                            300,
                            0,
                            -300,
                            -600
                        ],
                        "eqBoostGain": 500
                    },
                    {
                        "address": "11:22:33:44:55:66",
                        "lastConnected": 0,
                        "volumeLock": false,
                        "volumeObserving": false,
                        "volumeRateLimiter": false,
                        "volumeSaveOnDisconnect": false,
                        "volumeLimit": false,
                        "keepAwake": false,
                        "nudgeVolume": false,
                        "autoplay": false,
                        "launchPkgs": [],
                        "showHomeScreen": false,
                        "autoplayKeycodes": [],
                        "isEnabled": true,
                        "visibleAdjustments": true,
                        "connectionAlertType": "none",
                        "eqEnabled": false
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
                    "isNotificationPermissionHintDismissed": true,
                    "isDndAccessHintDismissed": true,
                    "isSpeakerHintDismissed": true
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
    fun `v2 payload without the equalizer fields decodes with the equalizer off`() {
        val jsonString = """
        {
            "formatVersion": 2,
            "appVersion": "3.3.1",
            "createdAt": "2026-04-16T14:30:00Z",
            "deviceConfigs": [{
                "address": "AA:BB:CC:DD:EE:FF",
                "musicVolume": 0.75,
                "connectionAlertType": "sound"
            }]
        }
        """.trimIndent()

        val backup = json.decodeFromString(AppBackup.serializer(), jsonString)
        backup.formatVersion shouldBe 2
        backup.deviceConfigs.single().eqEnabled shouldBe false
        backup.deviceConfigs.single().eqBandLevels shouldBe null
        backup.deviceConfigs.single().eqBoostGain shouldBe null
    }

    @Test
    fun `equalizer fields survive a round-trip`() {
        val original = AppBackup(
            formatVersion = 4,
            appVersion = "3.4.0",
            createdAt = "2026-04-16T14:30:00Z",
            deviceConfigs = listOf(
                DeviceConfigBackup(
                    address = "AA:BB:CC:DD:EE:FF",
                    eqEnabled = true,
                    eqBandLevels = listOf(-1500, -700, 0, 700, 1500),
                    eqBoostGain = 1000,
                ),
                DeviceConfigBackup(
                    address = "11:22:33:44:55:66",
                    eqEnabled = true,
                    eqBandLevels = null,
                    eqBoostGain = null,
                ),
                DeviceConfigBackup(
                    address = "22:33:44:55:66:77",
                    eqEnabled = true,
                    eqBoostGain = 0,
                ),
            ),
        )

        val restored = json.decodeFromString(AppBackup.serializer(), json.encodeToString(AppBackup.serializer(), original))

        restored shouldBe original
        restored.deviceConfigs[0].eqBandLevels shouldBe listOf(-1500, -700, 0, 700, 1500)
        restored.deviceConfigs[0].eqBoostGain shouldBe 1000
        restored.deviceConfigs[1].eqBandLevels shouldBe null
        restored.deviceConfigs[1].eqBoostGain shouldBe null
        restored.deviceConfigs[2].eqBoostGain shouldBe 0
    }

    @Test
    fun `v3 payload without the boost field decodes without a boost`() {
        val jsonString = """
        {
            "formatVersion": 3,
            "appVersion": "3.4.0",
            "createdAt": "2026-04-16T14:30:00Z",
            "deviceConfigs": [{
                "address": "AA:BB:CC:DD:EE:FF",
                "eqEnabled": true,
                "eqBandLevels": [600, 300, 0]
            }]
        }
        """.trimIndent()

        val backup = json.decodeFromString(AppBackup.serializer(), jsonString)
        backup.formatVersion shouldBe 3
        backup.deviceConfigs.single().eqEnabled shouldBe true
        backup.deviceConfigs.single().eqBandLevels shouldBe listOf(600, 300, 0)
        backup.deviceConfigs.single().eqBoostGain shouldBe null
    }

    @Test
    fun `v4 payload without the volume limit fields decodes with the limit off`() {
        val jsonString = """
        {
            "formatVersion": 4,
            "appVersion": "3.4.0",
            "createdAt": "2026-04-16T14:30:00Z",
            "deviceConfigs": [{
                "address": "AA:BB:CC:DD:EE:FF",
                "musicVolume": 0.75,
                "eqEnabled": true,
                "eqBoostGain": 500
            }]
        }
        """.trimIndent()

        val backup = json.decodeFromString(AppBackup.serializer(), jsonString)
        backup.formatVersion shouldBe 4
        backup.deviceConfigs.single().volumeLimit shouldBe false
        backup.deviceConfigs.single().musicVolumeMin shouldBe null
        backup.deviceConfigs.single().musicVolumeMax shouldBe null
        backup.deviceConfigs.single().alarmVolumeMax shouldBe null
    }

    @Test
    fun `volume limit fields survive a round-trip`() {
        val original = AppBackup(
            formatVersion = 5,
            appVersion = "3.5.0",
            createdAt = "2026-04-16T14:30:00Z",
            deviceConfigs = listOf(
                DeviceConfigBackup(
                    address = "AA:BB:CC:DD:EE:FF",
                    volumeLimit = true,
                    musicVolumeMin = 0.1f,
                    musicVolumeMax = 0.5f,
                    alarmVolumeMax = 0.9f,
                ),
            ),
        )

        val restored = json.decodeFromString(AppBackup.serializer(), json.encodeToString(AppBackup.serializer(), original))

        restored shouldBe original
        restored.deviceConfigs.single().musicVolumeMin shouldBe 0.1f
        restored.deviceConfigs.single().musicVolumeMax shouldBe 0.5f
        restored.deviceConfigs.single().alarmVolumeMin shouldBe null
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
        defaults.eqBoostGain shouldBe null
        defaults.volumeLimit shouldBe false
        defaults.musicVolumeMin shouldBe null
        defaults.musicVolumeMax shouldBe null
    }

    @Test
    fun `v1 payload without the speaker hint flag decodes with it disabled`() {
        val jsonString = """
        {
            "formatVersion": 1,
            "appVersion": "3.3.1",
            "createdAt": "2026-04-16T14:30:00Z",
            "generalSettings": {
                "themeMode": "DARK",
                "themeStyle": "MATERIAL_YOU",
                "themeColor": "SUNSET",
                "isOnboardingCompleted": true,
                "isBatteryOptimizationHintDismissed": true,
                "isAndroid10AppLaunchHintDismissed": true,
                "isNotificationPermissionHintDismissed": true,
                "isDndAccessHintDismissed": true
            }
        }
        """.trimIndent()

        val backup = json.decodeFromString(AppBackup.serializer(), jsonString)
        backup.formatVersion shouldBe 1
        backup.generalSettings.isDndAccessHintDismissed shouldBe true
        backup.generalSettings.isSpeakerHintDismissed shouldBe false
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
