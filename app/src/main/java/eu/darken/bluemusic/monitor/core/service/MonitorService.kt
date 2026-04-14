package eu.darken.bluemusic.monitor.core.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.common.ui.Service2
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import eu.darken.bluemusic.monitor.ui.MonitorNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : Service2() {

    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var notifications: MonitorNotifications
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var orchestrator: MonitorOrchestrator
    @Inject lateinit var eventDispatcher: EventDispatcher
    @Inject lateinit var ownerRegistry: AudioStreamOwnerRegistry

    private val serviceScope by lazy {
        CoroutineScope(SupervisorJob() + dispatcherProvider.IO)
    }

    private var injectionComplete = false
    private var monitoringJob: Job? = null
    @Volatile private var monitorGeneration = 0

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InlinedApi")
    override fun onCreate() {
        log(TAG, VERBOSE) { "onCreate()" }

        // Promote to foreground BEFORE Hilt injection (super.onCreate) to avoid 5-second timeout
        try {
            val earlyNotification = MonitorNotifications.createEarlyNotification(this)
            if (hasApiLevel(29)) {
                startForeground(
                    MonitorNotifications.NOTIFICATION_ID,
                    earlyNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(MonitorNotifications.NOTIFICATION_ID, earlyNotification)
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Early foreground promotion failed: ${e.asLog()}" }
            stopSelf()
            return
        }

        try {
            super.onCreate()
            injectionComplete = true
        } catch (e: Exception) {
            log(TAG, WARN) { "Hilt injection failed in onCreate(): ${e.asLog()}" }
            stopSelf()
            return
        }

        ContextCompat.registerReceiver(
            this,
            stopMonitorReceiver,
            IntentFilter(MonitorNotifications.ACTION_STOP_MONITOR),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log(TAG, VERBOSE) { "onStartCommand(intent=$intent, flags=$flags, startId=$startId)" }

        if (!injectionComplete) {
            log(TAG, WARN) { "onStartCommand: Injection incomplete, stopping service." }
            stopSelf()
            return START_NOT_STICKY
        }

        val forceStart = intent?.getBooleanExtra(EXTRA_FORCE_START, false) ?: false

        if (monitoringJob?.isActive == true && !forceStart) {
            log(TAG) { "Already monitoring and forceStart=false, keeping current session." }
            return START_STICKY
        }

        val generation = ++monitorGeneration
        serviceScope.coroutineContext.cancelChildren()

        monitoringJob = serviceScope.launch {
            try {
                orchestrator.monitor(serviceScope, ::updateNotification)
            } catch (_: CancellationException) {
                log(TAG) { "Monitor cancelled." }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Monitor failed: ${e.asLog()}" }
            } finally {
                if (monitorGeneration == generation) {
                    log(TAG) { "Monitor finished, stopping service." }
                    stopSelf()
                } else {
                    log(TAG) { "Monitor replaced, not stopping service." }
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        log(TAG, VERBOSE) { "onDestroy()" }
        try {
            unregisterReceiver(stopMonitorReceiver)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to unregister stopMonitor receiver: ${e.asLog()}" }
        }
        if (injectionComplete) {
            eventDispatcher.cancelAllJobs()
            ownerRegistry.resetBlocking()
            serviceScope.cancel("Service destroyed")
        } else {
            log(TAG, WARN) { "onDestroy: Skipping scope cancel, injection was incomplete." }
        }
        super.onDestroy()
    }

    private suspend fun updateNotification(activeDevices: List<ManagedDevice>) {
        notificationManager.notify(
            MonitorNotifications.NOTIFICATION_ID,
            notifications.getDevicesNotification(activeDevices),
        )
    }

    private val stopMonitorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            log(TAG) { "Stop monitor action received" }
            stopSelf()
        }
    }

    companion object {
        val TAG = logTag("Monitor", "Service")
        private const val EXTRA_FORCE_START = "extra.force_start"

        fun intent(context: Context, forceStart: Boolean = false): Intent {
            return Intent(context, MonitorService::class.java).apply {
                putExtra(EXTRA_FORCE_START, forceStart)
            }
        }
    }
}
