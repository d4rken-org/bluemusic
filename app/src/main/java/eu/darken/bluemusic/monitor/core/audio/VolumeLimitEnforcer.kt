package eu.darken.bluemusic.monitor.core.audio

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.ManagedDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeLimitEnforcer @Inject constructor(
    private val volumeTool: VolumeTool,
) {

    /**
     * One decision per stream: the owner group is resolved into a single band and at most one
     * hardware write. Iterating per device would issue several writes to the same hardware stream
     * and make the outcome depend on repository iteration order.
     *
     * Returns true when the level had to be corrected.
     */
    suspend fun enforce(
        streamId: AudioStream.Id,
        devices: Collection<ManagedDevice>,
        ownerAddresses: Set<DeviceAddr>,
    ): Boolean {
        if (ownerAddresses.isEmpty()) return false

        var lower: Int? = null
        var upper: Int? = null
        val members = mutableListOf<String>()

        for (device in devices) {
            if (!device.isActive) continue
            if (device.address !in ownerAddresses) continue
            val type = device.getStreamType(streamId) ?: continue
            val band = device.getVolumeBand(type) ?: continue

            val levels = volumeTool.bandLevels(streamId, band)
            lower = lower?.let { maxOf(it, levels.first) } ?: levels.first
            upper = upper?.let { minOf(it, levels.last) } ?: levels.last
            members.add("${device.address}/${device.label}")
        }

        if (lower == null || upper == null) {
            log(TAG, VERBOSE) { "No device bounds $streamId" }
            return false
        }

        // Disjoint bounds across the group: the maximum wins, it is the safety bound.
        val effectiveLower = minOf(lower, upper)

        val current = volumeTool.getCurrentVolume(streamId)
        if (current in effectiveLower..upper) {
            log(TAG, VERBOSE) { "Level $current is within $effectiveLower..$upper for $streamId" }
            return false
        }

        val target = current.coerceIn(effectiveLower, upper)
        log(TAG) { "Correcting $streamId from $current to $target (band=$effectiveLower..$upper, $members)" }
        return volumeTool.changeVolume(streamId = streamId, targetLevel = target, visible = false)
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "Limit", "Enforcer")
    }
}
