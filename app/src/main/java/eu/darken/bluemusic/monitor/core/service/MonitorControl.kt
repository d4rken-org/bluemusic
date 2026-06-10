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
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.monitor.ui.MonitorNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
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
    private val devicesSettings: DevicesSettings,
    @Suppress("unused") // Eagerly inits the singleton so notification channel + PendingIntent IPC
    monitorNotifications: MonitorNotifications, // run before any startForegroundService() call
) {

    init {
        var lastToggleEpoch: Long? = null
        combine(
            deviceRepo.devices.map { devices ->
                devices.any { device ->
                    device.isActive && device.requiresPersistentSession
                }
            },
            devicesSettings.enabledState,
        ) { needsService, enabledState -> needsService to enabledState }
            .distinctUntilChanged()
            .setupCommonEventHandlers(TAG) { "Device monitor" }
            .onEach { (needsService, enabledState) ->
                // A toggle epoch change means any running session is tearing itself down
                // (orchestrator's enabled watcher). A plain start in that window would be
                // swallowed by onStartCommand and then killed by the old job's stopSelf,
                // so re-enable starts must be replacement starts.
                val epochChanged = lastToggleEpoch != null && lastToggleEpoch != enabledState.toggleEpoch
                lastToggleEpoch = enabledState.toggleEpoch
                try {
                    when {
                        !enabledState.isEnabled -> {
                            log(TAG) { "App is disabled, stopping monitor service" }
                            stopMonitor()
                        }

                        needsService -> {
                            log(TAG) { "Active device needs persistent session, starting monitor service" }
                            startMonitor(forceStart = epochChanged)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Device monitor action failed: ${e.message}" }
                }
            }
            .launchIn(appScope)
    }

    suspend fun startMonitor(forceStart: Boolean = false) {
        log(TAG, VERBOSE) { "startMonitor(forceStart=$forceStart)" }
        if (!devicesSettings.currentEnabledState().isEnabled) {
            log(TAG, WARN) { "startMonitor: App is disabled, ignoring start request." }
            return
        }
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
