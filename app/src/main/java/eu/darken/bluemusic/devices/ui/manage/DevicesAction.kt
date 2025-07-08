package eu.darken.bluemusic.devices.ui.manage

import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.main.core.audio.AudioStream

sealed interface DevicesAction {

    data class AdjustVolume(
        val addr: DeviceAddr,
        val type: AudioStream.Type,
        val volume: Float
    ) : DevicesAction

    data object RequestBluetoothPermission : DevicesAction
}