package eu.darken.bluemusic.main.backup.core

import kotlinx.serialization.Serializable

@Serializable
data class AppBackup(
    val formatVersion: Int,
    val appVersion: String,
    val appVersionCode: Long = 0L,
    val createdAt: String,
    val deviceConfigs: List<DeviceConfigBackup> = emptyList(),
    val devicesSettings: DevicesSettingsBackup = DevicesSettingsBackup(),
    val generalSettings: GeneralSettingsBackup = GeneralSettingsBackup(),
)

@Serializable
data class DeviceConfigBackup(
    val address: String,
    val customName: String? = null,
    val lastConnected: Long = 0L,
    val actionDelay: Long? = null,
    val adjustmentDelay: Long? = null,
    val monitoringDuration: Long? = null,
    val musicVolume: Float? = null,
    val callVolume: Float? = null,
    val ringVolume: Float? = null,
    val notificationVolume: Float? = null,
    val alarmVolume: Float? = null,
    val volumeLock: Boolean = false,
    val volumeObserving: Boolean = false,
    val volumeRateLimiter: Boolean = false,
    val volumeRateLimitIncreaseMs: Long? = null,
    val volumeRateLimitDecreaseMs: Long? = null,
    val volumeSaveOnDisconnect: Boolean = false,
    val keepAwake: Boolean = false,
    val nudgeVolume: Boolean = false,
    val autoplay: Boolean = false,
    val launchPkgs: List<String> = emptyList(),
    val showHomeScreen: Boolean = false,
    val autoplayKeycodes: List<Int> = emptyList(),
    val isEnabled: Boolean = true,
    val visibleAdjustments: Boolean? = true,
    val dndMode: String? = null,
    val connectionAlertType: String = "none",
    val connectionAlertSoundUri: String? = null,
)

@Serializable
data class DevicesSettingsBackup(
    val isEnabled: Boolean = true,
    val restoreOnBoot: Boolean = true,
    val lockedDevices: Set<String> = emptySet(),
)

@Serializable
data class GeneralSettingsBackup(
    val themeMode: String = "SYSTEM",
    val themeStyle: String = "DEFAULT",
    val themeColor: String = "BLUE",
    val isOnboardingCompleted: Boolean = false,
    val isBatteryOptimizationHintDismissed: Boolean = false,
    val isAndroid10AppLaunchHintDismissed: Boolean = false,
    val isNotificationPermissionHintDismissed: Boolean = false,
)
