package eu.darken.bluemusic.main.core.service.modules.events

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.data.device.ManagedDevice
import eu.darken.bluemusic.main.core.service.modules.EventModule
import eu.darken.bluemusic.settings.core.Settings
import timber.log.Timber
import javax.inject.Inject

@AppComponent.Scope
class AutoplayModule @Inject constructor(
        private val audioManager: AudioManager,
        private val settings: Settings
) : EventModule() {

    override fun getPriority(): Int = 20

    override fun handle(device: ManagedDevice, event: SourceDevice.Event) {
        if (event.type != SourceDevice.Event.Type.CONNECTED) return
        if (!device.autoplay) return
        Timber.d("Autoplay enabled (playing=%b).", audioManager.isMusicActive)

        val autoplayKeycode = settings.autoplayKeycode

        val maxTries = when (autoplayKeycode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> 5
            else -> 1 // Don't toggle PLAY_PAUSE
        }
        var currentTries = 0
        // Some Xiaomi devices always have isMusicActive=true, so we have to send the command at least once
        while (true) {
            Timber.d("Sending up+down KeyEvent: %d", autoplayKeycode)
            val eventTime = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, autoplayKeycode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime + 50, eventTime + 50, KeyEvent.ACTION_UP, autoplayKeycode, 0))

            currentTries++

            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                Timber.w(e)
                return
            }

            if (audioManager.isMusicActive) {
                Timber.v("Music is playing (tries=%d), job done :).", currentTries)
                break
            } else if (currentTries == maxTries) {
                Timber.w("After %d tries, still getting isMusicActive=%b, giving up.", currentTries, audioManager.isMusicActive)
                break
            } else {
                Timber.v("Music isn't playing, retrying (tries=%d). :|", currentTries)
            }
        }
    }
}
