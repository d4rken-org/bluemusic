package eu.darken.bluemusic.monitor.core.worker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.SparseArray
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.util.size
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.currentState
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.setupCommonEventHandlers
import eu.darken.bluemusic.common.flow.throttleLatest
import eu.darken.bluemusic.common.flow.withPrevious
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.EventModule
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import eu.darken.bluemusic.monitor.ui.MonitorNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.time.delay


@HiltWorker
class MonitorWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val dispatcherProvider: DispatcherProvider,
    private val notifications: MonitorNotifications,
    private val notificationManager: NotificationManager,
    private val deviceRepo: DeviceRepo,
    private val bluetoothRepo: BluetoothRepo,
    private val eventModuleMap: Set<@JvmSuppressWildcards EventModule>,
    private val volumeModuleMap: Set<@JvmSuppressWildcards VolumeModule>,
    private val volumeObserver: VolumeObserver,
) : CoroutineWorker(context, params) {

    private val workerScope = CoroutineScope(SupervisorJob() + dispatcherProvider.IO)

    private var finishedWithError = false

    init {
        log(TAG, VERBOSE) { "init(): workerId=$id" }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return notifications.getForegroundInfo(emptyList())
    }

    override suspend fun doWork(): Result = try {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "Executing $inputData now (runAttemptCount=$runAttemptCount)" }

        doDoWork()

        val duration = System.currentTimeMillis() - start

        log(TAG, VERBOSE) { "Execution finished after ${duration}ms, $inputData" }

        Result.success(inputData)
    } catch (e: Throwable) {
        if (e !is CancellationException) {
            finishedWithError = true
            Result.failure(inputData)
        } else {
            Result.success()
        }
    } finally {
        this.workerScope.cancel("Worker finished (withError?=$finishedWithError).")
    }

    private suspend fun doDoWork() {
        val bluetoothState = bluetoothRepo.currentState()
        if (!bluetoothState.isReady) {
            log(TAG, WARN) { "Aborting, Bluetooth state is not ready: $bluetoothState" }
            return
        }

        setForeground(notifications.getForegroundInfo(deviceRepo.currentDevices().filter { it.isActive }))

        if (hasApiLevel(23)) {
            // TODO why do we do this?
            ContextCompat.registerReceiver(
                context,
                ringerPermission,
                IntentFilter(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        ContextCompat.registerReceiver(
            context,
            stopMonitorReceiver,
            IntentFilter(MonitorNotifications.ACTION_STOP_MONITOR),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        volumeObserver.volumes
            .setupCommonEventHandlers(TAG) { "Volume monitor" }
            .distinctUntilChanged()
            .onEach { handleVolumeChange(it) }
            .catch { log(TAG, WARN) { "Volume monitor flow failed:\n${it.asLog()}" } }
            .launchIn(workerScope)

        deviceRepo.devices
            .setupCommonEventHandlers(TAG) { "Connection monitor" }
            .distinctUntilChangedBy { devices ->
                devices.map { device -> device.address to device.isActive }.toSet()
            }
            .withPrevious()
            .onEach { (before, current) ->
                handleConnectionChange(before ?: emptyList(), current)
            }
            .catch { log(TAG, WARN) { "Connection monitor flow failed:\n${it.asLog()}" } }
            .launchIn(workerScope)

        val monitorJob = deviceRepo.devices
            .setupCommonEventHandlers(TAG) { "Devices monitor" }
            .distinctUntilChanged()
            .throttleLatest(1000)
            .flatMapLatest { devices ->
                val activeDevices = devices.filter { it.isActive }
                log(TAG) { "monitorJob: Currently active devices: $activeDevices" }
                notificationManager.notify(
                    MonitorNotifications.NOTIFICATION_ID,
                    notifications.getDevicesNotification(activeDevices),
                )
                when {
                    activeDevices.isNotEmpty() -> emptyFlow()
                    else -> flow<Unit> {
                        log(TAG) { "No devices connected, canceling soon" }
                        delay(15 * 1000)
                        log(TAG) { "Canceling worker now, still no devices connected." }

                        workerScope.coroutineContext.cancelChildren()
                    }
                }
            }
            .catch {
                log(TAG, WARN) { "Monitor flow failed:\n${it.asLog()}" }
            }
            .launchIn(workerScope)

        log(TAG, VERBOSE) { "Monitor job is active" }
        monitorJob.join()
        log(TAG, VERBOSE) { "Monitor job quit" }

        try {
            context.unregisterReceiver(ringerPermission)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to unregister ringerPermission receiver: ${e.asLog()}" }
        }

        try {
            context.unregisterReceiver(stopMonitorReceiver)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to unregister stopMonitor receiver: ${e.asLog()}" }
        }
    }

    private val ringerPermission = object : BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        override fun onReceive(context: Context, intent: Intent) {
            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            log(TAG) { "isNotificationPolicyAccessGranted()=${notificationManager.isNotificationPolicyAccessGranted}" }
        }
    }

    private val stopMonitorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            log(TAG) { "Stop monitor action received" }
            workerScope.cancel("Stop action received from notification")
        }
    }

    private suspend fun handleConnectionChange(
        before: List<ManagedDevice>,
        current: List<ManagedDevice>,
    ) {
        // Create maps for easier lookup
        val beforeMap = before.associateBy { it.address }
        val currentMap = current.associateBy { it.address }

        // Find devices that changed their active state
        val connectedDevices = mutableListOf<ManagedDevice>()
        val disconnectedDevices = mutableListOf<ManagedDevice>()

        // Only process devices where isActive state actually changed
        currentMap.forEach { (address, currentDevice) ->
            val beforeDevice = beforeMap[address]
            if (beforeDevice != null && beforeDevice.isActive != currentDevice.isActive) {
                // Device exists in both lists and active state changed
                if (currentDevice.isActive) {
                    connectedDevices.add(currentDevice)
                } else {
                    disconnectedDevices.add(currentDevice)
                }
            }
        }

        connectedDevices.forEach { dev ->
            deviceRepo.updateDevice(dev.address) {
                it.copy(lastConnected = System.currentTimeMillis())
            }
        }

        log(TAG) { "Connected devices:\n${connectedDevices.joinToString("\n")}" }
        log(TAG) { "Disconnected devices:\n${disconnectedDevices.joinToString("\n")}" }

        val events = mutableListOf<DeviceEvent>()
        disconnectedDevices.forEach { events.add(DeviceEvent.Disconnected(it)) }
        connectedDevices.forEach { events.add(DeviceEvent.Connected(it)) }

        // Apply reaction delay only if there are connected devices
        if (connectedDevices.isNotEmpty()) {
            val connectedDevice = connectedDevices.first()
            val reactionDelay = connectedDevice.actionDelay
            log(TAG) { "Delaying reaction by $reactionDelay ms." }
            delay(reactionDelay)
        }

        val priorityArray = SparseArray<MutableList<EventModule>>()

        for (module in eventModuleMap) {
            val priority = module.priority
            var list = priorityArray.get(priority)
            if (list == null) {
                list = ArrayList()
                priorityArray.put(priority, list)
            }
            list.add(module)
        }

        // Process all events
        for (event in events) {
            log(TAG) { "Processing event: $event" }

            for (i in 0 until priorityArray.size) {
                val currentPriorityModules = priorityArray.get(priorityArray.keyAt(i))
                log(TAG) { "${currentPriorityModules.size} event modules at priority ${priorityArray.keyAt(i)}" }

                coroutineScope {
                    currentPriorityModules.map { module ->
                        async(dispatcherProvider.IO) {
                            try {
                                log(TAG, VERBOSE) { "Event module $module HANDLE-START for $event" }
                                module.handle(event)
                                log(TAG, VERBOSE) { "Event module $module HANDLE-STOP for $event" }
                            } catch (e: Exception) {
                                log(TAG, ERROR) { "Event module error: $module for $event: ${e.asLog()}" }
                            }
                        }
                    }.awaitAll()
                }
            }
        }
    }

    private suspend fun handleVolumeChange(event: VolumeEvent) {
        val priorityArray = SparseArray<MutableList<VolumeModule>>()

        for (module in volumeModuleMap) {
            val priority = module.priority
            var list = priorityArray.get(priority)
            if (list == null) {
                list = ArrayList()
                priorityArray.put(priority, list)
            }
            list.add(module)
        }

        for (i in 0 until priorityArray.size) {
            val currentPriorityModules = priorityArray.get(priorityArray.keyAt(i))
            log(TAG) { "${currentPriorityModules.size} volume modules at priority ${priorityArray.keyAt(i)}" }

            coroutineScope {
                currentPriorityModules.map { module ->
                    async {
                        try {
                            log(TAG, VERBOSE) { "Volume module $module HANDLE-START" }
                            module.handle(event)
                            log(TAG, VERBOSE) { "Volume module $module HANDLE-STOP" }
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "Volume module error: $module: ${e.asLog()}" }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    companion object {
        val TAG = logTag("Monitor", "Worker")
    }
}
