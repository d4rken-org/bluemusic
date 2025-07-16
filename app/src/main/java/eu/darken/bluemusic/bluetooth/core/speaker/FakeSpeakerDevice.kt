package eu.darken.bluemusic.bluetooth.core.speaker

import android.os.Parcelable
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import kotlinx.parcelize.Parcelize

@Parcelize
class FakeSpeakerDevice(
    override val label: String,
    override val isActive: Boolean,
) : SourceDevice, Parcelable {

    override val address: String
        get() = ADDRESS

    override val deviceType: SourceDevice.Type
        get() = SourceDevice.Type.PHONE_SPEAKER

    override fun getStreamId(type: AudioStream.Type): AudioStream.Id = when (type) {
        AudioStream.Type.MUSIC -> AudioStream.Id.STREAM_MUSIC
        AudioStream.Type.CALL -> AudioStream.Id.STREAM_VOICE_CALL
        AudioStream.Type.RINGTONE -> AudioStream.Id.STREAM_RINGTONE
        AudioStream.Type.NOTIFICATION -> AudioStream.Id.STREAM_NOTIFICATION
        AudioStream.Type.ALARM -> AudioStream.Id.STREAM_ALARM
    }

    override fun toString(): String {
        return "FakeSpeakerDevice(isActive=$isActive)"
    }

    companion object {
        private const val ADDRESS = "self:speaker:main"
    }
}