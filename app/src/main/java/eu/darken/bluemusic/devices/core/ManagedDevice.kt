package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.main.core.audio.AudioStream

data class ManagedDevice(
    val address: String,
    val name: String? = null,
    val alias: String? = null,
    val lastConnected: Long = 0L,
    val actionDelay: Long? = null,
    val adjustmentDelay: Long? = null,
    val monitoringDuration: Long? = null,
    val musicVolume: Float? = null,
    val callVolume: Float? = null,
    val ringVolume: Float? = null,
    val notificationVolume: Float? = null,
    val alarmVolume: Float? = null,
    val volumeLock: Boolean = false,
    val keepAwake: Boolean = false,
    val nudgeVolume: Boolean = false,
    val autoplay: Boolean = false,
    val launchPkg: String? = null,
    val isActive: Boolean = false
) {
    val label: String
        get() = alias ?: name ?: address
    
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

    fun withUpdatedVolume(type: AudioStream.Type, volume: Float?): ManagedDevice = when (type) {
        AudioStream.Type.MUSIC -> copy(musicVolume = volume)
        AudioStream.Type.CALL -> copy(callVolume = volume)
        AudioStream.Type.RINGTONE -> copy(ringVolume = volume)
        AudioStream.Type.NOTIFICATION -> copy(notificationVolume = volume)
        AudioStream.Type.ALARM -> copy(alarmVolume = volume)
    }
}