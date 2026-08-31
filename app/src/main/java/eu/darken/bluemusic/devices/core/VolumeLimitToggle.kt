package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.common.upgrade.isProForUi
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream

// The pro check uses the UI gate: a paying user must not be told "not pro" just because billing
// hasn't settled yet. It only guards enabling — turning the limit off is always allowed, so a free
// user who restored a backup with it on is never locked into it.
suspend fun DeviceRepo.toggleVolumeLimit(address: DeviceAddr, upgradeRepo: UpgradeRepo): ToggleResult {
    if (!isManaged(address)) return ToggleResult.NOT_MANAGED

    // Only a device we can see to be limited is being disabled; anything else counts as enabling.
    val enabling = getDevice(address)?.volumeLimit != true
    if (enabling && !upgradeRepo.isProForUi()) return ToggleResult.NOT_PRO

    updateDevice(address) { config ->
        config.copy(
            volumeLimit = !config.volumeLimit,
        )
    }
    return ToggleResult.SUCCESS
}

/**
 * Rejects bounds that no apply path could honour: a bound outside 0..1 or not finite, or a floor
 * above the ceiling. A ceiling equal to the floor is allowed, it pins the stream to one level.
 *
 * Shared, because the config dialog is not the only writer: backup restore builds entities from
 * file contents without going through [setVolumeLimit], and an inverted pair would reach the
 * dashboard's `coerceIn(min, max)` and throw.
 *
 * @throws IllegalArgumentException when the pair could never be applied
 */
fun requireValidVolumeLimit(min: Float?, max: Float?) {
    require(min == null || (min.isFinite() && min in 0f..1f)) { "Invalid volume limit minimum: $min" }
    require(max == null || (max.isFinite() && max in 0f..1f)) { "Invalid volume limit maximum: $max" }
    require(min == null || max == null || min <= max) { "Volume limit minimum $min is above maximum $max" }
}

suspend fun DeviceRepo.setVolumeLimit(address: DeviceAddr, type: AudioStream.Type, min: Float?, max: Float?) {
    requireValidVolumeLimit(min, max)

    updateDevice(address) { config -> config.withVolumeLimit(type, min, max) }
}

/**
 * Puts [min] and [max] into the two fields [type] stores its band in.
 *
 * Checks the pair itself: a caller that hands this to a write queue has no earlier point to reject
 * an impossible band at.
 *
 * @throws IllegalArgumentException when the pair could never be applied
 */
fun DeviceConfigEntity.withVolumeLimit(type: AudioStream.Type, min: Float?, max: Float?): DeviceConfigEntity {
    requireValidVolumeLimit(min, max)

    return when (type) {
        AudioStream.Type.MUSIC -> copy(musicVolumeMin = min, musicVolumeMax = max)
        AudioStream.Type.CALL -> copy(callVolumeMin = min, callVolumeMax = max)
        AudioStream.Type.RINGTONE -> copy(ringVolumeMin = min, ringVolumeMax = max)
        AudioStream.Type.NOTIFICATION -> copy(notificationVolumeMin = min, notificationVolumeMax = max)
        AudioStream.Type.ALARM -> copy(alarmVolumeMin = min, alarmVolumeMax = max)
    }
}
