package eu.darken.bluemusic.main.core.audio

import android.media.AudioManager
import eu.darken.bluemusic.AppComponent
import timber.log.Timber
import java.util.*
import javax.inject.Inject


@AppComponent.Scope
class StreamHelper @Inject
constructor(private val audioManager: AudioManager) {
    @Volatile private var adjusting = false
    private val lastUs = HashMap<AudioStream.Id, Int>()

    fun getCurrentVolume(id: AudioStream.Id): Int {
        return audioManager.getStreamVolume(id.id)
    }

    fun getMaxVolume(streamId: AudioStream.Id): Int {
        return audioManager.getStreamMaxVolume(streamId.id)
    }

    @Synchronized private fun setVolume(streamId: AudioStream.Id, volume: Int, flags: Int) {
        Timber.v("setVolume(streamId=%s, volume=%d, flags=%d).", streamId, volume, flags)
        adjusting = true
        lastUs[streamId] = volume

        try {
            Thread.sleep(10)
        } catch (e: InterruptedException) {
            Timber.w(e)
            adjusting = false
            return
        }

        // https://stackoverflow.com/questions/6733163/notificationmanager-notify-fails-with-securityexception
        audioManager.setStreamVolume(streamId.id, volume, flags)

        try {
            Thread.sleep(10)
        } catch (e: InterruptedException) {
            Timber.w(e)
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

    fun changeVolume(streamId: AudioStream.Id, percent: Float, visible: Boolean, delay: Long): Boolean {
        val currentVolume = getCurrentVolume(streamId)
        val max = getMaxVolume(streamId)
        val target = Math.round(max * percent)

        Timber.v("changeVolume(streamId=%s, percent=%f, visible=%b, delay=%d)", streamId, percent, visible, delay)

        if (currentVolume != target) {
            Timber.d("Adjusting volume (streamId=%s, target=%d, current=%d, max=%d, visible=%b, delay=%d).", streamId, target, currentVolume, max, visible, delay)
            if (delay == 0L) {
                setVolume(streamId, target, if (visible) AudioManager.FLAG_SHOW_UI else 0)
            } else {
                if (currentVolume < target) {
                    for (volumeStep in currentVolume..target) {
                        setVolume(streamId, volumeStep, if (visible) AudioManager.FLAG_SHOW_UI else 0)
                        try {
                            Thread.sleep(delay)
                        } catch (e: InterruptedException) {
                            Timber.w(e)
                            return true
                        }

                    }
                } else {
                    for (volumeStep in currentVolume downTo target) {
                        setVolume(streamId, volumeStep, if (visible) AudioManager.FLAG_SHOW_UI else 0)
                        try {
                            Thread.sleep(delay)
                        } catch (e: InterruptedException) {
                            Timber.w(e)
                            return true
                        }

                    }
                }
            }
            return true
        } else {
            Timber.v("Target volume of %d already set.", target)
            return false
        }
    }

}
