package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import java.time.Duration
import java.time.Instant

data class ManagedDevice(
    val isActive: Boolean,
    val device: SourceDevice,
    val config: DeviceConfigEntity,
) {

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
    val volumeRateLimitMs: Long
        get() = config.volumeRateLimitMs ?: 1000L // Default to 1 second
    val volumeSaveOnDisconnect: Boolean
        get() = config.volumeSaveOnDisconnect
    val autoplay: Boolean
        get() = config.autoplay

    fun getVolume(type: AudioStream.Type): Float? = when (type) {
        AudioStream.Type.MUSIC -> config.musicVolume
        AudioStream.Type.CALL -> config.callVolume
        AudioStream.Type.RINGTONE -> config.ringVolume
        AudioStream.Type.NOTIFICATION -> config.notificationVolume
        AudioStream.Type.ALARM -> config.alarmVolume
    }

    fun getStreamId(type: AudioStream.Type): AudioStream.Id = device.getStreamId(type)

    fun getStreamType(id: AudioStream.Id): AudioStream.Type? {
        for (type in AudioStream.Type.entries) {
            if (getStreamId(type) == id) return type
        }
        return null
    }

    private val defaultActionDelay: Duration = Duration.ofMillis(4000)
    private val defaultMonitoringDuration: Duration = Duration.ofMillis(4000)
    private val defaultAdjustmentDelay: Duration = Duration.ofMillis(250)

    override fun toString(): String {
        return "ManagedDevice(isActive=$isActive, address=$address, last=$lastConnected, config=$config)"
    }
}