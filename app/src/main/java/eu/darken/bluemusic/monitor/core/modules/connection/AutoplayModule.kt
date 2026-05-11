package eu.darken.bluemusic.monitor.core.modules.connection

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.SettlePolicy
import kotlinx.coroutines.delay
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoplayModule @Inject constructor(
    private val audioManager: AudioManager,
) : ConnectionModule {

    override val tag: String
        get() = TAG

    override val priority: Int = 8

    private fun isApplicable(event: DeviceEvent): Boolean =
        event is DeviceEvent.Connected
            && event.device.autoplay
            && event.device.autoplayKeycodes.isNotEmpty()

    override fun appliesTo(event: DeviceEvent): Boolean = isApplicable(event)

    /**
     * Wait an extra [APP_READY_EXTRA_DELAY] beyond the dispatcher's settle barrier when
     * [event] is Connected and the user has configured a non-zero actionDelay. Previously
     * Autoplay accidentally inherited cushion from earlier phases' stacked delays; with
     * the per-module delays collapsed into a single barrier that cushion is gone, so the
     * launched media app may not be foreground-ready to receive the media key when the
     * barrier ends. Users who explicitly configured actionDelay=0 opt into no waiting at
     * all and don't get the extra wait either.
     */
    override fun settlePolicy(event: DeviceEvent): SettlePolicy {
        if (event !is DeviceEvent.Connected) return SettlePolicy.AfterDeviceSettle
        return if (event.device.actionDelay > Duration.ZERO) {
            SettlePolicy.AfterDeviceSettlePlus(APP_READY_EXTRA_DELAY)
        } else {
            SettlePolicy.AfterDeviceSettle
        }
    }

    override suspend fun handle(event: DeviceEvent) {
        if (!isApplicable(event)) return

        val device = event.device
        log(TAG) { "Autoplay enabled (playing=${audioManager.isMusicActive})." }

        val autoplayKeycodes = device.autoplayKeycodes

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
                    log(TAG, VERBOSE) { "Music is playing (tries=$currentTries), continuing to next keycode." }
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
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: AutoplayModule): ConnectionModule
    }

    companion object {
        private val TAG = logTag("Monitor", "Autoplay", "Module")

        // Approximate time for a launched media app to be foreground-ready to receive
        // KEYCODE_MEDIA_PLAY. Bump if "Plays 1/3 of the time" reports return.
        internal val APP_READY_EXTRA_DELAY: Duration = Duration.ofSeconds(2)
    }
}
