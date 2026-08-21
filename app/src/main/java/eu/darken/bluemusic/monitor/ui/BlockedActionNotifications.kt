package eu.darken.bluemusic.monitor.ui

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.PendingIntentCompat
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.hasApiLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the user why a connect action (launch app, show home screen, wake screen) did nothing.
 *
 * Android 10+ drops background activity starts without SYSTEM_ALERT_WINDOW, and it drops them
 * silently: [Context.startActivity] does not throw, so the failure is invisible both to the app
 * and to the user. The dashboard hint card that asks for the permission is dismissible, so a user
 * who dismissed it once gets no further signal that their configured actions never run.
 */
@Singleton
class BlockedActionNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    /**
     * Posts the "grant Display over other apps" notification, or updates it in place when it is
     * already showing. [NotificationCompat.Builder.setOnlyAlertOnce] keeps repeat connects from
     * buzzing the user again, so no extra throttling state is needed.
     */
    fun showOverlayPermissionMissing() {
        ensureNotificationChannel()

        val settingsIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val settingsPi = PendingIntent.getActivity(
            context,
            PI_REQUEST_CODE,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentCompat.FLAG_IMMUTABLE,
        )

        val message = context.getString(R.string.android10_applaunch_hint_message)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(context.getString(R.string.android10_applaunch_hint_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(settingsPi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()

        // notify() is a silent no-op when notifications are off (POST_NOTIFICATIONS denied on
        // API 33+, or the user disabled them). Record that, otherwise a support log shows us
        // raising the notification and the user insisting they never saw anything.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            log(TAG, WARN) { "Overlay permission is missing, but notifications are disabled." }
            return
        }

        log(TAG, WARN) { "Notifying about missing overlay permission" }
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /** Called once a background activity start succeeds again, i.e. the permission was granted. */
    fun clearOverlayPermissionMissing() {
        log(TAG, VERBOSE) { "Clearing overlay permission notification" }
        notificationManager.cancel(NOTIFICATION_ID)
    }

    @SuppressLint("NewApi")
    private fun ensureNotificationChannel() {
        if (!hasApiLevel(Build.VERSION_CODES.O)) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.label_notification_channel_setup),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    companion object {
        private val TAG = logTag("Monitor", "BlockedAction", "Notifications")
        private const val CHANNEL_ID = "notification.channel.setup"

        // 1 is the monitor foreground service notification, see MonitorNotifications.
        private const val NOTIFICATION_ID = 2
        private const val PI_REQUEST_CODE = 2
    }
}
