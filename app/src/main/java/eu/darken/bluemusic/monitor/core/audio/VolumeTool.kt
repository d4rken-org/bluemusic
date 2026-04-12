package eu.darken.bluemusic.monitor.core.audio

import android.media.AudioManager
import android.os.Build
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.time.delay
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt


fun levelToPercentage(current: Int, min: Int, max: Int): Float {
    val range = max - min
    if (range <= 0) return 0f
    return ((current - min).toFloat() / range).coerceIn(0f, 1f)
}

fun percentageToLevel(percentage: Float, min: Int, max: Int): Int {
    return (min + (max - min) * percentage).roundToInt()
}

@Singleton
class VolumeTool @Inject constructor(
    private val audioManager: AudioManager,
) {

    private data class RecentWrite(val volume: Int, val timestamp: Long)

    internal var clock: () -> Long = System::currentTimeMillis

    @Volatile private var adjusting = false
    private val lock = Mutex()
    private val lastUs = ConcurrentHashMap<AudioStream.Id, RecentWrite>()

    fun getCurrentVolume(id: AudioStream.Id): Int {
        return audioManager.getStreamVolume(id.id)
    }

    fun getMinVolume(streamId: AudioStream.Id): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return 0
        return try {
            audioManager.getStreamMinVolume(streamId.id)
        } catch (_: IllegalArgumentException) {
            // STREAM_BLUETOOTH_HANDSFREE (type 6) is not a public stream type,
            // so getStreamMinVolume rejects it. It shares the same audio path as
            // STREAM_VOICE_CALL, so use that stream's min as a proxy.
            if (streamId == AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE) {
                try {
                    audioManager.getStreamMinVolume(AudioStream.Id.STREAM_VOICE_CALL.id)
                } catch (_: IllegalArgumentException) {
                    0
                }
            } else {
                0
            }
        }
    }

    fun getMaxVolume(streamId: AudioStream.Id): Int {
        return audioManager.getStreamMaxVolume(streamId.id)
    }

    private suspend fun setVolume(streamId: AudioStream.Id, volume: Int, flags: Int) = lock.withLock {
        log(TAG, VERBOSE) { "setVolume(streamId=$streamId, volume=$volume, flags=$flags)." }
        try {
            adjusting = true
            val now = clock()
            val write = RecentWrite(volume, now)
            lastUs[streamId] = write
            mirroredPeer(streamId)?.let { lastUs[it] = write }

            delay(10)

            // https://stackoverflow.com/questions/6733163/notificationmanager-notify-fails-with-securityexception
            audioManager.setStreamVolume(streamId.id, volume, flags)

            delay(10)
        } finally {
            adjusting = false
        }
    }

    fun wasUs(id: AudioStream.Id, volume: Int): Boolean {
        if (adjusting) return true
        val entry = lastUs[id] ?: return false
        return entry.volume == volume && (clock() - entry.timestamp) < WRITE_TTL_MS
    }

    private fun mirroredPeer(id: AudioStream.Id): AudioStream.Id? = when (id) {
        AudioStream.Id.STREAM_VOICE_CALL -> AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE
        AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE -> AudioStream.Id.STREAM_VOICE_CALL
        else -> null
    }

    fun getVolumePercentage(streamId: AudioStream.Id): Float {
        return levelToPercentage(getCurrentVolume(streamId), getMinVolume(streamId), getMaxVolume(streamId))
    }

    suspend fun lowerByOne(streamId: AudioStream.Id, visible: Boolean): Boolean {
        val current = getCurrentVolume(streamId)
        val min = getMinVolume(streamId)
        val max = getMaxVolume(streamId)
        log(TAG, VERBOSE) { "lowerByOne(streamId=$streamId, visible=$visible): current=$current, min=$min, max=$max" }

        if (current <= min) {
            log(TAG, WARN) { "Volume was at min ($min), can't lower by one more." }
            return false
        }

        return changeVolume(streamId, levelToPercentage(current - 1, min, max), visible)
    }

    suspend fun increaseByOne(streamId: AudioStream.Id, visible: Boolean): Boolean {
        val current = getCurrentVolume(streamId)
        val min = getMinVolume(streamId)
        val max = getMaxVolume(streamId)
        log(TAG, VERBOSE) { "increaseByOne(streamId=$streamId, visible=$visible): current=$current, min=$min, max=$max" }

        if (current >= max) {
            log(TAG, WARN) { "Volume was at max ($max), can't increase by one more." }
            return false
        }

        return changeVolume(streamId, levelToPercentage(current + 1, min, max), visible)
    }

    suspend fun changeVolume(
        streamId: AudioStream.Id,
        percent: Float,
        visible: Boolean = false,
        delay: Duration = Duration.ZERO,
    ): Boolean {
        log(TAG, VERBOSE) { "changeVolume(streamId=$streamId, percent=$percent, visible=$visible, delay=$delay)" }
        val target = percentageToLevel(percent, getMinVolume(streamId), getMaxVolume(streamId))
        return changeVolume(
            streamId = streamId,
            targetLevel = target,
            visible = visible,
            delay = delay
        )
    }

    suspend fun changeVolume(
        streamId: AudioStream.Id,
        targetLevel: Int,
        visible: Boolean = false,
        delay: Duration = Duration.ZERO,
    ): Boolean {
        log(TAG, VERBOSE) { "changeVolume(streamId=$streamId, level=$targetLevel, visible=$visible, delay=$delay)" }

        val max = getMaxVolume(streamId)
        if (targetLevel > max) {
            log(TAG, WARN) { "Target volume of $targetLevel exceeds max of $max." }
            return false
        }

        val currentLevel = getCurrentVolume(streamId)
        if (currentLevel == targetLevel) {
            lastUs[streamId] = RecentWrite(targetLevel, clock())
            log(TAG, VERBOSE) { "Target volume of $targetLevel already set." }
            return false
        }

        log(TAG, DEBUG) {
            "Adjusting volume (streamId=$streamId, targetLevel=$targetLevel, current=$currentLevel, max=$max, visible=$visible, delay=$delay)."
        }
        if (delay == Duration.ZERO) {
            setVolume(streamId, targetLevel, if (visible) AudioManager.FLAG_SHOW_UI else 0)
        } else {
            if (currentLevel < targetLevel) {
                for (volumeStep in currentLevel..targetLevel) {
                    setVolume(streamId, volumeStep, if (visible) AudioManager.FLAG_SHOW_UI else 0)

                    delay(delay)
                }
            } else {
                for (volumeStep in currentLevel downTo targetLevel) {
                    setVolume(streamId, volumeStep, if (visible) AudioManager.FLAG_SHOW_UI else 0)

                    delay(delay)
                }
            }
        }
        return true
    }

    companion object {
        private val TAG = logTag("Audio", "StreamHelper")
        private const val WRITE_TTL_MS = 500L
    }
}
