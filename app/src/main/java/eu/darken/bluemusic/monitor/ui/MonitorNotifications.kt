package eu.darken.bluemusic.monitor.ui

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.PendingIntentCompat
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.main.ui.MainActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject


class MonitorNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    private val builderLock = Mutex()
    private val builder: NotificationCompat.Builder

    init {
        @SuppressLint("NewApi")
        if (hasApiLevel(Build.VERSION_CODES.O)) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.label_notification_channel_status),
                NotificationManager.IMPORTANCE_MIN
            )
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(context, 0, openIntent, PendingIntentCompat.FLAG_IMMUTABLE)

        builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setContentIntent(openPi)
            .setSmallIcon(R.drawable.ic_notification_small)


    }

    private fun getBuilder(
        devices: Collection<ManagedDevice>,
    ): NotificationCompat.Builder = builder.apply {
        log(TAG) { "getBuilder($devices)" }

        if (devices.isNotEmpty()) {
            val sb = StringBuilder()
            devices.forEachIndexed { index, device ->
                sb.append(device.label)
                if (index < devices.size - 1) sb.append(", ")
            }
            setContentTitle(sb.toString())

            val extraFlags = mutableListOf<String>()
            var listening = false
            var locking = false
            var waking = false
            var limiting = false

            for (dev in devices) {
                if (!dev.isActive) continue

                if (!listening && dev.volumeObserving) {
                    listening = true
                    log(TAG) { "Keep running because we are observing changes" }
                    extraFlags.add(context.getString(R.string.label_volume_listener))
                }
                if (!locking && dev.volumeLock) {
                    locking = true
                    log(TAG) { "Keep running because the device wants volume lock: $dev" }
                    extraFlags.add(context.getString(R.string.devices_device_config_volume_lock_label))
                }
                if (!waking && dev.keepAwake) {
                    waking = true
                    log(TAG) { "Keep running because the device wants keep awake: $dev" }
                    extraFlags.add(context.getString(R.string.devices_device_config_keep_awake_label))
                }
                if (!limiting && dev.volumeRateLimiter) {
                    limiting = true
                    log(TAG) { "Keep running because the device to be rate limited: $dev" }
                    extraFlags.add(context.getString(R.string.devices_device_config_volume_rate_limiter_label))
                }
            }
            val msg = extraFlags.joinToString(", ")
            setContentText(msg)
            setStyle(NotificationCompat.BigTextStyle().bigText(msg))
        } else {
            setContentTitle(context.getString(R.string.label_no_connected_devices))
            setContentText("")
            setStyle(NotificationCompat.BigTextStyle().bigText(""))
        }

        clearActions()
        val stopIntent = Intent(ACTION_STOP_MONITOR).apply { setPackage(context.packageName) }
        val stopPi = PendingIntent.getBroadcast(context, 0, stopIntent, PendingIntentCompat.FLAG_IMMUTABLE)
        addAction(NotificationCompat.Action.Builder(0, context.getString(R.string.action_exit), stopPi).build())
    }

    suspend fun getDevicesNotification(devices: Collection<ManagedDevice>) = builderLock.withLock {
        log(TAG) { "getDevicesNotification(devices=$devices)" }
        return@withLock getBuilder(devices).build()
    }

    suspend fun getForegroundInfo(devices: Collection<ManagedDevice>): ForegroundInfo = builderLock.withLock {
        log(TAG) { "getForegroundInfo(devices=$devices)" }
        getBuilder(devices).toForegroundInfo()
    }

    @SuppressLint("InlinedApi")
    private fun NotificationCompat.Builder.toForegroundInfo(): ForegroundInfo = if (hasApiLevel(29)) {
        ForegroundInfo(
            NOTIFICATION_ID,
            this.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
    } else {
        ForegroundInfo(
            NOTIFICATION_ID,
            this.build()
        )
    }

    companion object {
        val TAG = logTag("Monitor", "Notifications")
        private const val NOTIFICATION_CHANNEL_ID = "notification.channel.core"
        internal const val NOTIFICATION_ID = 1
        const val ACTION_STOP_MONITOR = "eu.darken.bluemusic.monitor.STOP"
    }
}
