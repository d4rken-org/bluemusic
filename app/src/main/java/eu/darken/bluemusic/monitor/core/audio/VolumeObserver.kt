package eu.darken.bluemusic.monitor.core.audio

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val volumeTool: VolumeTool,
) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val handler by lazy {
        val handlerThread = HandlerThread("VolumeObserver")
        handlerThread.start()
        val looper = handlerThread.looper
        Handler(looper)
    }

    private val volumesCache = mutableMapOf<AudioStream.Id, Int>()

    val volumes: Flow<VolumeEvent> = callbackFlow {
        AudioStream.Id.entries.forEach { id ->
            val volume = volumeTool.getCurrentVolume(id)
            volumesCache[id] = volume
        }

        val observer = object : ContentObserver(handler) {

            override fun deliverSelfNotifications(): Boolean = true

            override fun onChange(selfChange: Boolean) {
                log(TAG, VERBOSE) { "Change detected (self=$selfChange)" }
                AudioStream.Id.entries.forEach { id ->
                    val newVolume = volumeTool.getCurrentVolume(id)
                    val oldVolume = volumesCache[id] ?: -1
                    if (newVolume != oldVolume) {
                        log(TAG) { "Volume changed (type=$id, old=$oldVolume, new=$newVolume)" }
                        volumesCache[id] = newVolume
                        val change = VolumeEvent(
                            streamId = id,
                            oldVolume = oldVolume,
                            newVolume = newVolume,
                            self = selfChange
                        )
                        trySendBlocking(change)
                    }
                }
            }
        }

        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        log(TAG) { "Now listening for volume change events" }

        awaitClose {
            log(TAG) { "Stopping listening for volume change events" }
            contentResolver.unregisterContentObserver(observer)
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "VolumeObserver")
    }
}