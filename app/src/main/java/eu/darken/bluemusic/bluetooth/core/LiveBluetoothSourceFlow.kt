package eu.darken.bluemusic.bluetooth.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AppComponent.Scope
class LiveBluetoothSourceFlow @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) : BluetoothSourceFlow {
    
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    override val isEnabled: Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        trySend(state == BluetoothAdapter.STATE_ON)
                    }
                }
            }
        }
        
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(receiver, filter)
        
        // Emit initial state
        trySend(bluetoothAdapter?.isEnabled ?: false)
        
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }.flowOn(dispatcherProvider.io)
    
    override val connectedDevices: Flow<Map<String, SourceDevice>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED,
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        // Trigger a refresh of connected devices
                        trySend(Unit)
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
        
        // Emit initial state
        trySend(Unit)
        
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }.map {
        loadConnectedDevices()
    }.flowOn(dispatcherProvider.io)
    
    override suspend fun reloadConnectedDevices(): Map<String, SourceDevice> {
        return withContext(dispatcherProvider.io) {
            loadConnectedDevices()
        }
    }
    
    private fun loadConnectedDevices(): Map<String, SourceDevice> {
        val devices = mutableMapOf<String, SourceDevice>()

        // Always add FakeSpeakerDevice
        devices[FakeSpeakerDevice.ADDR] = FakeSpeakerDevice(context)
        
        if (bluetoothAdapter?.isEnabled != true) {
            return devices
        }

        // Check for BLUETOOTH_CONNECT permission on Android 12+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Timber.w("BLUETOOTH_CONNECT permission not granted")
                return devices
            }
        }
        
        try {
            // Get bonded devices that are connected
            bluetoothAdapter.bondedDevices?.forEach { device ->
                if (isConnected(device)) {
                    val sourceDevice = SourceDeviceWrapper(device)
                    devices[device.address] = sourceDevice
                    Timber.d("Connected device: ${device.name} - ${device.address}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading connected devices")
        }
        
        return devices
    }
    
    private fun isConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            Timber.w(e, "Could not determine connection state for ${device.address}")
            false
        }
    }
}