package eu.darken.bluemusic.monitor.core

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.monitor.ui.BlockedActionNotifications
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gate for the connect actions that start an activity from the background.
 *
 * Android 10+ (API 29) blocks background activity starts unless the app has an exemption, and it
 * blocks them *silently*: `startActivity` returns normally and throws nothing, the system just
 * drops the start on its own side. Without this gate a debug log reads
 * "Launching app: com.example.player" with no error after it, which looks exactly like success and
 * has repeatedly sent support down the wrong path.
 *
 * Two exemptions are reachable here: holding SYSTEM_ALERT_WINDOW ("Display over other apps"), which
 * is the only durable one for an autonomous start out of the monitor pipeline, and having a visible
 * activity, which applies while the user is in BlueMusic testing a reconnect. A foreground service
 * on its own is explicitly not an exemption.
 */
@Singleton
class BackgroundActivityGuard @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val permissionHelper: PermissionHelper,
    private val notifications: BlockedActionNotifications,
) {

    private val activityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    /**
     * Returns true when a background activity start will actually reach the system.
     *
     * When it will not, logs [what] as blocked and raises the permission notification so the user
     * learns why their configured action did nothing. [what] is a log-only description and is never
     * shown to the user.
     */
    fun canStartActivityOrNotify(what: String): Boolean {
        if (!permissionHelper.needsOverlayPermission()) {
            notifications.clearOverlayPermissionMissing()
            return true
        }

        // A visible activity is an independent BAL exemption, so a connect that happens while the
        // user sits in BlueMusic testing their setup would succeed even without the permission.
        // Reporting it as blocked there would be a false alarm on the one screen that can fix it.
        if (hasVisibleActivity()) {
            log(TAG, VERBOSE) { "Allowing $what: no overlay permission, but our UI is visible." }
            return true
        }

        log(TAG, WARN) { "Skipping $what: Android 10+ blocks this without the overlay permission." }
        notifications.showOverlayPermissionMissing()
        return false
    }

    /**
     * Drops the notification once the permission is in place, for the case where it was granted
     * somewhere other than the notification's own tap-through (dashboard hint, system settings).
     * Otherwise it would linger until the next connect event, which may be hours away.
     */
    fun syncNotificationState() {
        if (permissionHelper.needsOverlayPermission()) return
        notifications.clearOverlayPermissionMissing()
    }

    private fun hasVisibleActivity(): Boolean = try {
        // Since API 21 this only ever reports our own processes, which is all we need.
        val own = activityManager.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }
        own != null && own.importance <= RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    } catch (e: Exception) {
        log(TAG, WARN) { "Failed to determine process importance: ${e.asLog()}" }
        false
    }

    companion object {
        private val TAG = logTag("Monitor", "BackgroundActivityGuard")
    }
}
