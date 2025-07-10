package eu.darken.bluemusic.bluetooth.core

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.bluetooth.core.speaker.SpeakerDeviceProvider
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.error.MissingPermissionException
import eu.darken.bluemusic.common.flow.replayingShare
import eu.darken.bluemusic.common.hasApiLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BluetoothRepo @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val bluetoothManager: BluetoothManager,
    private val speakerDeviceProvider: SpeakerDeviceProvider,
) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH])
    private fun BluetoothDevice.hasUUID(target: Int): Boolean = uuids.any {
        val uuid = it.uuid
        val value = (uuid.mostSignificantBits and 0x0000FFFF00000000L) ushr 32
        value.toInt() == target
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH])
    val pairedDevices: Flow<Set<SourceDevice>> = callbackFlow {
        val bonded = bluetoothManager.adapter.bondedDevices
            .filterNot { device ->
                val isHealthDevice = device.hasUUID(0x1400)
                if (isHealthDevice) log(TAG) { "Health devices are excluded: ${device.name} - ${device.address}" }

                isHealthDevice
            }
            .map { device -> SourceDeviceWrapper(device) }
            .toSet()
        send(bonded)
        awaitClose()
    }
        .catch { err ->
            log(TAG, ERROR) { "Error loading paired devices: ${err.asLog()}" }
            if (err is SecurityException) throw MissingPermissionException(
                if (hasApiLevel(31)) {
                    @Suppress("NewApi")
                    Manifest.permission.BLUETOOTH_CONNECT
                } else {
                    Manifest.permission.BLUETOOTH
                }
            )
            else throw err
        }
        .replayingShare(appScope)

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
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Emit initial state
        trySend(bluetoothAdapter?.isEnabled ?: false)

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }.flowOn(dispatcherProvider.IO)

    val connectedDevices: Flow<Set<SourceDevice>> = callbackFlow {
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
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        trySend(Unit)

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }
        .map { loadConnectedDevices() }
        .flowOn(dispatcherProvider.IO)

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH])
    private suspend fun loadConnectedDevices(): Set<SourceDevice> {
        val devices = mutableSetOf<SourceDevice>()

        devices.add(speakerDeviceProvider.getSpeaker())

        if (bluetoothAdapter?.isEnabled != true) return devices

        try {
            bluetoothAdapter.bondedDevices?.forEach { device ->
                if (device.isConnected()) {
                    devices.add(SourceDeviceWrapper(device))
                    log(TAG) { "Connected device: ${device.name} - ${device.address}" }
                }
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Error loading connected devices: ${e.asLog()}" }
        }

        return devices
    }

    private fun BluetoothDevice.isConnected(): Boolean = try {
        val method = this.javaClass.getMethod("isConnected")
        method.invoke(this) as Boolean
    } catch (e: Exception) {
        log(TAG, WARN) { "Could not determine connection state for ${this.address}: ${e.asLog()}" }
        false
    }

    companion object {
        private val TAG = logTag("Bluetooth", "Repo")
    }
}