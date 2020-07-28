package eu.darken.bluemusic.util

import android.os.PowerManager
import com.bugsnag.android.Bugsnag
import eu.darken.bluemusic.AppComponent
import timber.log.Timber
import javax.inject.Inject

@AppComponent.Scope
class WakelockMan @Inject constructor(powerManager: PowerManager) {

    @Suppress("DEPRECATION")
    private val wakelock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "bvm:keepawake"
    )

    fun tryAquire() {
        if (wakelock.isHeld) {
            Timber.d("tryAquire(): Wakelock already held.")
            return
        } else {
            try {
                wakelock.acquire(3 * 60 * 60 * 1000)
                Timber.d("tryAquire(): Wakelock acquired (isHeld=%b)", wakelock.isHeld)
            } catch (e: Exception) {
                Timber.e(e, "Failed to acquire wakelock.")
                Bugsnag.notify(e)
            }
        }
    }

    fun tryRelease() {
        if (wakelock.isHeld) {
            try {
                wakelock.release()
            } catch (e: Exception) {
                Timber.e(e, "Failed to release wakelock.")
                Bugsnag.notify(e)
            }
            Timber.d("tryRelease(): Wakelock released (isHeld=%b)", wakelock.isHeld)
        } else {
            Timber.d("tryRelease(): Wakelock is not acquired.")
        }
    }
}
