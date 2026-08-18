package eu.darken.bluemusic.monitor.core.ownership

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.DeviceAddr

data class DeviceGroup(
    val entries: List<ActiveEntry>,
) {
    val ownerKey: String
        get() = entries.map { it.address }.sorted().joinToString(",")
}

data class ActiveEntry(
    val address: DeviceAddr,
    val label: String,
    val deviceType: SourceDevice.Type,
    val connectedAt: Long,
    val sequence: Long,
    val approximate: Boolean,
)

data class ConnectResult(
    val previousOwnerAddresses: List<DeviceAddr>,
    val ownershipChanged: Boolean,
)

/**
 * Observable view of who currently owns the audio streams.
 *
 * Ownership is topological, one group owns all streams at a time (see
 * [AudioStreamOwnerRegistry.ownerAddressesFor]), so a single address list covers every stream.
 */
data class OwnerSnapshot(
    val ownerAddresses: List<DeviceAddr> = emptyList(),
    val generation: Long = 0L,
)

data class DisconnectResult(
    val wasInOwnerGroup: Boolean,
    val ownerGroupBefore: List<DeviceAddr>,
    val ownerGroupAfter: List<DeviceAddr>?,
)
