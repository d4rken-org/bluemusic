package eu.darken.bluemusic.monitor.core.audio

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val volumeTool: VolumeTool,
) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val handler by lazy {
        val handlerThread = HandlerThread("VolumeObserver")
        handlerThread.start()
        val looper = handlerThread.looper
        Handler(looper)
    }

    private val volumesCache = ConcurrentHashMap<AudioStream.Id, Int>()

    val volumes: Flow<VolumeEvent> = callbackFlow {
        AudioStream.Id.entries.forEach { id ->
            val volume = volumeTool.getCurrentVolume(id)
            volumesCache[id] = volume
        }

        val observer = object : ContentObserver(handler) {

            override fun deliverSelfNotifications(): Boolean = true

            override fun onChange(selfChange: Boolean) {
                log(TAG, VERBOSE) { "Change detected (selfChange=$selfChange)" }
                AudioStream.Id.entries.forEach { id ->
                    val newVolume = volumeTool.getCurrentVolume(id)
                    val oldVolume = volumesCache[id] ?: -1
                    if (newVolume != oldVolume) {
                        val isSelf = volumeTool.wasUs(id, newVolume)
                        log(TAG) { "Volume changed (type=$id, old=$oldVolume, new=$newVolume, self=$isSelf)" }
                        volumesCache[id] = newVolume
                        val change = VolumeEvent(
                            streamId = id,
                            oldVolume = oldVolume,
                            newVolume = newVolume,
                            self = isSelf
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
    }.shareIn(
        scope = appScope,
        started = SharingStarted.WhileSubscribed(),
        replay = 0,
    )

    companion object {
        private val TAG = logTag("Monitor", "VolumeObserver")
    }
}