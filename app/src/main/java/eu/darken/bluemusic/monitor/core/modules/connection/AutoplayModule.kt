package eu.darken.bluemusic.monitor.core.modules.connection

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.EventModule
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoplayModule @Inject constructor(
    private val audioManager: AudioManager,
    private val devicesSettings: DevicesSettings,
) : EventModule {

    override val priority: Int
        get() = 20

    override suspend fun handle(event: DeviceEvent) {
        if (event !is DeviceEvent.Connected) return

        val device = event.device
        if (!device.autoplay) return
        log(TAG) { "Autoplay enabled (playing=${audioManager.isMusicActive})." }

        val autoplayKeycodes = devicesSettings.autoplayKeycodes.value()
        if (autoplayKeycodes.isEmpty()) {
            log(TAG, WARN) { "Autoplay enabled but no keycodes configured" }
            return
        }

        // Send all keycodes in sequence
        for (keycode in autoplayKeycodes) {
            val maxTries = when (keycode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> 1 // Don't toggle PLAY_PAUSE
                else -> 5
            }
            var currentTries = 0

            // Some Xiaomi devices always have isMusicActive=true, so we have to send the command at least once
            while (true) {
                log(TAG) { "Sending up+down KeyEvent: $keycode" }
                val eventTime = SystemClock.uptimeMillis()
                audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keycode, 0))
                audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime + 50, eventTime + 50, KeyEvent.ACTION_UP, keycode, 0))

                currentTries++

                delay(500)

                if (audioManager.isMusicActive) {
                    log(TAG, VERBOSE) { "Music is playing (tries=$currentTries), job done :)." }
                    break
                } else if (currentTries == maxTries) {
                    log(TAG, WARN) {
                        "After $currentTries tries, still getting isMusicActive=${audioManager.isMusicActive}, moving to next keycode."
                    }
                    break
                } else {
                    log(TAG, VERBOSE) { "Music isn't playing, retrying (tries=$currentTries). :|" }
                }
            }

            // If music is already playing, stop sending more keycodes
            if (audioManager.isMusicActive) break
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: AutoplayModule): EventModule
    }

    companion object {
        private val TAG = logTag("Monitor", "Autoplay", "Module")
    }
}
