package eu.darken.bluemusic.main.core.service.modules.events

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.main.core.database.ManagedDevice
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent
import eu.darken.bluemusic.main.core.service.modules.EventModule
import eu.darken.bluemusic.settings.core.Settings
import timber.log.Timber
import javax.inject.Inject

@BlueMusicServiceComponent.Scope
class AutoplayModule @Inject constructor(
        private val audioManager: AudioManager,
        private val settings: Settings
) : EventModule() {

    override fun getPriority(): Int = 20

    override fun handle(device: ManagedDevice, event: SourceDevice.Event) {
        if (event.type != SourceDevice.Event.Type.CONNECTED) return
        if (!device.autoPlay) return
        Timber.d("Autoplay enabled (playing=%b).", audioManager.isMusicActive)

        val autoplayKeycode = settings.autoplayKeycode

        var tries = 0

        // Some Xiaomi devices always have isMusicActive=true, so we have to send the command at least once
        while (true) {
            Timber.d("Sending up+down KeyEvent: %d", autoplayKeycode)
            val eventTime = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, autoplayKeycode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime + 50, eventTime + 50, KeyEvent.ACTION_UP, autoplayKeycode, 0))

            tries++
            Thread.sleep(500)

            if (audioManager.isMusicActive) {
                Timber.v("Music is playing, job done :).")
                break
            } else if (tries == 4) {
                Timber.w("After %d tries, still getting isMusicActive=%b, giving up.", tries, audioManager.isMusicActive)
                break
            } else {
                Timber.v("Music isn't playing, retrying (tries=%d). :|", tries)
            }
        }
    }
}
