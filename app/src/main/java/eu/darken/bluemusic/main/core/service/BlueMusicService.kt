package eu.darken.bluemusic.main.core.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.SparseArray
import androidx.annotation.RequiresApi
import androidx.core.util.size
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.BluetoothEventReceiver
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.speaker.FakeSpeakerDevice
import eu.darken.bluemusic.common.ApiHelper
import eu.darken.bluemusic.common.WakelockMan
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceAction
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.getDevice
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.audio.VolumeObserver
import eu.darken.bluemusic.main.core.service.modules.EventModule
import eu.darken.bluemusic.main.core.service.modules.VolumeModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class BlueMusicService : Service(), VolumeObserver.Callback {

    @Inject lateinit var deviceRepo: DeviceRepo
    @Inject lateinit var bluetoothSource: BluetoothRepo
    @Inject lateinit var streamHelper: StreamHelper
    @Inject lateinit var volumeObserver: VolumeObserver
    @Inject lateinit var devicesSettings: DevicesSettings
    @Inject lateinit var serviceHelper: ServiceHelper
    @Inject lateinit var wakelockMan: WakelockMan
    @Inject lateinit var eventModuleMap: Set<@JvmSuppressWildcards EventModule>
    @Inject lateinit var volumeModuleMap: Set<@JvmSuppressWildcards VolumeModule>
    @Inject lateinit var dispatcherProvider: DispatcherProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val onGoingConnections = ConcurrentHashMap<String, Job>()

    private val ringerPermission = object : BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        override fun onReceive(context: Context, intent: Intent) {
            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            log(TAG) { "isNotificationPolicyAccessGranted()=${notificationManager.isNotificationPolicyAccessGranted}" }
        }
    }

    private val binder = MBinder()

    override fun onCreate() {
        log(TAG, VERBOSE) { "onCreate()" }
        super.onCreate()

        // Set service reference to break circular dependency
        serviceHelper.setService(this)

        for (id in AudioStream.Id.values()) {
            volumeObserver.addCallback(id, this)
        }
        contentResolver.registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, volumeObserver)

        if (ApiHelper.hasMarshmallow()) {
            registerReceiver(ringerPermission, IntentFilter(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED))
        }

        // Subscribe to device changes
        serviceScope.launch {
            deviceRepo.devices
                .collect { stringManagedDeviceMap ->
                    val connected = stringManagedDeviceMap.filter { it.isActive }
                    serviceHelper.updateActiveDevices(connected.toList())
                }
        }

        // Monitor Bluetooth state
        serviceScope.launch {
            bluetoothSource.isEnabled
                .flowOn(dispatcherProvider.IO)
                .collect { isActive ->
                    if (!isActive) serviceHelper.stop()
                }
        }
    }

    override fun onDestroy() {
        log(TAG, VERBOSE) { "onDestroy()" }
        contentResolver.unregisterContentObserver(volumeObserver)
        if (ApiHelper.hasMarshmallow()) {
            unregisterReceiver(ringerPermission)
        }
        serviceScope.cancel()
        wakelockMan.tryRelease()
        super.onDestroy()
    }

    internal inner class MBinder : Binder()

    override fun onBind(intent: Intent): IBinder = binder

    override fun onUnbind(intent: Intent): Boolean {
        log(TAG, VERBOSE) { "onUnbind(intent=$intent)" }
        return true
    }

    override fun onRebind(intent: Intent) {
        log(TAG, VERBOSE) { "onRebind(intent=$intent)" }
        super.onRebind(intent)
    }

    @SuppressLint("ThrowableNotAtBeginning")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log(TAG, VERBOSE) { "onStartCommand-STARTED(intent=$intent, flags=$flags, startId=$startId)" }
        serviceHelper.start()

        if (intent == null) {
            log(TAG, WARN) { "Intent was null" }
            serviceHelper.stop()
        } else if (intent.hasExtra(BluetoothEventReceiver.EXTRA_DEVICE_EVENT)) {
            val event = intent.getParcelableExtra<SourceDevice.Event>(BluetoothEventReceiver.EXTRA_DEVICE_EVENT)!!

            serviceScope.launch(dispatcherProvider.IO) {
                handleDeviceEvent(event)
            }
        } else if (ServiceHelper.STOP_ACTION == intent.action) {
            log(TAG) { "Stopping service, currently ${onGoingConnections.size} on-going events, killing them." }
            onGoingConnections.values.forEach { it.cancel() }
            onGoingConnections.clear()
            serviceHelper.stop()
        } else {
            serviceHelper.stop()
        }

        log(TAG, VERBOSE) { "onStartCommand-END(intent=$intent, flags=$flags, startId=$startId)" }
        return START_NOT_STICKY
    }

    private suspend fun handleDeviceEvent(event: SourceDevice.Event) {
        try {
            // Retry logic for connection verification
            var retryCount = 0
            val maxRetries = 10
            var connectedDevices: Map<DeviceAddr, SourceDevice>? = null

            while (retryCount < maxRetries) {
                connectedDevices = bluetoothSource.connectedDevices.first().associateBy { it.address }

                if (event.type == SourceDevice.Event.Type.CONNECTED && !connectedDevices.containsKey(event.address)) {
                    retryCount++
                    log(TAG, WARN) { "${event.device.label} not fully connected, retrying (#$retryCount)." }
                    withContext(Dispatchers.Main) {
                        serviceHelper.updateMessage(
                            "${
                                getString(
                                    R.string.description_waiting_for_devicex,
                                    event.device.label
                                )
                            } (#$retryCount)"
                        )
                    }
                    delay(300L * retryCount.coerceAtMost(3)) // Progressive delay
                } else {
                    break
                }
            }

            if (event.type == SourceDevice.Event.Type.CONNECTED && connectedDevices?.containsKey(event.address) != true) {
                log(TAG, ERROR) { "Device ${event.address} failed to connect after $maxRetries retries" }
                return
            }

            // Get managed device
            val managedDevice = deviceRepo.getDevice(event.address)
            if (managedDevice == null) {
                log(TAG, WARN) { "Device ${event.address} is not managed" }
                return
            }

            val action = DeviceAction(managedDevice, event.type)

            // Handle connection delay
            if (action.type == SourceDevice.Event.Type.CONNECTED) {
                withContext(Dispatchers.Main) {
                    serviceHelper.updateMessage(getString(R.string.label_reaction_delay))
                }
                val reactionDelay = action.device.actionDelay ?: DevicesSettings.DEFAULT_REACTION_DELAY
                log(TAG) { "Delaying reaction to $action by $reactionDelay ms." }
                delay(reactionDelay)
            }

            // Execute event modules
            log(TAG) { "Acting on $action" }
            withContext(Dispatchers.Main) {
                serviceHelper.updateMessage(getString(R.string.label_status_adjusting_volumes))
            }

            if (event.type == SourceDevice.Event.Type.CONNECTED) {
                val job = serviceScope.launch {
                    executeEventModules(managedDevice, event)
                }
                onGoingConnections[event.address] = job
            } else if (event.type == SourceDevice.Event.Type.DISCONNECTED) {
                onGoingConnections.remove(event.address)?.cancel()
            }

            // Check if service should continue running
            checkServiceStatus()

        } catch (e: Exception) {
            log(TAG, ERROR) { "Error handling device event: $event: ${e.asLog()}" }
        }
    }

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
        for (i in 0 until priorityArray.size()) {
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

    private suspend fun checkServiceStatus() {
        val devices = deviceRepo.currentDevices()
        log(TAG) { "Current devices: $devices" }

        val msgBuilder = StringBuilder()
        var listening = false
        var locking = false
        var waking = false

        for (d in devices) {
            if (!d.isActive) continue
            if (d.address == FakeSpeakerDevice.ADDRESS) continue

            if (!listening && devicesSettings.volumeListening.value()) {
                listening = true
                log(TAG) { "Keep running because we are listening for changes" }
                msgBuilder.append(getString(R.string.label_volume_listener))
            }
            if (!locking && d.volumeLock) {
                locking = true
                log(TAG) { "Keep running because the device wants volume lock: $d" }
                if (msgBuilder.isNotEmpty()) msgBuilder.append(",\n")
                msgBuilder.append(getString(R.string.label_volume_lock))
            }
            if (!waking && d.keepAwake) {
                waking = true
                log(TAG) { "Keep running because the device wants keep awake: $d" }
                if (msgBuilder.isNotEmpty()) msgBuilder.append(",\n")
                msgBuilder.append(getString(R.string.label_keep_awake))
            }
        }

        val keepRunning = listening || locking || waking

        withContext(Dispatchers.Main) {
            if (keepRunning) {
                serviceHelper.updateMessage(msgBuilder.toString())
            } else {
                serviceHelper.stop()
                wakelockMan.tryRelease()
            }
        }
    }

    override fun onVolumeChanged(id: AudioStream.Id, volume: Int) {
        serviceScope.launch(dispatcherProvider.IO) {
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
                                module.handle(id, volume)
                                log(TAG, VERBOSE) { "Volume module $module HANDLE-STOP" }
                            } catch (e: Exception) {
                                log(TAG, ERROR) { "Volume module error: $module: ${e.asLog()}" }
                            }
                        }
                    }.awaitAll()
                }
            }
        }
    }

    companion object {
        private val TAG = logTag("Devices", "Service")
    }
}