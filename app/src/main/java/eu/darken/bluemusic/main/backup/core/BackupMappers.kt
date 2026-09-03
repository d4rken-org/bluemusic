package eu.darken.bluemusic.main.backup.core

import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.devices.core.normalizedVolumeLimits
import eu.darken.bluemusic.devices.core.requireValidVolumeLimit
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
    volumeLimit = volumeLimit,
    musicVolumeMin = musicVolumeMin,
    musicVolumeMax = musicVolumeMax,
    callVolumeMin = callVolumeMin,
    callVolumeMax = callVolumeMax,
    ringVolumeMin = ringVolumeMin,
    ringVolumeMax = ringVolumeMax,
    notificationVolumeMin = notificationVolumeMin,
    notificationVolumeMax = notificationVolumeMax,
    alarmVolumeMin = alarmVolumeMin,
    alarmVolumeMax = alarmVolumeMax,
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
    eqEnabled = eqEnabled,
    eqBandLevels = eqBandLevels,
    eqBoostGain = eqBoostGain,
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
    volumeLimit = volumeLimit,
    musicVolumeMin = musicVolumeMin,
    musicVolumeMax = musicVolumeMax,
    callVolumeMin = callVolumeMin,
    callVolumeMax = callVolumeMax,
    ringVolumeMin = ringVolumeMin,
    ringVolumeMax = ringVolumeMax,
    notificationVolumeMin = notificationVolumeMin,
    notificationVolumeMax = notificationVolumeMax,
    alarmVolumeMin = alarmVolumeMin,
    alarmVolumeMax = alarmVolumeMax,
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
    eqEnabled = eqEnabled,
    eqBandLevels = eqBandLevels,
    eqBoostGain = eqBoostGain,
).normalizedVolumeLimits()

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

/**
 * @throws IllegalArgumentException when any stored min/max pair could never be applied
 */
fun DeviceConfigBackup.requireValidVolumeLimits() {
    requireValidVolumeLimit(musicVolumeMin, musicVolumeMax)
    requireValidVolumeLimit(callVolumeMin, callVolumeMax)
    requireValidVolumeLimit(ringVolumeMin, ringVolumeMax)
    requireValidVolumeLimit(notificationVolumeMin, notificationVolumeMax)
    requireValidVolumeLimit(alarmVolumeMin, alarmVolumeMax)
}
