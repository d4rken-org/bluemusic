package eu.darken.bluemusic.bluetooth.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.speaker.FakeSpeakerDevice
import eu.darken.bluemusic.bluetooth.core.speaker.SpeakerDeviceProvider
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import eu.darken.bluemusic.common.debug.logging.asLog
import javax.inject.Inject

import javax.inject.Singleton

@Singleton
class LiveBluetoothSourceFlow @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val speakerDeviceProvider: SpeakerDeviceProvider,
) {

    companion object {
        private val TAG = logTag("LiveBluetoothSourceFlow")
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    val isEnabled: Flow<Boolean> = callbackFlow {
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
    }.flowOn(dispatcherProvider.IO)

    val connectedDevices: Flow<Map<String, SourceDevice>> = callbackFlow {
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
    }.flowOn(dispatcherProvider.IO)

    suspend fun reloadConnectedDevices(): Map<String, SourceDevice> {
        return withContext(dispatcherProvider.IO) {
            loadConnectedDevices()
        }
    }

    private suspend fun loadConnectedDevices(): Map<String, SourceDevice> {
        val devices = mutableMapOf<String, SourceDevice>()

        // Always add FakeSpeakerDevice
        devices[FakeSpeakerDevice.address] = speakerDeviceProvider.getSpeaker()

        if (bluetoothAdapter?.isEnabled != true) {
            return devices
        }

        // Check for BLUETOOTH_CONNECT permission on Android 12+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                log(TAG, WARN) { "BLUETOOTH_CONNECT permission not granted" }
                return devices
            }
        }

        try {
            // Get bonded devices that are connected
            bluetoothAdapter.bondedDevices?.forEach { device ->
                if (isConnected(device)) {
                    val sourceDevice = SourceDeviceWrapper(device)
                    devices[device.address] = sourceDevice
                    log(TAG) { "Connected device: ${device.name} - ${device.address}" }
                }
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Error loading connected devices: ${e.asLog()}" }
        }

        return devices
    }

    private fun isConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            log(TAG, WARN) { "Could not determine connection state for ${device.address}: ${e.asLog()}" }
            false
        }
    }
}