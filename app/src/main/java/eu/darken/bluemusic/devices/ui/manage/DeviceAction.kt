package eu.darken.bluemusic.devices.ui.manage

import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.main.core.audio.AudioStream

sealed interface DeviceAction {

    val addr: DeviceAddr

    data class AdjustVolume(
        override val addr: DeviceAddr,
        val type: AudioStream.Type,
        val volume: Float
    ) : DeviceAction
}