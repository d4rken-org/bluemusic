package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.currentState
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.setupCommonEventHandlers
import eu.darken.bluemusic.common.flow.throttleLatest
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.monitor.core.audio.RingerModeObserver
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorOrchestrator @Inject constructor(
    private val bluetoothRepo: BluetoothRepo,
    private val deviceRepo: DeviceRepo,
    private val volumeObserver: VolumeObserver,
    private val ringerModeObserver: RingerModeObserver,
    private val bluetoothEventQueue: BluetoothEventQueue,
    private val eventDispatcher: EventDispatcher,
    private val ringerModeTransitionHandler: RingerModeTransitionHandler,
    private val ownerRegistry: AudioStreamOwnerRegistry,
    private val volumeEventDispatcher: VolumeEventDispatcher,
) {

    /**
     * Runs the monitoring pipeline. Suspends until a shutdown condition is met, then returns normally.
     * The caller is responsible for acting on the return (e.g., stopping the service).
     *
     * @param scope the scope in which observer flows are launched. An internal child [Job] is used
     *              for lifecycle control — the caller's scope is never cancelled by this method.
     * @param onActiveDevicesChanged called whenever the active device list changes, including the
     *                               initial snapshot. The caller typically updates a notification.
     */
    suspend fun monitor(
        scope: CoroutineScope,
        onActiveDevicesChanged: suspend (List<ManagedDevice>) -> Unit,
    ) {
        val bluetoothState = bluetoothRepo.currentState()
        if (!bluetoothState.isReady) {
            log(TAG, WARN) { "Aborting, Bluetooth state is not ready: $bluetoothState" }
            return
        }

        val initialDevices = deviceRepo.currentDevices()
        ownerRegistry.reset()
        ownerRegistry.bootstrap(initialDevices)
        eventDispatcher.resetForNewSession()

        onActiveDevicesChanged(initialDevices.filter { it.isActive })

        val monitorJob = Job(scope.coroutineContext[Job])
        val monitorScope = CoroutineScope(scope.coroutineContext + monitorJob)

        ringerModeObserver.ringerMode
            .setupCommonEventHandlers(TAG) { "RingerMode monitor" }
            .distinctUntilChanged()
            .onEach { ringerModeTransitionHandler.handle(it) }
            .catch { log(TAG, WARN) { "RingerMode monitor flow failed:\n${it.asLog()}" } }
            .launchIn(monitorScope)

        volumeObserver.volumes
            .setupCommonEventHandlers(TAG) { "Volume monitor" }
            .distinctUntilChanged()
            .onEach { volumeEventDispatcher.dispatch(it) }
            .catch { log(TAG, WARN) { "Volume monitor flow failed:\n${it.asLog()}" } }
            .launchIn(monitorScope)

        bluetoothEventQueue.events
            .setupCommonEventHandlers(TAG) { "Event monitor" }
            .onEach { event ->
                log(TAG, INFO) { "START Handling bluetooth event: $event" }
                eventDispatcher.dispatch(event)
                log(TAG, INFO) { "STOP Handling bluetooth event: $event" }
            }
            .catch { log(TAG, WARN) { "Event monitor flow failed:\n${it.asLog()}" } }
            .launchIn(monitorScope)

        val deviceMonitorJob = deviceRepo.devices
            .setupCommonEventHandlers(TAG) { "Devices monitor" }
            .distinctUntilChanged()
            .throttleLatest(3000)
            .flatMapLatest { devices ->
                val activeDevices = devices.filter { it.isActive }

                log(TAG) { "monitor: Currently active devices: ${activeDevices.map { "${it.address}/${it.label}" }}" }
                onActiveDevicesChanged(activeDevices)

                val stayActive = activeDevices.any { it.requiresMonitor }

                when {
                    activeDevices.isNotEmpty() && stayActive -> {
                        log(TAG) { "Staying connected for active devices." }
                        emptyFlow()
                    }

                    activeDevices.isNotEmpty() -> flow {
                        log(TAG) { "There are active devices but we don't need to stay active for them." }
                        val maxMonitoringDuration = activeDevices.maxOf { it.monitoringDuration }
                        log(TAG) { "Maximum monitoring duration: $maxMonitoringDuration" }
                        val toDelay = Duration.ofSeconds(15) + maxMonitoringDuration
                        delay(toDelay.toMillis())
                        log(TAG) { "Stopping now, nothing changed." }
                        monitorJob.cancel()
                    }

                    else -> flow<Unit> {
                        log(TAG) { "No devices connected, stopping soon" }
                        delay(15 * 1000)
                        log(TAG) { "Stopping now, still no devices connected." }
                        monitorJob.cancel()
                    }
                }
            }
            .catch { log(TAG, WARN) { "Monitor flow failed:\n${it.asLog()}" } }
            .launchIn(monitorScope)

        try {
            log(TAG, VERBOSE) { "Monitor job is active" }
            deviceMonitorJob.join()
            log(TAG, VERBOSE) { "Monitor job quit" }
        } finally {
            monitorJob.cancel()
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "Orchestrator")
    }
}
