package eu.darken.bluemusic.main.core.audio

import android.media.AudioManager
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt


@Singleton
class StreamHelper @Inject
constructor(private val audioManager: AudioManager) {

    companion object {
        private val TAG = logTag("StreamHelper")
    }
    @Volatile private var adjusting = false
    private val lastUs = HashMap<AudioStream.Id, Int>()

    fun getCurrentVolume(id: AudioStream.Id): Int {
        return audioManager.getStreamVolume(id.id)
    }

    fun getMaxVolume(streamId: AudioStream.Id): Int {
        return audioManager.getStreamMaxVolume(streamId.id)
    }

    @Synchronized private fun setVolume(streamId: AudioStream.Id, volume: Int, flags: Int) {
        log(TAG, VERBOSE) { "setVolume(streamId=$streamId, volume=$volume, flags=$flags)." }
        adjusting = true
        lastUs[streamId] = volume

        try {
            Thread.sleep(10)
        } catch (e: InterruptedException) {
            log(TAG, WARN) { e.asLog() }
            adjusting = false
            return
        }

        // https://stackoverflow.com/questions/6733163/notificationmanager-notify-fails-with-securityexception
        audioManager.setStreamVolume(streamId.id, volume, flags)

        try {
            Thread.sleep(10)
        } catch (e: InterruptedException) {
            log(TAG, WARN) { e.asLog() }
            return
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

    fun lowerByOne(streamId: AudioStream.Id, visible: Boolean): Boolean {
        val current = getCurrentVolume(streamId)
        val max = getMaxVolume(streamId)
        log(TAG, VERBOSE) { "lowerByOne(streamId=$streamId, visible=$visible): current=$current, max=$max" }

        if (current == 0) {
            log(TAG, WARN) { "Volume was at 0, can't lower by one more." }
            return false
        }

        return changeVolume(streamId, (current - 1f) / max, visible, 0)
    }

    fun increaseByOne(streamId: AudioStream.Id, visible: Boolean): Boolean {
        val current = getCurrentVolume(streamId)
        val max = getMaxVolume(streamId)
        log(TAG, VERBOSE) { "increaseByOne(streamId=$streamId, visible=$visible): current=$current, max=$max" }

        if (current == max) {
            log(TAG, WARN) { "Volume was at max, can't increase by one more." }
            return false
        }

        return changeVolume(streamId, (current + 1f) / max, visible, 0)
    }

    fun changeVolume(
            streamId: AudioStream.Id,
            percent: Float,
            visible: Boolean,
            delay: Long
    ): Boolean {
        val currentVolume = getCurrentVolume(streamId)
        val max = getMaxVolume(streamId)
        val target = (max * percent).roundToInt()

        log(TAG, VERBOSE) { "changeVolume(streamId=$streamId, percent=$percent, visible=$visible, delay=$delay)" }

        if (currentVolume != target) {
            log(
                TAG,
                DEBUG
            ) { "Adjusting volume (streamId=$streamId, target=$target, current=$currentVolume, max=$max, visible=$visible, delay=$delay)." }
            if (delay == 0L) {
                setVolume(streamId, target, if (visible) AudioManager.FLAG_SHOW_UI else 0)
            } else {
                if (currentVolume < target) {
                    for (volumeStep in currentVolume..target) {
                        setVolume(streamId, volumeStep, if (visible) AudioManager.FLAG_SHOW_UI else 0)
                        try {
                            Thread.sleep(delay)
                        } catch (e: InterruptedException) {
                            log(TAG, WARN) { e.asLog() }
                            return true
                        }

                    }
                } else {
                    for (volumeStep in currentVolume downTo target) {
                        setVolume(streamId, volumeStep, if (visible) AudioManager.FLAG_SHOW_UI else 0)
                        try {
                            Thread.sleep(delay)
                        } catch (e: InterruptedException) {
                            log(TAG, WARN) { e.asLog() }
                            return true
                        }

                    }
                }
            }
            return true
        } else {
            log(TAG, VERBOSE) { "Target volume of $target already set." }
            return false
        }
    }

}
