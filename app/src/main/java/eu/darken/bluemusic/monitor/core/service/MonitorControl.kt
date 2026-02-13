package eu.darken.bluemusic.monitor.core.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.setupCommonEventHandlers
import eu.darken.bluemusic.common.startServiceCompat
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.monitor.ui.MonitorNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorControl @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    deviceRepo: DeviceRepo,
    @Suppress("unused") // Eagerly inits the singleton so notification channel + PendingIntent IPC
    monitorNotifications: MonitorNotifications, // run before any startForegroundService() call
) {

    init {
        deviceRepo.devices
            .map { devices ->
                devices.any { device ->
                    device.isConnected && device.requiresMonitor
                }
            }
            .distinctUntilChanged()
            .setupCommonEventHandlers(TAG) { "Device monitor" }
            .onEach { requiresMonitor ->
                if (requiresMonitor) {
                    log(TAG) { "Connected device requires monitor, starting monitor service" }
                    startMonitor()
                }
            }
            .launchIn(appScope)
    }

    fun startMonitor(forceStart: Boolean = false) {
        log(TAG, VERBOSE) { "startMonitor(forceStart=$forceStart)" }
        try {
            context.startServiceCompat(MonitorService.intent(context, forceStart))
            log(TAG) { "Monitor start request sent." }
        } catch (e: IllegalStateException) {
            log(TAG, WARN) { "Failed to start monitor service: ${e.message}" }
        }
    }

    fun stopMonitor() {
        log(TAG, VERBOSE) { "stopMonitor()" }
        context.stopService(MonitorService.intent(context))
        log(TAG) { "Monitor stop request sent." }
    }

    companion object {
        private val TAG = logTag("Monitor", "Control")
    }
}
