package eu.darken.bluemusic.main.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.PendingIntentCompat
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.main.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {
    private val builder: NotificationCompat.Builder
    private var service: BlueMusicService? = null

    @Volatile
    private var isStarted = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.label_notification_channel_status),
                NotificationManager.IMPORTANCE_MIN
            )
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(context, 0, openIntent, PendingIntentCompat.FLAG_IMMUTABLE)

        val stopIntent = Intent(context, BlueMusicService::class.java).apply {
            action = STOP_ACTION
        }
        val stopPi = PendingIntent.getService(context, 0, stopIntent, PendingIntentCompat.FLAG_IMMUTABLE)

        builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setContentIntent(openPi)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentText(context.getString(R.string.label_status_idle))
            .setContentTitle(context.getString(R.string.app_name))
            .addAction(
                NotificationCompat.Action.Builder(0, context.getString(R.string.action_exit), stopPi).build()
            )

//        val lastCmd = ValueBox<String>()
//        Observable.create { emitter: ObservableEmitter<String> ->
//            this.emitter = emitter
//        }
//            .doOnNext { cmd -> log(TAG, VERBOSE) { "Submitted: cmd-$cmd" } }
//            .filter { cmd -> cmd != lastCmd.value }
//            .doOnNext { cmd -> lastCmd.value = cmd }
//            .subscribe { cmd ->
//                log(TAG) { "Processing cmd-$cmd" }
//                when (cmd) {
//                    CMD_START -> {
//                        if (!serviceStopper.isDisposed) {
//                            log(TAG) { "Stopping on-going shutdown due to cmd-$cmd" }
//                            serviceStopper.dispose()
//                        }
//
//                        if (isStarted) {
//                            log(TAG) { "Ignoring cmd-$cmd, already started!" }
//                            return@subscribe
//                        }
//
//                        isStarted = true
//                        log(TAG) { "Executing startForeground()" }
//                        service?.startForeground(NOTIFICATION_ID, builder.build())
//                    }
//                    else -> {
//                        if (!isStarted) {
//                            log(TAG, WARN) { "Calling stopForeground() without startForeground()" }
//                            emitter?.onNext(CMD_START)
//                            emitter?.onNext(CMD_STOP)
//                            return@subscribe
//                        }
//                        serviceStopper = Completable.timer(1500, TimeUnit.MILLISECONDS)
//                            .subscribeOn(Schedulers.io())
//                            .doOnComplete { isStarted = false }
//                            .observeOn(AndroidSchedulers.mainThread())
//                            .subscribe {
//                                log(TAG) { "Executing stopForeground()" }
//                                service?.let {
//                                    it.stopForeground(true)
//                                    it.stopSelf()
//                                }
//                                // Sometimes stopForeground doesn't remove the notification, but just makes it removable
//                                notificationManager.cancel(NOTIFICATION_ID)
//                            }
//                    }
//                }
//            }
    }

    fun setService(service: BlueMusicService) {
        this.service = service
    }

    fun start() {
//        emitter?.onNext(CMD_START)
    }

    fun stop() {
//        emitter?.onNext(CMD_STOP)
    }

    private fun updateNotification() {
        if (!isStarted) return
        val notification: Notification = builder.build()
        log(TAG, VERBOSE) { "updatingNotification()" }
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun updateActiveDevices(devices: Collection<ManagedDevice>) {
        log(TAG) { "updateActiveDevices(devices=$devices)" }
        val sb = StringBuilder()
        devices.forEachIndexed { index, device ->
            sb.append(device.label)
            if (index < devices.size - 1) sb.append(", ")
        }
        if (devices.isNotEmpty()) {
            builder.setContentTitle(sb.toString())
        } else {
            builder.setContentTitle(context.getString(R.string.label_no_connected_devices))
            builder.setContentText("")
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(""))
        }
        updateNotification()
    }

    fun updateMessage(message: String) {
        log(TAG) { "updateMessage(message=$message)" }
        builder.setContentText(message)
        builder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
        updateNotification()
    }

    companion object {
        private val TAG = logTag("ServiceHelper")
        private const val NOTIFICATION_CHANNEL_ID = "notification.channel.core"
        private const val NOTIFICATION_ID = 1
        internal const val STOP_ACTION = "STOP_SERVICE"
        private const val CMD_START = "start"
        private const val CMD_STOP = "stop"

        @JvmStatic
        fun getIntent(context: Context): Intent {
            return Intent(context, BlueMusicService::class.java)
        }

        @JvmStatic
        fun startService(context: Context, intent: Intent): ComponentName? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}