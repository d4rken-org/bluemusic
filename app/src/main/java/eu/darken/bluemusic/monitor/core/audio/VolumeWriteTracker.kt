package eu.darken.bluemusic.monitor.core.audio

import java.util.concurrent.ConcurrentHashMap

internal class VolumeWriteTracker(
    private val clock: () -> Long,
) {

    private data class RecentWrite(val volume: Int, val timestamp: Long)

    @Volatile private var adjustingStream: AudioStream.Id? = null

    // Used by monitor loops that need to remember the last target we intended to hold.
    private val recentTargets = ConcurrentHashMap<AudioStream.Id, RecentWrite>()
    // Used by VolumeObserver to classify a matching observed change exactly once.
    private val pendingObserverWrites = ConcurrentHashMap<AudioStream.Id, RecentWrite>()

    fun onWriteStarted(streamId: AudioStream.Id, volume: Int) {
        adjustingStream = streamId
        val now = clock()
        rememberWrite(recentTargets, streamId, volume, mirror = true, timestamp = now)
        rememberWrite(pendingObserverWrites, streamId, volume, mirror = true, timestamp = now)
    }

    fun onWriteFinished() {
        adjustingStream = null
    }

    fun rememberCurrentTarget(streamId: AudioStream.Id, volume: Int) {
        rememberWrite(recentTargets, streamId, volume, mirror = false, timestamp = clock())
    }

    fun hasRecentTarget(id: AudioStream.Id, volume: Int): Boolean {
        return hasFreshWrite(recentTargets, id, volume)
    }

    fun wasUs(id: AudioStream.Id, volume: Int): Boolean {
        if (consumePendingWrite(id, volume)) return true

        val currentlyAdjusting = adjustingStream
        if (currentlyAdjusting != null) {
            if (currentlyAdjusting == id || mirroredPeer(currentlyAdjusting) == id) {
                return hasFreshWrite(recentTargets, id, volume)
            }
        }
        return false
    }

    private fun rememberWrite(
        map: ConcurrentHashMap<AudioStream.Id, RecentWrite>,
        streamId: AudioStream.Id,
        volume: Int,
        mirror: Boolean,
        timestamp: Long,
    ) {
        val write = RecentWrite(volume, timestamp)
        map[streamId] = write
        if (mirror) {
            mirroredPeer(streamId)?.let { map[it] = write }
        }
    }

    private fun hasFreshWrite(
        map: ConcurrentHashMap<AudioStream.Id, RecentWrite>,
        id: AudioStream.Id,
        volume: Int,
    ): Boolean {
        val entry = map[id] ?: return false
        val now = clock()
        if (entry.volume != volume) return false
        if ((now - entry.timestamp) >= WRITE_TTL_MS) {
            map.remove(id, entry)
            return false
        }
        return true
    }

    private fun consumePendingWrite(id: AudioStream.Id, volume: Int): Boolean {
        val entry = pendingObserverWrites[id] ?: return false
        val now = clock()
        if ((now - entry.timestamp) >= WRITE_TTL_MS) {
            pendingObserverWrites.remove(id, entry)
            return false
        }
        if (entry.volume != volume) {
            val currentlyAdjusting = adjustingStream
            if (currentlyAdjusting == null || (currentlyAdjusting != id && mirroredPeer(currentlyAdjusting) != id)) {
                pendingObserverWrites.remove(id, entry)
            }
            return false
        }
        pendingObserverWrites.remove(id, entry)
        return true
    }

    private fun mirroredPeer(id: AudioStream.Id): AudioStream.Id? = when (id) {
        AudioStream.Id.STREAM_VOICE_CALL -> AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE
        AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE -> AudioStream.Id.STREAM_VOICE_CALL
        else -> null
    }

    companion object {
        private const val WRITE_TTL_MS = 2000L
    }
}
