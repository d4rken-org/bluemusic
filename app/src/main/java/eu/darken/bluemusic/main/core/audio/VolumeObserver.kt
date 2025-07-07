package eu.darken.bluemusic.main.core.audio

import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class VolumeObserver @Inject constructor(
    private val streamHelper: StreamHelper
) : ContentObserver(
    run {
        val handlerThread = HandlerThread("VolumeObserver")
        handlerThread.start()
        val looper = handlerThread.looper
        Handler(looper)
    }
) {

    interface Callback {
        fun onVolumeChanged(streamId: AudioStream.Id, volume: Int)
    }

    private val callbacks = mutableMapOf<AudioStream.Id, Callback>()
    private val volumes = mutableMapOf<AudioStream.Id, Int>()

    fun addCallback(id: AudioStream.Id, callback: Callback) {
        callbacks[id] = callback
        val volume = streamHelper.getCurrentVolume(id)
        volumes[id] = volume
    }

    override fun deliverSelfNotifications(): Boolean {
        return false
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        log(TAG, VERBOSE) { "Change detected." }
        for ((id, callback) in callbacks) {
            val newVolume = streamHelper.getCurrentVolume(id)
            val oldVolume = volumes[id] ?: -1
            if (newVolume != oldVolume) {
                log(TAG, VERBOSE) { "Volume changed (type=$id, old=$oldVolume, new=$newVolume)" }
                volumes[id] = newVolume
                callback.onVolumeChanged(id, newVolume)
            }
        }
    }

    companion object {
        private val TAG = logTag("VolumeObserver")
    }
}