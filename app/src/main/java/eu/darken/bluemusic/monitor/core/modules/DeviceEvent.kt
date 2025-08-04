package eu.darken.bluemusic.monitor.core.modules

import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.ManagedDevice

interface DeviceEvent {

    val address: DeviceAddr
        get() = device.address

    val device: ManagedDevice

    data class Connected(
        override val device: ManagedDevice,
    ) : DeviceEvent

    data class Disconnected(
        override val device: ManagedDevice,
    ) : DeviceEvent
}