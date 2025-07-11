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
import androidx.core.util.size
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.setupCommonEventHandlers
import eu.darken.bluemusic.common.flow.throttleLatest
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.main.core.GeneralSettings
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.audio.VolumeObserver
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach


@HiltWorker
class MonitorWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val dispatcherProvider: DispatcherProvider,
    private val notifications: MonitorNotifications,
    private val notificationManager: NotificationManager,
    private val generalSettings: GeneralSettings,
    private val deviceRepo: DeviceRepo,
    private val bluetoothRepo: BluetoothRepo,
    private val streamHelper: StreamHelper,
    private val devicesSettings: DevicesSettings,
    private val eventModuleMap: Set<@JvmSuppressWildcards EventModule>,
    private val volumeModuleMap: Set<@JvmSuppressWildcards VolumeModule>,
    private val volumeObserver: VolumeObserver,
    private val permissionHelper: PermissionHelper,
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
        if (!permissionHelper.hasBluetoothPermission()) {
            log(TAG, WARN) { "Aborting, missing Bluetooth permission" }
            return
        }

        if (!bluetoothRepo.isEnabled.first()) {
            log(TAG, WARN) { "Aborting, Bluetooth is disabled." }
            return
        }

        setForeground(notifications.getForegroundInfo(emptyList()))

        if (hasApiLevel(23)) {
            // TODO why do we do this?
            context.registerReceiver(ringerPermission, IntentFilter(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED))
        }

        volumeObserver.volumes
            .setupCommonEventHandlers(TAG) { "Volume monitor" }
            .distinctUntilChanged()
            .onEach { handleVolumeChange(it) }
            .catch {
                log(TAG, WARN) { "Volume monitor flow failed:\n${it.asLog()}" }
            }
            .launchIn(workerScope)

        val monitorJob = deviceRepo.devices
            .setupCommonEventHandlers(TAG) { "Devices monitor" }
            .distinctUntilChanged()
            .throttleLatest(1000)
            .flatMapLatest { devices ->
                notificationManager.notify(
                    MonitorNotifications.NOTIFICATION_ID,
                    notifications.getDevicesNotification(devices),
                )
                when {
                    devices.any { it.isActive } -> emptyFlow()
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
    }

    private val ringerPermission = object : BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        override fun onReceive(context: Context, intent: Intent) {
            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            log(TAG) { "isNotificationPolicyAccessGranted()=${notificationManager.isNotificationPolicyAccessGranted}" }
        }
    }

//    private suspend fun handleDeviceEvent(event: SourceDevice.Event) {
//        try {
//            // Retry logic for connection verification
//            var retryCount = 0
//            val maxRetries = 10
//            var connectedDevices: Map<DeviceAddr, SourceDevice>? = null
//
//            while (retryCount < maxRetries) {
//                connectedDevices = bluetoothSource.connectedDevices.first().associateBy { it.address }
//
//                if (event.type == SourceDevice.Event.Type.CONNECTED && !connectedDevices.containsKey(event.address)) {
//                    retryCount++
//                    log(TAG, WARN) { "${event.device.label} not fully connected, retrying (#$retryCount)." }
//                    withContext(Dispatchers.Main) {
//                        serviceController.updateMessage(
//                            "${
//                                getString(
//                                    R.string.description_waiting_for_devicex,
//                                    event.device.label
//                                )
//                            } (#$retryCount)"
//                        )
//                    }
//                    delay(300L * retryCount.coerceAtMost(3)) // Progressive delay
//                } else {
//                    break
//                }
//            }
//
//            if (event.type == SourceDevice.Event.Type.CONNECTED && connectedDevices?.containsKey(event.address) != true) {
//                log(TAG, ERROR) { "Device ${event.address} failed to connect after $maxRetries retries" }
//                return
//            }
//
//            // Get managed device
//            val managedDevice = deviceRepo.getDevice(event.address)
//            if (managedDevice == null) {
//                log(TAG, WARN) { "Device ${event.address} is not managed" }
//                return
//            }
//
//            val action = DeviceAction(managedDevice, event.type)
//
//            // Handle connection delay
//            if (action.type == SourceDevice.Event.Type.CONNECTED) {
//                withContext(Dispatchers.Main) {
//                    serviceController.updateMessage(getString(R.string.label_reaction_delay))
//                }
//                val reactionDelay = action.device.actionDelay ?: DevicesSettings.DEFAULT_REACTION_DELAY
//                log(TAG) { "Delaying reaction to $action by $reactionDelay ms." }
//                delay(reactionDelay)
//            }
//
//            // Execute event modules
//            log(TAG) { "Acting on $action" }
//            withContext(Dispatchers.Main) {
//                serviceController.updateMessage(getString(R.string.label_status_adjusting_volumes))
//            }
//
//            if (event.type == SourceDevice.Event.Type.CONNECTED) {
//                val job = serviceScope.launch {
//                    executeEventModules(managedDevice, event)
//                }
//                onGoingConnections[event.address] = job
//            } else if (event.type == SourceDevice.Event.Type.DISCONNECTED) {
//                onGoingConnections.remove(event.address)?.cancel()
//            }
//
//            // Check if service should continue running
//            checkServiceStatus()
//
//        } catch (e: Exception) {
//            log(TAG, ERROR) { "Error handling device event: $event: ${e.asLog()}" }
//        }
//    }

    private suspend fun executeEventModules(device: ManagedDevice, event: SourceDevice.Event) {
        val priorityArray = SparseArray<MutableList<EventModule>>()

        // Group modules by priority
        for (module in eventModuleMap) {
            val priority = module.priority
            var list = priorityArray.get(priority)
            if (list == null) {
                list = ArrayList()
                priorityArray.put(priority, list)
            }
            list.add(module)
        }

        // Execute modules by priority
        for (i in 0 until priorityArray.size) {
            val currentPriorityModules = priorityArray.get(priorityArray.keyAt(i))
            log(TAG) { "${currentPriorityModules.size} event modules at priority ${priorityArray.keyAt(i)}" }

            coroutineScope {
                currentPriorityModules.map { module ->
                    async(dispatcherProvider.IO) {
                        try {
                            log(TAG, VERBOSE) { "Event module $module HANDLE-START" }
                            module.handle(device, event)
                            log(TAG, VERBOSE) { "Event module $module HANDLE-STOP" }
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "Event module error: $module: ${e.asLog()}" }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun handleVolumeChange(event: VolumeObserver.ChangeEvent) {
        val priorityArray = SparseArray<MutableList<VolumeModule>>()

        // Group modules by priority
        for (module in volumeModuleMap) {
            val priority = module.priority
            var list = priorityArray.get(priority)
            if (list == null) {
                list = ArrayList()
                priorityArray.put(priority, list)
            }
            list.add(module)
        }

        // Execute modules by priority
        for (i in 0 until priorityArray.size) {
            val currentPriorityModules = priorityArray.get(priorityArray.keyAt(i))
            log(TAG) { "${currentPriorityModules.size} volume modules at priority ${priorityArray.keyAt(i)}" }

            coroutineScope {
                currentPriorityModules.map { module ->
                    async {
                        try {
                            log(TAG, VERBOSE) { "Volume module $module HANDLE-START" }
                            module.handle(event.streamId, event.volume)
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
