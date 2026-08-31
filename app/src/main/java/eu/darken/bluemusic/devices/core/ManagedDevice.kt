package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.alert.AlertType
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.DndMode
import eu.darken.bluemusic.monitor.core.audio.VolumeBand
import java.time.Duration
import java.time.Instant

data class ManagedDevice(
    val isConnected: Boolean,
    val device: SourceDevice,
    val config: DeviceConfigEntity,
) {
    val isActive: Boolean
        get() = isConnected && config.isEnabled
    val isEnabled: Boolean
        get() = config.isEnabled

    val address: DeviceAddr
        get() = config.address
    val type: SourceDevice.Type
        get() = device.deviceType
    val label: String
        get() = config.customName ?: device.label
    val lastConnected: Instant
        get() = Instant.ofEpochMilli(config.lastConnected)

    val monitoringDuration: Duration
        get() = config.monitoringDuration?.let { Duration.ofMillis(it) } ?: defaultMonitoringDuration
    val adjustmentDelay: Duration
        get() = config.adjustmentDelay?.let { Duration.ofMillis(it) } ?: defaultAdjustmentDelay
    val actionDelay: Duration
        get() = config.actionDelay?.let { Duration.ofMillis(it) } ?: defaultActionDelay
    val launchPkgs: List<String>
        get() = config.launchPkgs
    val nudgeVolume: Boolean
        get() = config.nudgeVolume
    val keepAwake: Boolean
        get() = config.keepAwake
    val volumeLock: Boolean
        get() = config.volumeLock
    val volumeObserving: Boolean
        get() = config.volumeObserving
    val volumeRateLimiter: Boolean
        get() = config.volumeRateLimiter
    val volumeObservingEffective: Boolean
        get() = volumeObserving && !volumeLock
    val volumeRateLimiterEffective: Boolean
        get() = volumeRateLimiter && !volumeLock
    val volumeObservingOverridden: Boolean
        get() = volumeObserving && volumeLock
    val volumeRateLimiterOverridden: Boolean
        get() = volumeRateLimiter && volumeLock
    val volumeRateLimitIncreaseMs: Long
        get() = config.volumeRateLimitIncreaseMs ?: 1000L
    val volumeRateLimitDecreaseMs: Long
        get() = config.volumeRateLimitDecreaseMs ?: 500L
    val volumeSaveOnDisconnect: Boolean
        get() = config.volumeSaveOnDisconnect
    val volumeLimit: Boolean
        get() = config.volumeLimit
    val autoplay: Boolean
        get() = config.autoplay
    val autoplayKeycodes: List<Int>
        get() = config.autoplayKeycodes
    val showHomeScreen: Boolean
        get() = config.showHomeScreen
    val visibleAdjustments: Boolean
        get() = config.visibleAdjustments ?: true
    val dndMode: DndMode?
        get() = config.dndMode
    val connectionAlertType: AlertType
        get() = config.connectionAlertType
    val connectionAlertSoundUri: String?
        get() = config.connectionAlertSoundUri
    val eqEnabled: Boolean
        get() = config.eqEnabled
    val eqBandLevels: List<Int>?
        get() = config.eqBandLevels
    val eqBoostGain: Int?
        get() = config.eqBoostGain
    /**
     * True when at least one stream is actually bounded, i.e. the toggle is on AND a stream that
     * this device manages carries a bound. The toggle alone is a normal state: the UI only offers
     * the per-stream bounds once it is on.
     */
    val hasEffectiveVolumeLimit: Boolean
        get() = volumeLimit && AudioStream.Type.entries.any { getVolumeBand(it) != null }

    /**
     * True when this device requires the foreground service to keep running for ongoing work.
     *
     * - Volume monitoring (lock / observing / rate-limiter / limit) needs continuous re-enforcement.
     * - Keep-awake needs the partial CPU wakelock held until disconnect.
     *
     * One-shot features (autoplay, app launch, connection alert, show home screen) are NOT
     * included — they fire once during the connect dispatch and the service is free to stop
     * after the post-dispatch idle grace.
     */
    val requiresPersistentSession: Boolean
        get() = volumeLock || volumeObserving || volumeRateLimiter || keepAwake || hasEffectiveVolumeLimit

    fun getVolume(type: AudioStream.Type): Float? = when (type) {
        AudioStream.Type.MUSIC -> config.musicVolume
        AudioStream.Type.CALL -> config.callVolume
        AudioStream.Type.RINGTONE -> config.ringVolume
        AudioStream.Type.NOTIFICATION -> config.notificationVolume
        AudioStream.Type.ALARM -> config.alarmVolume
    }

    fun getVolumeMin(type: AudioStream.Type): Float? = when (type) {
        AudioStream.Type.MUSIC -> config.musicVolumeMin
        AudioStream.Type.CALL -> config.callVolumeMin
        AudioStream.Type.RINGTONE -> config.ringVolumeMin
        AudioStream.Type.NOTIFICATION -> config.notificationVolumeMin
        AudioStream.Type.ALARM -> config.alarmVolumeMin
    }

    fun getVolumeMax(type: AudioStream.Type): Float? = when (type) {
        AudioStream.Type.MUSIC -> config.musicVolumeMax
        AudioStream.Type.CALL -> config.callVolumeMax
        AudioStream.Type.RINGTONE -> config.ringVolumeMax
        AudioStream.Type.NOTIFICATION -> config.notificationVolumeMax
        AudioStream.Type.ALARM -> config.alarmVolumeMax
    }

    /**
     * The bounds that apply to [type], or null when nothing should be bounded: the limit is off,
     * the stream isn't managed by this device, the stored target is a Silent/Vibrate sentinel (an
     * explicit user choice) or corrupt, or no bound is set.
     */
    fun getVolumeBand(type: AudioStream.Type): VolumeBand? {
        if (!volumeLimit) return null
        val target = getVolume(type) ?: return null
        if (target !in 0f..1f) return null
        val min = getVolumeMin(type)
        val max = getVolumeMax(type)
        if (min == null && max == null) return null
        return VolumeBand(min = min, max = max)
    }

    fun getStreamId(type: AudioStream.Type): AudioStream.Id = device.getStreamId(type)

    fun getStreamType(id: AudioStream.Id): AudioStream.Type? {
        for (type in AudioStream.Type.entries) {
            if (getStreamId(type) == id) return type
        }
        return null
    }

    private val defaultActionDelay: Duration = Duration.ofSeconds(4)
    private val defaultMonitoringDuration: Duration = Duration.ofSeconds(4)
    private val defaultAdjustmentDelay: Duration = Duration.ofMillis(250)

    fun toCompactString(): String = buildString {
        append("ManagedDevice($address/$label, active=$isActive, connected=$isConnected")
        if (eqEnabled) append(", eq=${eqBandLevels ?: "flat"}")
        if (eqBoostGain != null && eqBoostGain != 0) append(", boost=$eqBoostGain")
        append(")")
    }

    override fun toString(): String {
        return "ManagedDevice(isActive=$isActive, isConnected=$isConnected, isEnabled=$isEnabled, address=$address, last=$lastConnected, config=$config)"
    }
}
