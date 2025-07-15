package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import java.time.Instant

data class ManagedDevice(
    val isActive: Boolean,
    val device: SourceDevice,
    val config: DeviceConfigEntity,
) {

    val address: DeviceAddr
        get() = config.address
    val label: String
        get() = config.alias ?: device.label
    val name: String?
        get() = device.name
    val lastConnected: Instant
        get() = Instant.ofEpochMilli(config.lastConnected)

    val monitoringDuration: Long?
        get() = config.monitoringDuration
    val adjustmentDelay: Long?
        get() = config.adjustmentDelay
    val actionDelay: Long?
        get() = config.actionDelay
    val launchPkg: String?
        get() = config.launchPkg
    val nudgeVolume: Boolean
        get() = config.nudgeVolume
    val keepAwake: Boolean
        get() = config.keepAwake
    val volumeLock: Boolean
        get() = config.volumeLock
    val volumeObserving: Boolean
        get() = config.volumeObserving
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
}