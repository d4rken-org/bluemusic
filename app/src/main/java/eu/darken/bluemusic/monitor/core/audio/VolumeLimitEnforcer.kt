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
     * The levels the whole owner group permits on [streamId], or null when nothing bounds it.
     * Every apply path resolves its bounds through here, so no two of them can disagree about what
     * a grouped pair allows: the strictest ceiling and the strictest floor of the group win.
     */
    fun allowedLevels(
        streamId: AudioStream.Id,
        devices: Collection<ManagedDevice>,
        ownerAddresses: Set<DeviceAddr>,
    ): IntRange? {
        if (ownerAddresses.isEmpty()) return null

        var lower: Int? = null
        var upper: Int? = null

        for (device in devices) {
            val band = device.bandFor(streamId, ownerAddresses) ?: continue
            val levels = volumeTool.bandLevels(streamId, band)
            lower = lower?.let { maxOf(it, levels.first) } ?: levels.first
            upper = upper?.let { minOf(it, levels.last) } ?: levels.last
        }

        if (lower == null || upper == null) return null

        // Disjoint bounds across the group: the maximum wins, it is the safety bound.
        return minOf(lower, upper)..upper
    }

    /** [allowedLevels] expressed as percentages, for the sliders that display the travel. */
    fun allowedBand(
        streamId: AudioStream.Id,
        devices: Collection<ManagedDevice>,
        ownerAddresses: Set<DeviceAddr>,
    ): VolumeBand? {
        val levels = allowedLevels(streamId, devices, ownerAddresses) ?: return null
        val streamMin = volumeTool.getMinVolume(streamId)
        val streamMax = volumeTool.getMaxVolume(streamId)
        return VolumeBand(
            min = levelToPercentage(levels.first, streamMin, streamMax),
            max = levelToPercentage(levels.last, streamMin, streamMax),
        )
    }

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
        val allowed = allowedLevels(streamId, devices, ownerAddresses)
        if (allowed == null) {
            log(TAG, VERBOSE) { "No device bounds $streamId" }
            return false
        }

        val current = volumeTool.getCurrentVolume(streamId)
        if (current in allowed) {
            log(TAG, VERBOSE) { "Level $current is within $allowed for $streamId" }
            return false
        }

        val members = devices
            .filter { it.bandFor(streamId, ownerAddresses) != null }
            .map { "${it.address}/${it.label}" }
        val target = current.coerceIn(allowed.first, allowed.last)
        log(TAG) { "Correcting $streamId from $current to $target (band=$allowed, $members)" }
        return volumeTool.changeVolume(streamId = streamId, targetLevel = target, visible = false)
    }

    private fun ManagedDevice.bandFor(streamId: AudioStream.Id, ownerAddresses: Set<DeviceAddr>): VolumeBand? {
        if (!isActive) return null
        if (address !in ownerAddresses) return null
        val type = getStreamType(streamId) ?: return null
        return getVolumeBand(type)
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "Limit", "Enforcer")
    }
}
