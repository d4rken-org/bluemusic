package eu.darken.bluemusic.monitor.core.audio

import android.media.AudioManager
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt


@Singleton
class StreamHelper @Inject constructor(private val audioManager: AudioManager) {

    @Volatile private var adjusting = false
    private val lock = Mutex()
    private val lastUs = HashMap<AudioStream.Id, Int>()

    fun getCurrentVolume(id: AudioStream.Id): Int {
        return audioManager.getStreamVolume(id.id)
    }

    fun getMaxVolume(streamId: AudioStream.Id): Int {
        return audioManager.getStreamMaxVolume(streamId.id)
    }

    suspend fun setVolume(streamId: AudioStream.Id, volume: Int, flags: Int) = lock.withLock {
        log(TAG, VERBOSE) { "setVolume(streamId=$streamId, volume=$volume, flags=$flags)." }
        try {
            adjusting = true
            lastUs[streamId] = volume

            delay(10)

            // https://stackoverflow.com/questions/6733163/notificationmanager-notify-fails-with-securityexception
            audioManager.setStreamVolume(streamId.id, volume, flags)

            delay(10)
        } finally {
            adjusting = false
        }
    }

    fun wasUs(id: AudioStream.Id, volume: Int): Boolean {
        return lastUs.containsKey(id) && lastUs[id] == volume || adjusting
    }

    fun getVolumePercentage(streamId: AudioStream.Id): Float {
        return audioManager.getStreamVolume(streamId.id).toFloat() / audioManager.getStreamMaxVolume(streamId.id)
    }

    suspend fun lowerByOne(streamId: AudioStream.Id, visible: Boolean): Boolean {
        val current = getCurrentVolume(streamId)
        val max = getMaxVolume(streamId)
        log(TAG, VERBOSE) { "lowerByOne(streamId=$streamId, visible=$visible): current=$current, max=$max" }

        if (current == 0) {
            log(TAG, WARN) { "Volume was at 0, can't lower by one more." }
            return false
        }

        return changeVolume(streamId, (current - 1f) / max, visible)
    }

    suspend fun increaseByOne(streamId: AudioStream.Id, visible: Boolean): Boolean {
        val current = getCurrentVolume(streamId)
        val max = getMaxVolume(streamId)
        log(TAG, VERBOSE) { "increaseByOne(streamId=$streamId, visible=$visible): current=$current, max=$max" }

        if (current == max) {
            log(TAG, WARN) { "Volume was at max, can't increase by one more." }
            return false
        }

        return changeVolume(streamId, (current + 1f) / max, visible)
    }

    suspend fun changeVolume(
        streamId: AudioStream.Id,
        percent: Float,
        visible: Boolean = false,
        delay: Duration = Duration.ZERO,
    ): Boolean {
        val currentVolume = getCurrentVolume(streamId)
        val max = getMaxVolume(streamId)
        val target = (max * percent).roundToInt()

        log(TAG, VERBOSE) { "changeVolume(streamId=$streamId, percent=$percent, visible=$visible, delay=$delay)" }

        if (currentVolume == target) {
            log(TAG, VERBOSE) { "Target volume of $target already set." }
            return false
        }

        log(TAG, DEBUG) {
            "Adjusting volume (streamId=$streamId, target=$target, current=$currentVolume, max=$max, visible=$visible, delay=$delay)."
        }
        if (delay == Duration.ZERO) {
            setVolume(streamId, target, if (visible) AudioManager.FLAG_SHOW_UI else 0)
        } else {
            if (currentVolume < target) {
                for (volumeStep in currentVolume..target) {
                    setVolume(streamId, volumeStep, if (visible) AudioManager.FLAG_SHOW_UI else 0)

                    delay(delay)
                }
            } else {
                for (volumeStep in currentVolume downTo target) {
                    setVolume(streamId, volumeStep, if (visible) AudioManager.FLAG_SHOW_UI else 0)

                    delay(delay)
                }
            }
        }
        return true
    }

    companion object {
        private val TAG = logTag("StreamHelper")
    }
}
