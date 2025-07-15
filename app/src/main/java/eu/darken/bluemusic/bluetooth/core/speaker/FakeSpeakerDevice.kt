package eu.darken.bluemusic.bluetooth.core.speaker

import android.os.Build
import android.os.Parcelable
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import kotlinx.parcelize.Parcelize

@Parcelize
class FakeSpeakerDevice(
    override val alias: String,
    override val isActive: Boolean,
) : SourceDevice, Parcelable {

    override val name: String
        get() = Build.MODEL

    override val label: String
        get() {
            var label = alias
            if (label == null) label = name
            if (label == null) label = address
            return label
        }

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
        else -> throw IllegalArgumentException("Unsupported AudioStreamType: $type")
    }

    override fun toString(): String {
        return "FakeSpeakerDevice()"
    }

    companion object {
        const val ADDRESS = "self:speaker:main"
    }
}