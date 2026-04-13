package eu.darken.bluemusic.monitor.core.modules.volume

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks which audio streams are currently being adjusted by connection volume modules.
 *
 * When a [BaseVolumeModule] is ramping a stream (setInitial + monitor), it suppresses
 * observation for that stream and its mirrored peer (VOICE_CALL ↔ BLUETOOTH_HANDSFREE).
 * [VolumeUpdateModule] checks this gate before persisting observed volume changes,
 * preventing cross-device contamination during device transitions.
 */
@Singleton
class VolumeObservationGate @Inject constructor() {

    private val suppressed = ConcurrentHashMap.newKeySet<AudioStream.Id>()

    fun suppress(streamId: AudioStream.Id) {
        suppressed.add(streamId)
        mirroredPeer(streamId)?.let { suppressed.add(it) }
        log(TAG, VERBOSE) { "Suppressed observation for $streamId (suppressed=$suppressed)" }
    }

    fun unsuppress(streamId: AudioStream.Id) {
        suppressed.remove(streamId)
        mirroredPeer(streamId)?.let { suppressed.remove(it) }
        log(TAG, VERBOSE) { "Unsuppressed observation for $streamId (suppressed=$suppressed)" }
    }

    fun isSuppressed(streamId: AudioStream.Id): Boolean = streamId in suppressed

    private fun mirroredPeer(id: AudioStream.Id): AudioStream.Id? = when (id) {
        AudioStream.Id.STREAM_VOICE_CALL -> AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE
        AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE -> AudioStream.Id.STREAM_VOICE_CALL
        else -> null
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "ObservationGate")
    }
}
