package eu.darken.bluemusic.monitor.core

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.monitor.core.screenwake.ScreenWakeActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionHelper: PermissionHelper,
) {
    private val powerManager by lazy { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    private val mutex = Mutex()
    private var cpuWakeLock: PowerManager.WakeLock? = null

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

                if (cpuWakeLock?.isHeld == false) {
                    cpuWakeLock?.acquire(4 * 60 * 60 * 1000L /*4 hours */)
                    log(TAG, INFO) { "CPU WakeLock acquired" }
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
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to release wakelocks: ${e.asLog()}" }
            }
        }
    }

    fun wakeScreenNow() {
        // Background activity launch (BAL) restrictions only apply on API 29+ (Android 10+).
        // On API 23-28 a transparent activity launch from a foreground service is permitted
        // without SYSTEM_ALERT_WINDOW. Skipping only when the OS would actually reject the
        // launch avoids silently no-oping on Android 6-9 where the dashboard hint is also
        // gated off.
        if (permissionHelper.needsOverlayPermission()) {
            log(TAG, WARN) { "Skipping screen wake: overlay permission not granted (Android 10+)." }
            return
        }
        try {
            val intent = Intent(context, ScreenWakeActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            or Intent.FLAG_ACTIVITY_NO_HISTORY
                            or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
            context.startActivity(intent)
            // OS-level BAL denial may not throw — this log means "launch requested",
            // not necessarily "screen actually woke".
            log(TAG, INFO) { "Requested ScreenWakeActivity launch." }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to launch ScreenWakeActivity: ${e.asLog()}" }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "WakeLock", "Manager")
    }
}
