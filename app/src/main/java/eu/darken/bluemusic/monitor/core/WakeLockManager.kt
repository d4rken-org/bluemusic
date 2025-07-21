package eu.darken.bluemusic.monitor.core

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val powerManager by lazy { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    private val mutex = Mutex()
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null

    suspend fun setWakeLock(enabled: Boolean) = mutex.withLock {
        if (enabled) {
            try {
                if (cpuWakeLock == null) {
                    cpuWakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "BlueMusic:KeepAwakeCPU"
                    ).apply {
                        setReferenceCounted(false)
                    }
                }

                if (screenWakeLock == null) {
                    @Suppress("DEPRECATION")
                    screenWakeLock = powerManager.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                        "BlueMusic:KeepAwakeScreen"
                    ).apply {
                        setReferenceCounted(false)
                    }
                }

                if (cpuWakeLock?.isHeld == false) {
                    cpuWakeLock?.acquire(4 * 60 * 60 * 1000L /*4 hours */)
                    log(TAG, INFO) { "CPU WakeLock acquired" }
                }

                if (screenWakeLock?.isHeld == false) {
                    screenWakeLock?.acquire(4 * 60 * 60 * 1000L /*4 hours */)
                    log(TAG, INFO) { "Screen WakeLock acquired" }
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to acquire wakelocks: ${e.asLog()}" }
            }
        } else {
            try {
                if (cpuWakeLock?.isHeld == true) {
                    cpuWakeLock?.release()
                    log(TAG, INFO) { "CPU WakeLock released" }
                }
                if (screenWakeLock?.isHeld == true) {
                    screenWakeLock?.release()
                    log(TAG, INFO) { "Screen WakeLock released" }
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to release wakelocks: ${e.asLog()}" }
            }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "WakeLock", "Manager")
    }
}