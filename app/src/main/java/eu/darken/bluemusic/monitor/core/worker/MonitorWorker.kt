package eu.darken.bluemusic.monitor.core.worker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.SparseArray
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
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.getDevice
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.Duration


@HiltWorker
class MonitorWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val dispatcherProvider: DispatcherProvider,
    private val notifications: MonitorNotifications,
    private val notificationManager: NotificationManager,
    private val deviceRepo: DeviceRepo,
    private val bluetoothRepo: BluetoothRepo,
    private val connectionModuleMap: Set<@JvmSuppressWildcards ConnectionModule>,
    private val volumeModuleMap: Set<@JvmSuppressWildcards VolumeModule>,
    private val volumeObserver: VolumeObserver,
    private val bluetoothEventQueue: BluetoothEventQueue,
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

        bluetoothEventQueue.events
            .setupCommonEventHandlers(TAG) { "Event monitor" }
            .onEach { event -> handleBluetoothEvent(event) }
            .catch { log(TAG, WARN) { "Event monitor flow failed:\n${it.asLog()}" } }
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

                val stayActive = activeDevices.any { it.volumeLock || it.volumeObserving || it.volumeRateLimiter }
                val maxMonitoringDuration = activeDevices.maxOf { it.monitoringDuration }
                log(TAG) { "Maximum monitoring duration: $maxMonitoringDuration (stayActive=$stayActive)" }

                when {
                    activeDevices.isNotEmpty() && stayActive -> {
                        log(TAG) { "Staying connected for active devices." }
                        emptyFlow()
                    }

                    activeDevices.isNotEmpty() -> flow {
                        log(TAG) { "There are active devices but we don't need to stay active for them." }
                        val toDelay = Duration.ofSeconds(15) + maxMonitoringDuration
                        delay(toDelay.toMillis())
                        log(TAG) { "Canceling worker now, nothing changed." }
                        workerScope.coroutineContext.cancelChildren()
                    }

                    else -> flow<Unit> {
                        log(TAG) { "No devices connected, canceling soon" }
                        delay(15 * 1000)
                        log(TAG) { "Canceling worker now, still no devices connected." }

                        workerScope.coroutineContext.cancelChildren()
                    }
                }
            }
            .catch { log(TAG, WARN) { "Monitor flow failed:\n${it.asLog()}" } }
            .launchIn(workerScope)

        log(TAG, VERBOSE) { "Monitor job is active" }
        monitorJob.join()
        log(TAG, VERBOSE) { "Monitor job quit" }

        try {
            context.unregisterReceiver(stopMonitorReceiver)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to unregister stopMonitor receiver: ${e.asLog()}" }
        }
    }

    private val stopMonitorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            log(TAG) { "Stop monitor action received" }
            workerScope.cancel("Stop action received from notification")
        }
    }

    private suspend fun handleBluetoothEvent(
        bluetoothEvent: BluetoothEventQueue.Event,
    ) {
        log(TAG) { "handleBluetoothEvent: Handling $bluetoothEvent" }
        val managedDevice = deviceRepo.getDevice(bluetoothEvent.sourceDevice.address)

        if (managedDevice == null) {
            log(TAG, WARN) { "Can't find managed device for $bluetoothEvent" }
            return
        }

        val deviceEvent = when (bluetoothEvent.type) {
            BluetoothEventQueue.Event.Type.CONNECTED -> DeviceEvent.Connected(managedDevice)
            BluetoothEventQueue.Event.Type.DISCONNECTED -> DeviceEvent.Disconnected(managedDevice)
        }

        // TODO make this a module?
        deviceRepo.updateDevice(managedDevice.address) {
            it.copy(lastConnected = System.currentTimeMillis())
        }

        val priorityArray = SparseArray<MutableList<ConnectionModule>>()

        for (module in connectionModuleMap) {
            val priority = module.priority
            var list = priorityArray.get(priority)
            if (list == null) {
                list = ArrayList()
                priorityArray.put(priority, list)
            }
            list.add(module)
        }

        log(TAG) { "handleBluetoothEvent: Processing event $deviceEvent" }

        for (i in 0 until priorityArray.size) {
            val currentPriorityModules = priorityArray.get(priorityArray.keyAt(i))
            log(TAG) {
                "handleBluetoothEvent: ${currentPriorityModules.size} event modules at priority ${priorityArray.keyAt(i)}"
            }

            coroutineScope {
                currentPriorityModules.map { module ->
                    async(dispatcherProvider.IO) {
                        try {
                            log(TAG, VERBOSE) {
                                "handleBluetoothEvent: ${module.tag} HANDLE-START for $deviceEvent"
                            }
                            module.handle(deviceEvent)
                            log(TAG, VERBOSE) {
                                "handleConnection: ${module.tag} HANDLE-STOP for $deviceEvent"
                            }
                        } catch (e: Exception) {
                            log(TAG, ERROR) {
                                "handleBluetoothEvent: Error: ${module.tag} for $deviceEvent: ${e.asLog()}"
                            }
                        }
                    }
                }.awaitAll()
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
            log(TAG) {
                "handleVolune: ${currentPriorityModules.size} volume modules at priority ${priorityArray.keyAt(i)}"
            }

            coroutineScope {
                currentPriorityModules.map { module ->
                    async {
                        try {
                            log(TAG, VERBOSE) { "handleVolune: Volume module ${module.tag} HANDLE-START" }
                            module.handle(event)
                            log(TAG, VERBOSE) { "handleVolune: Volume module ${module.tag} HANDLE-STOP" }
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "handleVolune: Volume module error: ${module.tag}: ${e.asLog()}" }
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