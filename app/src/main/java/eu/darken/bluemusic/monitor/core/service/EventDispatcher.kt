package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.getDevice
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches a [BluetoothEventQueue.Event] to all registered
 * [ConnectionModule]s after applying the fake-speaker safeguard and the
 * per-device type dedup.
 *
 * The dispatch pipeline is split into a fast acceptance lane and async module execution:
 * 1. Fast path (synchronous): fake speaker safeguard, dedup, ownership update, lastConnected
 * 2. Module execution: launched in a child job keyed by device address (non-blocking)
 *
 * Superseding events cancel in-flight cancellable module jobs for the same device.
 */
@Singleton
class EventDispatcher @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val deviceRepo: DeviceRepo,
    private val devicesSettings: DevicesSettings,
    private val connectionModuleMap: Set<@JvmSuppressWildcards ConnectionModule>,
    private val eventTypeDedupTracker: EventTypeDedupTracker,
    private val ownerRegistry: AudioStreamOwnerRegistry,
) {

    private val activeJobs = ConcurrentHashMap<DeviceAddr, Job>()
    private val nonCancellableJobs: MutableSet<Job> = ConcurrentHashMap.newKeySet()
    private val shutdown = AtomicBoolean(false)
    private val dispatchMutex = Mutex()

    suspend fun dispatch(bluetoothEvent: BluetoothEventQueue.Event) {
        dispatchMutex.withLock {
            if (shutdown.get()) {
                log(TAG, WARN) { "dispatch: Dropping event after shutdown: $bluetoothEvent" }
                return@withLock
            }

            log(TAG) { "dispatch: Handling $bluetoothEvent" }
            val managedDevice = deviceRepo.getDevice(bluetoothEvent.sourceDevice.address)

            if (managedDevice == null) {
                log(TAG, WARN) { "dispatch: Can't find managed device for $bluetoothEvent" }
                return@withLock
            }

            val isFakeSpeakerEvent = bluetoothEvent.sourceDevice.deviceType == SourceDevice.Type.PHONE_SPEAKER
            if (isFakeSpeakerEvent
                && bluetoothEvent.type == BluetoothEventQueue.Event.Type.CONNECTED
                && !managedDevice.isConnected
            ) {
                log(TAG, INFO) { "Dropping stale fake speaker CONNECTED, speaker is not currently the active device" }
                return@withLock
            }

            eventTypeDedupTracker.observeEnabledState(devicesSettings.currentEnabledState())

            if (!eventTypeDedupTracker.shouldProcess(bluetoothEvent.sourceDevice.address, bluetoothEvent.type)) {
                return@withLock
            }

            // --- Fast acceptance lane: ownership + state updates (synchronous) ---

            var displacedOwnerAddresses: List<String> = emptyList()

            val deviceEvent = when (bluetoothEvent.type) {
                BluetoothEventQueue.Event.Type.CONNECTED -> {
                    val connectResult = ownerRegistry.onDeviceConnected(
                        address = managedDevice.address,
                        label = managedDevice.label,
                        deviceType = managedDevice.type,
                        receivedAtElapsedMs = bluetoothEvent.receivedAtElapsedMs,
                        sequence = bluetoothEvent.sequence,
                    )
                    if (connectResult.ownershipChanged) {
                        displacedOwnerAddresses = connectResult.previousOwnerAddresses
                    }
                    DeviceEvent.Connected(managedDevice)
                }

                BluetoothEventQueue.Event.Type.DISCONNECTED -> {
                    val disconnectResult = ownerRegistry.resolveDisconnect(
                        address = managedDevice.address,
                        receivedAtElapsedMs = bluetoothEvent.receivedAtElapsedMs,
                    )
                    DeviceEvent.Disconnected(
                        device = managedDevice,
                        volumeSnapshot = bluetoothEvent.volumeSnapshot,
                        disconnectResult = disconnectResult,
                    )
                }
            }

            if (bluetoothEvent.type == BluetoothEventQueue.Event.Type.CONNECTED) {
                deviceRepo.updateDevice(managedDevice.address) {
                    it.copy(lastConnected = System.currentTimeMillis())
                }
            }

            // --- Async module execution (non-blocking) ---

            val address = managedDevice.address

            // Cancel displaced owner's in-flight cancellable jobs when ownership transfers.
            // e.g., AirPods ramping to 100% should stop when speaker takes ownership.
            //
            // Known limitation: real BT devices always take ownership from PHONE_SPEAKER
            // (see resolveOwnerGroupLocked), so a reconnecting device will cancel the
            // speaker's ramp and apply its own volumes — even if the speaker was just
            // set up.  In chaotic connect/disconnect cycles (e.g., AirPods firmware
            // briefly reconnecting after case closure), this causes visible volume churn:
            // the device ramps up, disconnects, then the speaker restarts its ramp down.
            // We can't suppress the device's connect because we can't distinguish an
            // intentional connect from a firmware ghost reconnect at the ACL level.
            for (displacedAddr in displacedOwnerAddresses) {
                if (displacedAddr == address) continue
                val displacedJob = activeJobs[displacedAddr]
                if (displacedJob != null && displacedJob.isActive) {
                    log(TAG, INFO) { "dispatch: Cancelling displaced owner job for $displacedAddr" }
                    displacedJob.cancel(CancellationException("Ownership transferred to $address"))
                }
            }

            val existingJob = activeJobs[address]
            if (existingJob != null && existingJob.isActive) {
                log(TAG, INFO) { "dispatch: Cancelling superseded cancellable job for $address" }
                existingJob.cancel(CancellationException("Superseded by new ${bluetoothEvent.type} event"))
            }

            val cancellableModules = connectionModuleMap.filter { it.cancellable }
            val nonCancellableModules = connectionModuleMap.filter { !it.cancellable }

            log(TAG) { "dispatch: Launching module work for $deviceEvent (${cancellableModules.size} cancellable, ${nonCancellableModules.size} non-cancellable)" }

            // Non-cancellable modules survive supersession but are tracked for teardown cleanup.
            if (nonCancellableModules.isNotEmpty()) {
                appScope.launch(dispatcherProvider.IO) {
                    executeModules(deviceEvent, nonCancellableModules)
                }.also { job ->
                    nonCancellableJobs.add(job)
                    job.invokeOnCompletion { nonCancellableJobs.remove(job) }
                }
            }

            // Cancellable modules are tracked and cancelled when a superseding event arrives.
            activeJobs[address] = appScope.launch(dispatcherProvider.IO) {
                executeModules(deviceEvent, cancellableModules)
            }.also { job -> job.invokeOnCompletion { activeJobs.remove(address, job) } }
        }
    }

    private suspend fun executeModules(deviceEvent: DeviceEvent, modules: Collection<ConnectionModule>) {
        val modulesByPriority = modules
            .groupBy { it.priority }
            .toSortedMap()

        for ((priority, modules) in modulesByPriority) {
            log(TAG, VERBOSE) { "dispatch: ${modules.size} modules at priority $priority" }

            coroutineScope {
                modules.map { module ->
                    async(dispatcherProvider.IO) {
                        try {
                            log(TAG, VERBOSE) { "dispatch: ${module.tag} HANDLE-START" }
                            module.handle(deviceEvent)
                            log(TAG, VERBOSE) { "dispatch: ${module.tag} HANDLE-STOP" }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log(TAG, ERROR) {
                                "dispatch: Error: ${module.tag} for $deviceEvent: ${e.asLog()}"
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    fun resetForNewSession() {
        runBlocking {
            dispatchMutex.withLock {
                shutdown.set(false)
                log(TAG, INFO) { "resetForNewSession: Dispatcher ready for new events" }
            }
        }
    }

    fun cancelAllJobs() {
        shutdown.set(true)
        runBlocking {
            dispatchMutex.withLock {
                log(TAG, INFO) { "cancelAllJobs: Cancelling ${activeJobs.size} cancellable + ${nonCancellableJobs.size} non-cancellable jobs" }
                activeJobs.values.forEach { it.cancel() }
                activeJobs.clear()
                nonCancellableJobs.forEach { it.cancel() }
                nonCancellableJobs.clear()
            }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "Event", "Dispatcher")
    }
}
