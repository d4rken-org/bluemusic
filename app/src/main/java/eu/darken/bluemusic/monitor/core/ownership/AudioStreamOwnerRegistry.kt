package eu.darken.bluemusic.monitor.core.ownership

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioStreamOwnerRegistry @Inject constructor() {

    private val mutex = Mutex()
    private val entries = mutableMapOf<DeviceAddr, ActiveEntry>()
    private var _generation: Long = 0L

    suspend fun onDeviceConnected(
        address: DeviceAddr,
        label: String,
        deviceType: SourceDevice.Type,
        receivedAtElapsedMs: Long,
        sequence: Long,
    ): ConnectResult = mutex.withLock {
        val oldOwnerGroup = resolveOwnerGroupLocked()
        val oldOwnerKey = oldOwnerGroup?.ownerKey
        val previousOwnerAddresses = oldOwnerGroup?.entries?.map { it.address } ?: emptyList()
        entries[address] = ActiveEntry(
            address = address,
            label = label,
            deviceType = deviceType,
            connectedAt = receivedAtElapsedMs,
            sequence = sequence,
            approximate = false,
        )
        val newOwnerKey = resolveOwnerGroupLocked()?.ownerKey
        val ownershipChanged = oldOwnerKey != newOwnerKey
        if (ownershipChanged) {
            _generation++
            log(TAG, INFO) { "Ownership changed: gen=$_generation, old=$oldOwnerKey, new=$newOwnerKey" }
        }
        log(TAG, VERBOSE) { "onDeviceConnected: $address ($label), groups=${buildGroupsLocked()}" }
        ConnectResult(
            previousOwnerAddresses = previousOwnerAddresses,
            ownershipChanged = ownershipChanged,
        )
    }

    suspend fun resolveDisconnect(
        address: DeviceAddr,
        receivedAtElapsedMs: Long,
    ): DisconnectResult = mutex.withLock {
        val entry = entries[address]
        if (entry == null) {
            log(TAG, VERBOSE) { "resolveDisconnect: unknown address $address" }
            return@withLock DisconnectResult(
                wasInOwnerGroup = false,
                ownerGroupBefore = emptyList(),
                ownerGroupAfter = null,
            )
        }

        // Stale event protection: if the device was reconnected more recently
        // than this disconnect event, don't remove it.
        if (entry.connectedAt > receivedAtElapsedMs && !entry.approximate) {
            log(TAG, INFO) { "resolveDisconnect: stale disconnect for $address (connected=${entry.connectedAt} > event=$receivedAtElapsedMs), ignoring" }
            return@withLock DisconnectResult(
                wasInOwnerGroup = false,
                ownerGroupBefore = resolveOwnerGroupLocked()?.entries?.map { it.address } ?: emptyList(),
                ownerGroupAfter = resolveOwnerGroupLocked()?.entries?.map { it.address },
            )
        }

        val ownerBefore = resolveOwnerGroupLocked()
        val wasInOwnerGroup = ownerBefore?.entries?.any { it.address == address } == true
        val ownerGroupBefore = ownerBefore?.entries?.map { it.address } ?: emptyList()

        entries.remove(address)

        val ownerAfter = resolveOwnerGroupLocked()
        val ownerGroupAfter = ownerAfter?.entries?.map { it.address }

        if (wasInOwnerGroup && ownerBefore?.ownerKey != ownerAfter?.ownerKey) {
            _generation++
            log(TAG, INFO) { "Ownership changed on disconnect: gen=$_generation" }
        }

        log(TAG, VERBOSE) { "resolveDisconnect: $address, wasOwner=$wasInOwnerGroup, before=$ownerGroupBefore, after=$ownerGroupAfter" }

        DisconnectResult(
            wasInOwnerGroup = wasInOwnerGroup,
            ownerGroupBefore = ownerGroupBefore,
            ownerGroupAfter = ownerGroupAfter,
        )
    }

    suspend fun ownerAddressesFor(streamId: AudioStream.Id): List<DeviceAddr> = mutex.withLock {
        val ownerGroup = resolveOwnerGroupLocked() ?: return@withLock emptyList()
        // Purely topological: return all addresses in the owner group.
        // Modules check device config (volumeObserving, volumeLock, etc.) themselves.
        ownerGroup.entries.map { it.address }
    }

    suspend fun ownershipGeneration(): Long = mutex.withLock { _generation }

    suspend fun bootstrap(devices: Collection<ManagedDevice>) = mutex.withLock {
        log(TAG, INFO) { "bootstrap: ${devices.size} devices" }
        for (device in devices) {
            if (!device.isActive) continue
            entries[device.address] = ActiveEntry(
                address = device.address,
                label = device.label,
                deviceType = device.type,
                connectedAt = device.config.lastConnected,
                sequence = 0L,
                approximate = true,
            )
        }
        log(TAG, VERBOSE) { "bootstrap: groups=${buildGroupsLocked()}" }
    }

    suspend fun reset() = mutex.withLock {
        resetInternal()
    }

    /**
     * Non-suspending reset for use in [android.app.Service.onDestroy] where no coroutine
     * context is available.  Uses [runBlocking] to acquire the mutex, ensuring no
     * concurrent readers (e.g. in-flight appScope jobs) observe a partially-cleared state.
     */
    fun resetBlocking() {
        runBlocking { mutex.withLock { resetInternal() } }
    }

    private fun resetInternal() {
        log(TAG, INFO) { "reset: clearing ${entries.size} entries" }
        entries.clear()
        _generation = 0L
    }

    private fun resolveOwnerGroupLocked(): DeviceGroup? {
        val groups = buildGroupsLocked()
        if (groups.isEmpty()) return null

        // Fresh entries take priority over approximate (bootstrap) entries.
        val freshGroups = groups.filter { group -> group.entries.any { !it.approximate } }
        val candidateGroups = freshGroups.ifEmpty { groups }

        // Owner = group with the latest connection time.
        // Speaker (PHONE_SPEAKER) is only eligible when no real device groups exist.
        val realGroups = candidateGroups.filter { group ->
            group.entries.none { it.deviceType == SourceDevice.Type.PHONE_SPEAKER }
        }
        val eligible = realGroups.ifEmpty { candidateGroups }

        return eligible.maxByOrNull { group ->
            group.entries.maxOf { it.connectedAt * 1_000_000 + it.sequence }
        }
    }

    private fun buildGroupsLocked(): List<DeviceGroup> {
        if (entries.isEmpty()) return emptyList()

        val sorted = entries.values.sortedWith(
            compareBy<ActiveEntry> { it.connectedAt }
                .thenBy { it.sequence }
        )

        val groups = mutableListOf<MutableList<ActiveEntry>>()
        for (entry in sorted) {
            val matchingGroup = groups.find { group ->
                group.any { existing ->
                    existing.label == entry.label
                            && existing.deviceType == entry.deviceType
                            && canGroup(existing, entry)
                }
            }
            if (matchingGroup != null) {
                matchingGroup.add(entry)
            } else {
                groups.add(mutableListOf(entry))
            }
        }
        return groups.map { DeviceGroup(it) }
    }

    private fun canGroup(a: ActiveEntry, b: ActiveEntry): Boolean {
        // Never group across clock domains.
        if (a.approximate != b.approximate) return false
        // Approximate entries group by label+type only (no timing info).
        if (a.approximate && b.approximate) return true
        // Fresh entries group within the 10s window.
        return kotlin.math.abs(a.connectedAt - b.connectedAt) <= GROUPING_WINDOW_MS
    }

    companion object {
        private val TAG = logTag("Monitor", "Ownership", "Registry")

        // 10s window for grouping dual-earbud devices (e.g., Samsung Buds L+R) that share a
        // label and device type.  True paired earbuds use a single BT chip and deliver both
        // ACL_CONNECTED broadcasts within 1-3s.  Two independent headphones (even with the
        // same name) are serialized by Android's A2DP profile negotiation, which consistently
        // introduces a ~10.05-10.09s gap between broadcasts (verified on Pixel 7a with two
        // AirPods: 10058ms, 10073ms, 10086ms across three trials).  The 10s boundary therefore
        // acts as a natural separator: paired earbuds group, independent devices don't.
        const val GROUPING_WINDOW_MS = 10_000L
    }
}
