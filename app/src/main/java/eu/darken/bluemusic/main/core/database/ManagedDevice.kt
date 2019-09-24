package eu.darken.bluemusic.main.core.database


import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.main.core.audio.AudioStream
import timber.log.Timber
import java.util.*

class ManagedDevice internal constructor(val sourceDevice: SourceDevice, val deviceConfig: DeviceConfig) {
    var isActive: Boolean = false

    private val maxVolumeMap = HashMap<AudioStream.Type, Int>()

    val label: String
        get() = sourceDevice.label

    val alias: String?
        get() = sourceDevice.alias

    var lastConnected: Long
        get() = deviceConfig.lastConnected
        set(timestamp) {
            deviceConfig.lastConnected = timestamp
        }

    val name: String?
        get() = sourceDevice.name

    val address: String
        get() = sourceDevice.address

    var actionDelay: Long?
        get() = deviceConfig.actionDelay
        set(actionDelay) {
            deviceConfig.actionDelay = actionDelay
        }

    var adjustmentDelay: Long?
        get() = deviceConfig.adjustmentDelay
        set(adjustmentDelay) {
            deviceConfig.adjustmentDelay = adjustmentDelay
        }

    var autoPlay: Boolean
        get() = deviceConfig.autoplay
        set(enabled) {
            deviceConfig.autoplay = enabled
        }

    var volumeLock: Boolean
        get() = deviceConfig.volumeLock
        set(enabled) {
            deviceConfig.volumeLock = enabled
        }

    var keepAwake: Boolean
        get() = deviceConfig.keepAwake
        set(enabled) {
            deviceConfig.keepAwake = enabled
        }

    var launchPkg: String?
        get() = deviceConfig.launchPkg
        set(pkg) {
            deviceConfig.launchPkg = pkg
        }

    var monitoringDuration: Long?
        get() = deviceConfig.monitoringDuration
        set(value) {
            deviceConfig.monitoringDuration = value
        }

    fun setAlias(newAlias: String): Boolean {
        return sourceDevice.setAlias(newAlias)
    }

    override fun toString(): String {
        return String.format(Locale.US, "Device(active=%b, address=%s, name=%s, musicVolume=%.2f, callVolume=%.2f, ringVolume=%.2f, alarmVolume=%.2f)",
                isActive, address, name, getVolume(AudioStream.Type.MUSIC), getVolume(AudioStream.Type.CALL), getVolume(AudioStream.Type.RINGTONE), getVolume(AudioStream.Type.ALARM))
    }

    fun setMaxVolume(type: AudioStream.Type, max: Int) {
        maxVolumeMap[type] = max
    }

    fun getMaxVolume(type: AudioStream.Type): Int {
        return maxVolumeMap[type]!!
    }

    fun setVolume(type: AudioStream.Type, volume: Float?) = when (type) {
        AudioStream.Type.MUSIC -> deviceConfig.musicVolume = volume
        AudioStream.Type.CALL -> deviceConfig.callVolume = volume
        AudioStream.Type.RINGTONE -> deviceConfig.ringVolume = volume
        AudioStream.Type.NOTIFICATION -> deviceConfig.notificationVolume = volume
        AudioStream.Type.ALARM -> deviceConfig.alarmVolume = volume
    }

    fun getVolume(type: AudioStream.Type): Float? = when (type) {
        AudioStream.Type.MUSIC -> deviceConfig.musicVolume
        AudioStream.Type.CALL -> deviceConfig.callVolume
        AudioStream.Type.RINGTONE -> deviceConfig.ringVolume
        AudioStream.Type.NOTIFICATION -> deviceConfig.notificationVolume
        AudioStream.Type.ALARM -> deviceConfig.alarmVolume
    }

    fun getRealVolume(type: AudioStream.Type): Int = Math.round(getMaxVolume(type) * getVolume(type)!!)

    fun getStreamId(type: AudioStream.Type): AudioStream.Id = sourceDevice.getStreamId(type)

    /**
     * @return NULL if no mapping exists for this device
     */
    fun getStreamType(id: AudioStream.Id): AudioStream.Type? {
        for (type in AudioStream.Type.values()) {
            if (getStreamId(type) == id) return type
        }
        Timber.d("%s is not mapped by %s.", id, label)
        return null
    }

    class Action(val device: ManagedDevice, val type: SourceDevice.Event.Type) {
        override fun toString(): String {
            return String.format(Locale.US, "ManagedDeviceAction(action=%s, device=%s)", type, device)
        }
    }
}
