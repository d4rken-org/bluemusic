package eu.darken.bluemusic.data.device

import eu.darken.bluemusic.bluetooth.core.SourceDevice

data class DeviceAction(
    val device: ManagedDevice,
    val type: SourceDevice.Event.Type
)