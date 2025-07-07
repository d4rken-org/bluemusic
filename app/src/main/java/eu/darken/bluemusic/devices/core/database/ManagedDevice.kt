package eu.darken.bluemusic.devices.core.database


import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import java.util.*

class ManagedDevice internal constructor(val sourceDevice: SourceDevice, val config: DeviceConf) {

    val isActive: Boolean = false

    private val maxVolumeMap = HashMap<AudioStream.Type, Int>()

    val label: String
        get() = sourceDevice.label

    val alias: String?
        get() = sourceDevice.alias

    val lastConnected: Long
        get() = config.lastConnected

    val name: String?
        get() = sourceDevice.name

    val address: String
        get() = sourceDevice.address

    val actionDelay: Long?
        get() = config.actionDelay

    val adjustmentDelay: Long?
        get() = config.adjustmentDelay

    val autoPlay: Boolean
        get() = config.autoplay

    val volumeLock: Boolean
        get() = config.volumeLock

    val keepAwake: Boolean
        get() = config.keepAwake

    val nudgeVolume: Boolean
        get() = config.nudgeVolume

    val launchPkg: String?
        get() = config.launchPkg

    val monitoringDuration: Long?
        get() = config.monitoringDuration

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

    fun getVolume(type: AudioStream.Type): Float? = when (type) {
        AudioStream.Type.MUSIC -> config.musicVolume
        AudioStream.Type.CALL -> config.callVolume
        AudioStream.Type.RINGTONE -> config.ringVolume
        AudioStream.Type.NOTIFICATION -> config.notificationVolume
        AudioStream.Type.ALARM -> config.alarmVolume
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
        log(TAG) { "$id is not mapped by $label." }
        return null
    }

    class Action(val device: ManagedDevice, val type: SourceDevice.Event.Type) {
        override fun toString(): String {
            return String.format(Locale.US, "ManagedDeviceAction(action=%s, device=%s)", type, device)
        }
    }

    companion object {
        private val TAG = logTag("ManagedDevice")
    }
}
