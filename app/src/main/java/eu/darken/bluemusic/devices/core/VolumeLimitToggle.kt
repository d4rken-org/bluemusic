package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.common.upgrade.isProForUi
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import kotlin.math.roundToInt

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

/** How every surface spells a bound out. */
fun Float.toVolumePercent(): Int = (this * 100).roundToInt()

/**
 * A bound at the stream's own extreme constrains nothing: 0% is the stream's minimum and 100% its
 * maximum. Null instead keeps [ManagedDevice.hasEffectiveVolumeLimit] false, so a fully open band
 * doesn't hold the foreground service and its notification alive.
 *
 * The test is the displayed percentage, not the raw float: 0.004f prints as "At least 0%" while
 * behaving as a floor.
 *
 * 0f -> null, 0.004f -> null, 0.2f -> 0.2f
 */
fun normalizeVolumeLimitMin(min: Float?): Float? = min?.takeIf { it.toVolumePercent() > 0 }

/** The ceiling half of [normalizeVolumeLimitMin]: 1f -> null, 0.996f -> null, 0.7f -> 0.7f. */
fun normalizeVolumeLimitMax(max: Float?): Float? = max?.takeIf { it.toVolumePercent() < 100 }

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

    val normalizedMin = normalizeVolumeLimitMin(min)
    val normalizedMax = normalizeVolumeLimitMax(max)

    return when (type) {
        AudioStream.Type.MUSIC -> copy(musicVolumeMin = normalizedMin, musicVolumeMax = normalizedMax)
        AudioStream.Type.CALL -> copy(callVolumeMin = normalizedMin, callVolumeMax = normalizedMax)
        AudioStream.Type.RINGTONE -> copy(ringVolumeMin = normalizedMin, ringVolumeMax = normalizedMax)
        AudioStream.Type.NOTIFICATION -> copy(notificationVolumeMin = normalizedMin, notificationVolumeMax = normalizedMax)
        AudioStream.Type.ALARM -> copy(alarmVolumeMin = normalizedMin, alarmVolumeMax = normalizedMax)
    }
}

/** [normalizeVolumeLimitMin] / [normalizeVolumeLimitMax] applied to all five streams. */
fun DeviceConfigEntity.normalizedVolumeLimits(): DeviceConfigEntity = copy(
    musicVolumeMin = normalizeVolumeLimitMin(musicVolumeMin),
    musicVolumeMax = normalizeVolumeLimitMax(musicVolumeMax),
    callVolumeMin = normalizeVolumeLimitMin(callVolumeMin),
    callVolumeMax = normalizeVolumeLimitMax(callVolumeMax),
    ringVolumeMin = normalizeVolumeLimitMin(ringVolumeMin),
    ringVolumeMax = normalizeVolumeLimitMax(ringVolumeMax),
    notificationVolumeMin = normalizeVolumeLimitMin(notificationVolumeMin),
    notificationVolumeMax = normalizeVolumeLimitMax(notificationVolumeMax),
    alarmVolumeMin = normalizeVolumeLimitMin(alarmVolumeMin),
    alarmVolumeMax = normalizeVolumeLimitMax(alarmVolumeMax),
)
