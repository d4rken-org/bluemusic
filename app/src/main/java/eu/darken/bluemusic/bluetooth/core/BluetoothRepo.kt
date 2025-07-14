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
import eu.darken.bluemusic.common.flow.replayingShare
import eu.darken.bluemusic.common.permissions.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BluetoothRepo @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val bluetoothManager: BluetoothManager,
    private val speakerDeviceProvider: SpeakerDeviceProvider,
    private val permissionHelper: PermissionHelper,
) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    val isEnabled: Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        trySendBlocking(state == BluetoothAdapter.STATE_ON)
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        trySend(bluetoothAdapter?.isEnabled ?: false)

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }
        .replayingShare(appScope + dispatcherProvider.IO)

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH])
    private fun BluetoothDevice.hasUUID(target: Int): Boolean = uuids.any {
        val uuid = it.uuid
        val value = (uuid.mostSignificantBits and 0x0000FFFF00000000L) ushr 32
        value.toInt() == target
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH])
    val pairedDevices: Flow<Set<SourceDevice>?> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                log(TAG) { "New event: $intent" }
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED,
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> trySend(Unit)

                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> trySend(Unit)

                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        log(TAG) { "Monitoring devices" }
        trySend(Unit)

        awaitClose {
            log(TAG) { "Stopping device monitoring" }
            context.unregisterReceiver(receiver)
        }
    }
        .map {
            val devices = mutableSetOf<SourceDevice>()

            if (bluetoothAdapter?.isEnabled != true) return@map devices

            if (!permissionHelper.hasBluetoothPermission()) return@map devices

            bluetoothAdapter.bondedDevices
                .filterNot { device ->
                    val isHealthDevice = device.hasUUID(0x1400)
                    if (isHealthDevice) log(TAG) { "Health devices are excluded: ${device.name} - ${device.address}" }
                    isHealthDevice
                }
                .forEach { device ->
                    val wrapper = SourceDeviceWrapper.from(realDevice = device, isActive = device.isConnected())
                    devices.add(wrapper)
                    log(TAG) { "Paired evice: ${wrapper.name} - ${wrapper.address} - isActive=${wrapper.isActive}" }

                }

            devices.add(speakerDeviceProvider.getSpeaker(isActive = devices.none { it.isActive }))

            devices.toSet() as Set<SourceDevice>?
        }
        .retryWhen { err, attempt ->
            if (err is SecurityException) {
                log(TAG, WARN) { "Can't load paired devices: $err" }
                emit(null)
                delay(3000)
                true
            } else {
                false
            }
        }
        .catch { err ->
            log(TAG, ERROR) { "Error loading paired devices: ${err.asLog()}" }
        }
        .replayingShare(appScope + dispatcherProvider.IO)


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