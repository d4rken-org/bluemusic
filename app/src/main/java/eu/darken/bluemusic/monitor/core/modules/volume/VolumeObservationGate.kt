package eu.darken.bluemusic.monitor.core.modules.volume

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks which audio streams are currently being adjusted by connection volume modules.
 *
 * When a [BaseVolumeModule][eu.darken.bluemusic.monitor.core.modules.connection.BaseVolumeModule]
 * is ramping a stream (setInitial + monitor), it suppresses observation for that stream and its
 * mirrored peer (VOICE_CALL ↔ BLUETOOTH_HANDSFREE).
 * [VolumeUpdateModule] checks this gate before persisting observed volume changes,
 * preventing cross-device contamination during device transitions.
 *
 * Uses ref-counted tokens so concurrent module runs on the same stream don't unsuppress
 * prematurely — the stream stays suppressed until ALL suppressors release their tokens.
 */
@Singleton
class VolumeObservationGate @Inject constructor() {

    private val refCounts = ConcurrentHashMap<AudioStream.Id, AtomicInteger>()
    private val tokenCounter = AtomicLong(0)

    data class SuppressionToken(
        val id: Long,
        val streamIds: Set<AudioStream.Id>,
    )

    fun suppress(streamId: AudioStream.Id): SuppressionToken {
        val streams = buildSet {
            add(streamId)
            mirroredPeer(streamId)?.let { add(it) }
        }
        for (s in streams) {
            refCounts.getOrPut(s) { AtomicInteger(0) }.incrementAndGet()
        }
        val token = SuppressionToken(tokenCounter.getAndIncrement(), streams)
        log(TAG, VERBOSE) { "suppress($streamId) → token=${token.id}, refs=${refSnapshot()}" }
        return token
    }

    fun unsuppress(token: SuppressionToken) {
        for (s in token.streamIds) {
            val ref = refCounts[s]
            if (ref != null) {
                val newVal = ref.decrementAndGet()
                if (newVal <= 0) {
                    refCounts.remove(s)
                }
            }
        }
        log(TAG, VERBOSE) { "unsuppress(token=${token.id}), refs=${refSnapshot()}" }
    }

    fun isSuppressed(streamId: AudioStream.Id): Boolean {
        val count = refCounts[streamId]?.get() ?: 0
        return count > 0
    }

    private fun refSnapshot(): Map<AudioStream.Id, Int> =
        refCounts.mapValues { it.value.get() }.filter { it.value > 0 }

    private fun mirroredPeer(id: AudioStream.Id): AudioStream.Id? = when (id) {
        AudioStream.Id.STREAM_VOICE_CALL -> AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE
        AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE -> AudioStream.Id.STREAM_VOICE_CALL
        else -> null
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "ObservationGate")
    }
}
