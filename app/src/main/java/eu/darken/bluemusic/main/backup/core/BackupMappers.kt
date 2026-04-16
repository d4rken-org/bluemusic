package eu.darken.bluemusic.main.backup.core

import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.alert.AlertType
import eu.darken.bluemusic.monitor.core.audio.DndMode

fun DeviceConfigEntity.toBackup(): DeviceConfigBackup = DeviceConfigBackup(
    address = address,
    customName = customName,
    lastConnected = lastConnected,
    actionDelay = actionDelay,
    adjustmentDelay = adjustmentDelay,
    monitoringDuration = monitoringDuration,
    musicVolume = musicVolume,
    callVolume = callVolume,
    ringVolume = ringVolume,
    notificationVolume = notificationVolume,
    alarmVolume = alarmVolume,
    volumeLock = volumeLock,
    volumeObserving = volumeObserving,
    volumeRateLimiter = volumeRateLimiter,
    volumeRateLimitIncreaseMs = volumeRateLimitIncreaseMs,
    volumeRateLimitDecreaseMs = volumeRateLimitDecreaseMs,
    volumeSaveOnDisconnect = volumeSaveOnDisconnect,
    keepAwake = keepAwake,
    nudgeVolume = nudgeVolume,
    autoplay = autoplay,
    launchPkgs = launchPkgs,
    showHomeScreen = showHomeScreen,
    autoplayKeycodes = autoplayKeycodes,
    isEnabled = isEnabled,
    visibleAdjustments = visibleAdjustments,
    dndMode = dndMode?.key,
    connectionAlertType = connectionAlertType.key,
    connectionAlertSoundUri = connectionAlertSoundUri,
)

fun DeviceConfigBackup.toEntity(): DeviceConfigEntity = DeviceConfigEntity(
    address = address,
    customName = customName,
    lastConnected = lastConnected,
    actionDelay = actionDelay,
    adjustmentDelay = adjustmentDelay,
    monitoringDuration = monitoringDuration,
    musicVolume = musicVolume,
    callVolume = callVolume,
    ringVolume = ringVolume,
    notificationVolume = notificationVolume,
    alarmVolume = alarmVolume,
    volumeLock = volumeLock,
    volumeObserving = volumeObserving,
    volumeRateLimiter = volumeRateLimiter,
    volumeRateLimitIncreaseMs = volumeRateLimitIncreaseMs,
    volumeRateLimitDecreaseMs = volumeRateLimitDecreaseMs,
    volumeSaveOnDisconnect = volumeSaveOnDisconnect,
    keepAwake = keepAwake,
    nudgeVolume = nudgeVolume,
    autoplay = autoplay,
    launchPkgs = launchPkgs,
    showHomeScreen = showHomeScreen,
    autoplayKeycodes = autoplayKeycodes,
    isEnabled = isEnabled,
    visibleAdjustments = visibleAdjustments,
    dndMode = DndMode.fromKey(dndMode),
    connectionAlertType = AlertType.fromKey(connectionAlertType),
    connectionAlertSoundUri = connectionAlertSoundUri,
)

/**
 * Checks for enum values in the backup that are not recognized by the current app version.
 * Returns a list of human-readable warning strings.
 */
fun DeviceConfigBackup.detectUnknownEnums(): List<String> = buildList {
    dndMode?.let { key ->
        if (DndMode.fromKey(key) == null && key != "null") {
            add("Unknown DnD mode '$key' for device $address, will be ignored")
        }
    }
    if (AlertType.fromKey(connectionAlertType) == AlertType.NONE && connectionAlertType != "none") {
        add("Unknown alert type '$connectionAlertType' for device $address, defaulting to none")
    }
}
