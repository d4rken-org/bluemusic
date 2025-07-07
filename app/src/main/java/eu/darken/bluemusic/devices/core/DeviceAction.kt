package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.bluetooth.core.SourceDevice

data class DeviceAction(
    val device: ManagedDevice,
    val type: SourceDevice.Event.Type
)