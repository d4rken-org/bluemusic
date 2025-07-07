package eu.darken.bluemusic.common

import android.os.PowerManager
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import eu.darken.bluemusic.common.debug.logging.asLog
import javax.inject.Inject

import javax.inject.Singleton

@Singleton
class WakelockMan @Inject constructor(powerManager: PowerManager) {

    companion object {
        private val TAG = logTag("WakelockMan")
    }

    @Suppress("DEPRECATION")
    private val wakelock = powerManager.newWakeLock(
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
        "bvm:keepawake"
    )

    fun tryAquire() {
        if (wakelock.isHeld) {
            log(TAG) { "tryAquire(): Wakelock already held." }
            return
        } else {
            try {
                wakelock.acquire(3 * 60 * 60 * 1000)
                log(TAG) { "tryAquire(): Wakelock acquired (isHeld=${wakelock.isHeld})" }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to acquire wakelock: ${e.asLog()}" }
            }
        }
    }

    fun tryRelease() {
        if (wakelock.isHeld) {
            try {
                wakelock.release()
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to release wakelock: ${e.asLog()}" }
            }
            log(TAG) { "tryRelease(): Wakelock released (isHeld=${wakelock.isHeld})" }
        } else {
            log(TAG) { "tryRelease(): Wakelock is not acquired." }
        }
    }
}
