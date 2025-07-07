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
import eu.darken.bluemusic.App
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.BluetoothEventReceiverFlow
import eu.darken.bluemusic.bluetooth.core.BluetoothSourceFlow
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import eu.darken.bluemusic.data.device.DeviceAction
import eu.darken.bluemusic.data.device.DeviceManagerFlow
import eu.darken.bluemusic.data.device.ManagedDevice
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.audio.VolumeObserver
import eu.darken.bluemusic.main.core.service.modules.EventModule
import eu.darken.bluemusic.main.core.service.modules.VolumeModule
import eu.darken.bluemusic.settings.core.Settings
import eu.darken.bluemusic.util.ApiHelper
import eu.darken.bluemusic.util.WakelockMan
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
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class BlueMusicServiceFlow : Service(), VolumeObserver.Callback {
    @Inject lateinit var deviceManager: DeviceManagerFlow
    @Inject lateinit var bluetoothSource: BluetoothSourceFlow
    @Inject lateinit var streamHelper: StreamHelper
    @Inject lateinit var volumeObserver: VolumeObserver
    @Inject lateinit var settings: Settings
    @Inject lateinit var serviceHelper: ServiceHelper
    @Inject lateinit var wakelockMan: WakelockMan
    @Inject lateinit var eventModuleMap: Map<Class<out EventModule>, @JvmSuppressWildcards EventModule>
    @Inject lateinit var volumeModuleMap: Map<Class<out VolumeModule>, @JvmSuppressWildcards VolumeModule>
    @Inject lateinit var dispatcherProvider: DispatcherProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val onGoingConnections = ConcurrentHashMap<String, Job>()

    private val ringerPermission = object : BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        override fun onReceive(context: Context, intent: Intent) {
            val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            Timber.d("isNotificationPolicyAccessGranted()=%b", notificationManager.isNotificationPolicyAccessGranted)
        }
    }

    private val binder = MBinder()

    override fun onCreate() {
        Timber.v("onCreate()")
        (application as App).appComponent.inject(this)
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
            deviceManager.devices()
                .flowOn(dispatcherProvider.io)
                .collect { stringManagedDeviceMap ->
                    val connected = stringManagedDeviceMap.values.filter { it.isActive }
                    serviceHelper.updateActiveDevices(connected.toList())
                }
        }

        // Monitor Bluetooth state
        serviceScope.launch {
            bluetoothSource.isEnabled
                .flowOn(dispatcherProvider.io)
                .collect { isActive ->
                    if (!isActive) serviceHelper.stop()
                }
        }
    }

    override fun onDestroy() {
        Timber.v("onDestroy()")
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
        Timber.v("onUnbind(intent=%s)", intent)
        return true
    }

    override fun onRebind(intent: Intent) {
        Timber.v("onRebind(intent=%s)", intent)
        super.onRebind(intent)
    }

    @SuppressLint("ThrowableNotAtBeginning")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.v("onStartCommand-STARTED(intent=%s, flags=%d, startId=%d)", intent, flags, startId)
        serviceHelper.start()
        
        if (intent == null) {
            Timber.w("Intent was null")
            serviceHelper.stop()
        } else if (intent.hasExtra(BluetoothEventReceiverFlow.EXTRA_DEVICE_EVENT)) {
            val event = intent.getParcelableExtra<SourceDevice.Event>(BluetoothEventReceiverFlow.EXTRA_DEVICE_EVENT)!!
            
            serviceScope.launch(dispatcherProvider.io) {
                handleDeviceEvent(event)
            }
        } else if (ServiceHelper.STOP_ACTION == intent.action) {
            Timber.d("Stopping service, currently %d on-going events, killing them.", onGoingConnections.size)
            onGoingConnections.values.forEach { it.cancel() }
            onGoingConnections.clear()
            serviceHelper.stop()
        } else {
            serviceHelper.stop()
        }

        Timber.v("onStartCommand-END(intent=%s, flags=%d, startId=%d)", intent, flags, startId)
        return START_NOT_STICKY
    }

    private suspend fun handleDeviceEvent(event: SourceDevice.Event) {
        try {
            // Retry logic for connection verification
            var retryCount = 0
            val maxRetries = 10
            var connectedDevices: Map<String, SourceDevice>? = null
            
            while (retryCount < maxRetries) {
                connectedDevices = bluetoothSource.reloadConnectedDevices()
                
                if (event.type == SourceDevice.Event.Type.CONNECTED && !connectedDevices.containsKey(event.address)) {
                    retryCount++
                    Timber.w("%s not fully connected, retrying (#%d).", event.device.label, retryCount)
                    withContext(Dispatchers.Main) {
                        serviceHelper.updateMessage("${getString(R.string.description_waiting_for_devicex, event.device.label)} (#$retryCount)")
                    }
                    delay(300L * retryCount.coerceAtMost(3)) // Progressive delay
                } else {
                    break
                }
            }
            
            if (event.type == SourceDevice.Event.Type.CONNECTED && connectedDevices?.containsKey(event.address) != true) {
                Timber.e("Device ${event.address} failed to connect after $maxRetries retries")
                return
            }
            
            // Get managed device
            val managedDevice = deviceManager.getDevice(event.address)
            if (managedDevice == null) {
                Timber.w("Device ${event.address} is not managed")
                return
            }

            val action = DeviceAction(managedDevice, event.type)
            
            // Handle connection delay
            if (action.type == SourceDevice.Event.Type.CONNECTED) {
                withContext(Dispatchers.Main) {
                    serviceHelper.updateMessage(getString(R.string.label_reaction_delay))
                }
                val reactionDelay = action.device.actionDelay ?: Settings.DEFAULT_REACTION_DELAY
                Timber.d("Delaying reaction to %s by %d ms.", action, reactionDelay)
                delay(reactionDelay)
            }
            
            // Execute event modules
            Timber.d("Acting on %s", action)
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
            Timber.e(e, "Error handling device event: $event")
        }
    }
    
    private suspend fun executeEventModules(device: ManagedDevice, event: SourceDevice.Event) {
        val priorityArray = SparseArray<MutableList<EventModule>>()
        
        // Group modules by priority
        for ((_, module) in eventModuleMap) {
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
            Timber.d("%d event modules at priority %d", currentPriorityModules.size, priorityArray.keyAt(i))
            
            coroutineScope {
                currentPriorityModules.map { module ->
                    async(dispatcherProvider.io) {
                        try {
                            Timber.v("Event module %s HANDLE-START", module)
                            module.handle(device, event)
                            Timber.v("Event module %s HANDLE-STOP", module)
                        } catch (e: Exception) {
                            Timber.e(e, "Event module error: $module")
                        }
                    }
                }.awaitAll()
            }
        }
    }
    
    private suspend fun checkServiceStatus() {
        val devices = deviceManager.devices().first()
        Timber.d("Active devices: %s", devices)
        
        val msgBuilder = StringBuilder()
        var listening = false
        var locking = false
        var waking = false
        
        for (d in devices.values) {
            if (!d.isActive) continue
            if (d.address == FakeSpeakerDevice.ADDR) continue
            
            if (!listening && settings.isVolumeChangeListenerEnabled) {
                listening = true
                Timber.d("Keep running because we are listening for changes")
                msgBuilder.append(getString(R.string.label_volume_listener))
            }
            if (!locking && d.volumeLock) {
                locking = true
                Timber.d("Keep running because the device wants volume lock: %s", d)
                if (msgBuilder.isNotEmpty()) msgBuilder.append(",\n")
                msgBuilder.append(getString(R.string.label_volume_lock))
            }
            if (!waking && d.keepAwake) {
                waking = true
                Timber.d("Keep running because the device wants keep awake: %s", d)
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
        serviceScope.launch(dispatcherProvider.io) {
            val priorityArray = SparseArray<MutableList<VolumeModule>>()
            
            // Group modules by priority
            for ((_, module) in volumeModuleMap) {
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
                Timber.d("%d volume modules at priority %d", currentPriorityModules.size, priorityArray.keyAt(i))
                
                coroutineScope {
                    currentPriorityModules.map { module ->
                        async {
                            try {
                                Timber.v("Volume module %s HANDLE-START", module)
                                module.handle(id, volume)
                                Timber.v("Volume module %s HANDLE-STOP", module)
                            } catch (e: Exception) {
                                Timber.e(e, "Volume module error: $module")
                            }
                        }
                    }.awaitAll()
                }
            }
        }
    }
}