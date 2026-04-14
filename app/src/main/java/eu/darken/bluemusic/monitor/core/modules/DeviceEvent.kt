package eu.darken.bluemusic.monitor.core.modules

import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.ownership.DisconnectResult
import eu.darken.bluemusic.monitor.core.service.BluetoothEventQueue.VolumeSnapshot

interface DeviceEvent {

    val address: DeviceAddr
        get() = device.address

    val device: ManagedDevice

    data class Connected(
        override val device: ManagedDevice,
    ) : DeviceEvent {
        override fun toString(): String = "Connected(${device.toCompactString()})"
    }

    data class Disconnected(
        override val device: ManagedDevice,
        val volumeSnapshot: VolumeSnapshot? = null,
        val disconnectResult: DisconnectResult? = null,
    ) : DeviceEvent {
        override fun toString(): String = "Disconnected(${device.toCompactString()}, disconnectResult=$disconnectResult)"
    }
}