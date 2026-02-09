package eu.darken.bluemusic.monitor.core.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.SparseArray
import androidx.core.content.ContextCompat
import androidx.core.util.size
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.currentState
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.setupCommonEventHandlers
import eu.darken.bluemusic.common.flow.throttleLatest
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.common.ui.Service2
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.getDevice
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerModeEvent
import eu.darken.bluemusic.monitor.core.audio.RingerModeObserver
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import eu.darken.bluemusic.monitor.ui.MonitorNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : Service2() {

    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var notifications: MonitorNotifications
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var deviceRepo: DeviceRepo
    @Inject lateinit var bluetoothRepo: BluetoothRepo
    @Inject lateinit var connectionModuleMap: Set<@JvmSuppressWildcards ConnectionModule>
    @Inject lateinit var volumeModuleMap: Set<@JvmSuppressWildcards VolumeModule>
    @Inject lateinit var volumeObserver: VolumeObserver
    @Inject lateinit var ringerModeObserver: RingerModeObserver
    @Inject lateinit var bluetoothEventQueue: BluetoothEventQueue

    private val serviceScope by lazy {
        CoroutineScope(SupervisorJob() + dispatcherProvider.IO)
    }

    private var monitoringJob: Job? = null
    @Volatile private var monitorGeneration = 0

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InlinedApi")
    override fun onCreate() {
        super.onCreate()
        log(TAG, VERBOSE) { "onCreate()" }

        val notification = notifications.getInitialNotification()
        try {
            if (hasApiLevel(29)) {
                startForeground(
                    MonitorNotifications.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(MonitorNotifications.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to start foreground: ${e.asLog()}" }
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

        val forceStart = intent?.getBooleanExtra(EXTRA_FORCE_START, false) ?: false

        if (monitoringJob?.isActive == true && !forceStart) {
            log(TAG) { "Already monitoring and forceStart=false, keeping current session." }
            return START_STICKY
        }

        val generation = ++monitorGeneration
        serviceScope.coroutineContext.cancelChildren()

        monitoringJob = serviceScope.launch {
            try {
                startMonitoring()
            } catch (e: CancellationException) {
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
        serviceScope.cancel("Service destroyed")
        super.onDestroy()
    }

    private suspend fun startMonitoring() {
        val bluetoothState = bluetoothRepo.currentState()
        if (!bluetoothState.isReady) {
            log(TAG, WARN) { "Aborting, Bluetooth state is not ready: $bluetoothState" }
            return
        }

        notificationManager.notify(
            MonitorNotifications.NOTIFICATION_ID,
            notifications.getDevicesNotification(deviceRepo.currentDevices().filter { it.isActive }),
        )

        ringerModeObserver.ringerMode
            .setupCommonEventHandlers(TAG) { "RingerMode monitor" }
            .distinctUntilChanged()
            .onEach { handleRingerMode(it) }
            .catch { log(TAG, WARN) { "RingerMode monitor flow failed:\n${it.asLog()}" } }
            .launchIn(serviceScope)

        volumeObserver.volumes
            .setupCommonEventHandlers(TAG) { "Volume monitor" }
            .distinctUntilChanged()
            .onEach { handleVolumeChange(it) }
            .catch { log(TAG, WARN) { "Volume monitor flow failed:\n${it.asLog()}" } }
            .launchIn(serviceScope)

        bluetoothEventQueue.events
            .setupCommonEventHandlers(TAG) { "Event monitor" }
            .onEach { event ->
                log(TAG, INFO) { "START Handling bluetooth event: $event" }
                handleEvent(event)
                log(TAG, INFO) { "STOP Handling bluetooth event: $event" }
            }
            .catch { log(TAG, WARN) { "Event monitor flow failed:\n${it.asLog()}" } }
            .launchIn(serviceScope)

        val monitorJob = deviceRepo.devices
            .setupCommonEventHandlers(TAG) { "Devices monitor" }
            .distinctUntilChanged()
            .throttleLatest(3000)
            .flatMapLatest { devices ->
                val activeDevices = devices.filter { it.isActive }

                log(TAG) { "monitorJob: Currently active devices: $activeDevices" }
                notificationManager.notify(
                    MonitorNotifications.NOTIFICATION_ID,
                    notifications.getDevicesNotification(activeDevices),
                )

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
                        log(TAG) { "Stopping service now, nothing changed." }
                        serviceScope.coroutineContext.cancelChildren()
                    }

                    else -> flow<Unit> {
                        log(TAG) { "No devices connected, stopping soon" }
                        delay(15 * 1000)
                        log(TAG) { "Stopping service now, still no devices connected." }
                        serviceScope.coroutineContext.cancelChildren()
                    }
                }
            }
            .catch { log(TAG, WARN) { "Monitor flow failed:\n${it.asLog()}" } }
            .launchIn(serviceScope)

        log(TAG, VERBOSE) { "Monitor job is active" }
        monitorJob.join()
        log(TAG, VERBOSE) { "Monitor job quit" }
    }

    private val stopMonitorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            log(TAG) { "Stop monitor action received" }
            stopSelf()
        }
    }

    private suspend fun handleEvent(bluetoothEvent: BluetoothEventQueue.Event) {
        log(TAG) { "handleEvent: Handling $bluetoothEvent" }
        val managedDevice = deviceRepo.getDevice(bluetoothEvent.sourceDevice.address)

        if (managedDevice == null) {
            log(TAG, WARN) { "handleEvent: Can't find managed device for $bluetoothEvent" }
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

        log(TAG) { "handleEvent: Processing event $deviceEvent" }

        for (i in 0 until priorityArray.size) {
            val currentPriorityModules = priorityArray.get(priorityArray.keyAt(i))
            log(TAG, VERBOSE) {
                "handleEvent: ${currentPriorityModules.size} modules at priority ${priorityArray.keyAt(i)}"
            }

            coroutineScope {
                currentPriorityModules.map { module ->
                    async(dispatcherProvider.IO) {
                        try {
                            log(TAG, VERBOSE) {
                                "handleEvent: ${module.tag} HANDLE-START for $deviceEvent"
                            }
                            module.handle(deviceEvent)
                            log(TAG, VERBOSE) {
                                "handleEvent: ${module.tag} HANDLE-STOP for $deviceEvent"
                            }
                        } catch (e: Exception) {
                            log(TAG, ERROR) {
                                "handleEvent: Error: ${module.tag} for $deviceEvent: ${e.asLog()}"
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
                "handleVolume: ${currentPriorityModules.size} modules at priority ${priorityArray.keyAt(i)}"
            }

            coroutineScope {
                currentPriorityModules.map { module ->
                    async {
                        try {
                            log(TAG, VERBOSE) { "handleVolume: ${module.tag} HANDLE-START" }
                            module.handle(event)
                            log(TAG, VERBOSE) { "handleVolume: ${module.tag} HANDLE-STOP" }
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "handleVolume: error: ${module.tag}: ${e.asLog()}" }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun handleRingerMode(event: RingerModeEvent) {
        log(TAG, VERBOSE) { "handleRingerMode: $event" }
        val activeDevice = deviceRepo.currentDevices().firstOrNull { it.isActive }
        if (activeDevice == null) {
            log(TAG, INFO) { "handleRingerMode: No active device, skipping." }
            return
        }
        if (activeDevice.getVolume(AudioStream.Type.RINGTONE) == null) {
            log(TAG, INFO) { "handleRingerMode: No ringtone volume configured, skipping." }
            return
        }

        val volumeMode = when (event.newMode) {
            RingerMode.SILENT -> VolumeMode.Silent
            RingerMode.VIBRATE -> VolumeMode.Vibrate
            RingerMode.NORMAL -> {
                val currentVolume = activeDevice.getVolume(AudioStream.Type.RINGTONE)
                if (currentVolume != null && currentVolume >= 0f) {
                    VolumeMode.Normal(currentVolume)
                } else {
                    VolumeMode.Normal(0.5f)
                }
            }
        }

        deviceRepo.updateDevice(activeDevice.address) {
            it.updateVolume(AudioStream.Type.RINGTONE, volumeMode)
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
