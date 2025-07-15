package eu.darken.bluemusic.bluetooth.core

import android.os.Parcelable
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.AudioStream.Id

interface SourceDevice : Parcelable {

    val name: String?
    val address: DeviceAddr
    val alias: String?
    val label: String
    val isActive: Boolean
    val deviceType: Type

    fun getStreamId(type: AudioStream.Type): Id

    enum class Type {
        UNKNOWN,
        PHONE_SPEAKER,
        HEADPHONES,
        HEADSET,
        CAR_AUDIO,
        PORTABLE_SPEAKER,
        COMPUTER,
        SMARTPHONE,
        WATCH,
        ;
    }
}