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
import eu.darken.bluemusic.monitor.core.modules.SettlePolicy
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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
    private val trackedJobs: MutableSet<Job> = ConcurrentHashMap.newKeySet()
    private val shutdown = AtomicBoolean(false)
    private val dispatchMutex = Mutex()

    private val _isIdle = MutableStateFlow(true)
    val isIdle: StateFlow<Boolean> = _isIdle.asStateFlow()

    // Monotonic counter incremented on every job tracked. Allows the orchestrator's
    // cooldown to detect "work happened during grace" deterministically, without
    // depending on StateFlow conflation behaviour for short-lived work.
    private val workGeneration = AtomicLong(0)
    fun currentWorkGeneration(): Long = workGeneration.get()

    // Lock that serializes the (set mutation + _isIdle write) pair so completion
    // callbacks for old jobs can't overwrite an `_isIdle = false` set by a fresh
    // trackJob — see EventDispatcherTest.`isIdle stays consistent under concurrent track and complete`.
    private val trackingLock = Any()

    suspend fun awaitIdle() {
        isIdle.filter { it }.first()
    }

    private fun trackJob(job: Job) {
        workGeneration.incrementAndGet()
        synchronized(trackingLock) {
            trackedJobs.add(job)
            _isIdle.value = false
        }
        job.invokeOnCompletion {
            synchronized(trackingLock) {
                trackedJobs.remove(job)
                if (trackedJobs.isEmpty()) _isIdle.value = true
            }
        }
    }

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

            val enabledState = devicesSettings.currentEnabledState()
            eventTypeDedupTracker.observeEnabledState(enabledState)
            if (!enabledState.isEnabled) {
                log(TAG, WARN) { "dispatch: Dropping event, app is disabled: $bluetoothEvent" }
                return@withLock
            }

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
                    trackJob(job)
                }
            }

            // Cancellable modules are tracked and cancelled when a superseding event arrives.
            activeJobs[address] = appScope.launch(dispatcherProvider.IO) {
                executeModules(deviceEvent, cancellableModules)
            }.also { job ->
                job.invokeOnCompletion { activeJobs.remove(address, job) }
                trackJob(job)
            }
        }
    }

    /**
     * Filters [modules] by [ConnectionModule.appliesTo] for this event, then runs them in
     * two groups: [SettlePolicy.Immediate] modules first, followed by a single
     * `delay(actionDelay)` barrier, followed by the [SettlePolicy.AfterDeviceSettle] /
     * [SettlePolicy.AfterDeviceSettlePlus] modules.
     *
     * Called twice per dispatched event — once for the non-cancellable group and once for
     * the cancellable group — so the barrier fires at most once per module-job-group.
     * In practice the non-cancellable group has only Immediate modules (no barrier paid),
     * and the cancellable group has the settled modules (one barrier).
     */
    private suspend fun executeModules(deviceEvent: DeviceEvent, modules: Collection<ConnectionModule>) {
        val applicable = modules.filter { module ->
            runCatching { module.appliesTo(deviceEvent) }.getOrElse {
                log(TAG, ERROR) { "appliesTo threw for ${module.tag}; treating as not-applicable: ${it.asLog()}" }
                false
            }
        }
        if (applicable.isEmpty()) {
            log(TAG, VERBOSE) { "executeModules: no applicable modules for $deviceEvent" }
            return
        }

        val planned = applicable.map { it to safePolicy(it, deviceEvent) }
        val (immediate, settled) = planned.partition { (_, policy) -> policy == SettlePolicy.Immediate }

        if (immediate.isNotEmpty()) runByPriority(deviceEvent, immediate)

        if (settled.isNotEmpty()) {
            val actionDelay = deviceEvent.device.actionDelay
            log(TAG, VERBOSE) { "executeModules: settle barrier — sleeping $actionDelay before ${settled.size} settled modules" }
            delay(actionDelay.toMillis())
            runByPriority(deviceEvent, settled)
        }
    }

    private fun safePolicy(module: ConnectionModule, event: DeviceEvent): SettlePolicy =
        runCatching { module.settlePolicy(event) }.getOrElse {
            log(TAG, ERROR) { "settlePolicy threw for ${module.tag}; defaulting to AfterDeviceSettle: ${it.asLog()}" }
            SettlePolicy.AfterDeviceSettle
        }

    private suspend fun runByPriority(
        deviceEvent: DeviceEvent,
        planned: List<Pair<ConnectionModule, SettlePolicy>>,
    ) {
        planned.groupBy { (module, _) -> module.priority }.toSortedMap().forEach { (priority, group) ->
            log(TAG, VERBOSE) { "runByPriority: ${group.size} modules at priority $priority" }
            coroutineScope {
                group.map { (module, policy) ->
                    async(dispatcherProvider.IO) {
                        try {
                            val extra = (policy as? SettlePolicy.AfterDeviceSettlePlus)?.extraDelay
                            if (extra != null && extra > Duration.ZERO) {
                                log(TAG, VERBOSE) { "runByPriority: ${module.tag} extra delay $extra" }
                                delay(extra.toMillis())
                            }
                            log(TAG, VERBOSE) { "runByPriority: ${module.tag} HANDLE-START" }
                            module.handle(deviceEvent)
                            log(TAG, VERBOSE) { "runByPriority: ${module.tag} HANDLE-STOP" }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log(TAG, ERROR) {
                                "runByPriority: Error: ${module.tag} for $deviceEvent: ${e.asLog()}"
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
