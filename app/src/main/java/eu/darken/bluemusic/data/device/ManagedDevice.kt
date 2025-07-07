package eu.darken.bluemusic.data.device

import eu.darken.bluemusic.main.core.audio.AudioStream

data class ManagedDevice(
    val address: String,
    val name: String? = null,
    var alias: String? = null,
    var lastConnected: Long = 0L,
    var actionDelay: Long? = null,
    var adjustmentDelay: Long? = null,
    var monitoringDuration: Long? = null,
    var musicVolume: Float? = null,
    var callVolume: Float? = null,
    var ringVolume: Float? = null,
    var notificationVolume: Float? = null,
    var alarmVolume: Float? = null,
    var volumeLock: Boolean = false,
    var keepAwake: Boolean = false,
    var nudgeVolume: Boolean = false,
    var autoplay: Boolean = false,
    var launchPkg: String? = null,
    var isActive: Boolean = false
) {
    val label: String
        get() = alias ?: name ?: address
    
    fun setVolume(type: AudioStream.Type, volume: Float?) {
        when (type) {
            AudioStream.Type.MUSIC -> musicVolume = volume
            AudioStream.Type.CALL -> callVolume = volume
            AudioStream.Type.RINGTONE -> ringVolume = volume
            AudioStream.Type.NOTIFICATION -> notificationVolume = volume
            AudioStream.Type.ALARM -> alarmVolume = volume
        }
    }
    
    fun getVolume(type: AudioStream.Type): Float? = when (type) {
        AudioStream.Type.MUSIC -> musicVolume
        AudioStream.Type.CALL -> callVolume
        AudioStream.Type.RINGTONE -> ringVolume
        AudioStream.Type.NOTIFICATION -> notificationVolume
        AudioStream.Type.ALARM -> alarmVolume
    }
    
    fun getStreamId(type: AudioStream.Type): AudioStream.Id {
        // This is a simplified version, in reality this would come from device capabilities
        return when (type) {
            AudioStream.Type.MUSIC -> AudioStream.Id.STREAM_MUSIC
            AudioStream.Type.CALL -> AudioStream.Id.STREAM_VOICE_CALL
            AudioStream.Type.RINGTONE -> AudioStream.Id.STREAM_RINGTONE
            AudioStream.Type.NOTIFICATION -> AudioStream.Id.STREAM_NOTIFICATION
            AudioStream.Type.ALARM -> AudioStream.Id.STREAM_ALARM
        }
    }

    fun getStreamType(id: AudioStream.Id): AudioStream.Type? {
        for (type in AudioStream.Type.values()) {
            if (getStreamId(type) == id) return type
        }
        return null
    }
}