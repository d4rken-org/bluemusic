package eu.darken.bluemusic.monitor.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
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
class RingerModeObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ringerTool: RingerTool,
) {

    private var cachedMode: RingerMode? = null

    val ringerMode: Flow<RingerModeEvent> = callbackFlow {
        // Get initial state
        val initialMode = ringerTool.getCurrentRingerMode()
        cachedMode = initialMode

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                    val newMode = ringerTool.getCurrentRingerMode()
                    val oldMode = cachedMode ?: newMode

                    if (newMode != oldMode) {
                        log(TAG, VERBOSE) { "Ringer mode changed (old=$oldMode, new=$newMode)" }
                        cachedMode = newMode

                        val event = RingerModeEvent(
                            oldMode = oldMode,
                            newMode = newMode
                        )
                        trySendBlocking(event)
                    }
                }
            }
        }

        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        context.registerReceiver(receiver, filter)
        log(TAG) { "Now listening for ringer mode change events" }

        awaitClose {
            log(TAG) { "Stopping listening for ringer mode change events" }
            context.unregisterReceiver(receiver)
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "RingerModeObserver")
    }
}