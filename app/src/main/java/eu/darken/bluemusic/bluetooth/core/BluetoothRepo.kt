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
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.devices.core.DeviceAddr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext


@Singleton
class BluetoothRepo @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val bluetoothManager: BluetoothManager,
    private val speakerDeviceProvider: SpeakerDeviceProvider,
    private val permissionHelper: PermissionHelper,
) {

    private val bluetoothAdapter: BluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val isBluetoothSupported: Boolean = BluetoothAdapter.getDefaultAdapter() != null

    private val isEnabled: Flow<Boolean> = callbackFlow {
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

        trySend(isBluetoothSupported && bluetoothAdapter.isEnabled)

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }
        .replayingShare(appScope + dispatcherProvider.IO)

    @SuppressLint("MissingPermission")
    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH])
    private val pairedDevices: Flow<Set<SourceDevice>?> = callbackFlow {
        if (!isBluetoothSupported) {
            log(TAG, WARN) { "Bluetooth hardware is not available" }
            throw IllegalStateException("Bluetooth hardware is not available")
        }

        if (!bluetoothAdapter.isEnabled) {
            log(TAG, WARN) { "Bluetooth is not enabled" }
            throw IllegalStateException("Bluetooth is not enabled")
        }

        if (!permissionHelper.hasBluetoothPermission()) {
            log(TAG, WARN) { "No bluetooth permission" }
            throw IllegalStateException("No bluetooth permission")
        }

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

            bluetoothAdapter.bondedDevices
                .filterNot { device ->
                    val isHealthDevice = device.hasUUID(0x1400)
                    if (isHealthDevice) log(TAG) { "Health devices are excluded: ${device.name} - ${device.address}" }
                    isHealthDevice
                }
                .forEach { device ->
                    val wrapper = SourceDeviceWrapper.from(realDevice = device, isConnected = device.isConnected())
                    devices.add(wrapper)
                    log(TAG) { "Paired evice: ${wrapper.name} - ${wrapper.address} - isConnected=${wrapper.isConnected}" }

                }

            devices.add(speakerDeviceProvider.getSpeaker(isConnected = true))

            devices.toSet() as Set<SourceDevice>?
        }
        .retryWhen { err, attempt ->
            log(TAG, WARN) { "Can't load paired devices: ${err.asLog()}" }
            emit(null)
            delay(3000)
            true
        }

    private val hasPermission = flow {
        while (coroutineContext.isActive) {
            emit(permissionHelper.hasBluetoothPermission())
            delay(3000)
        }
    }.distinctUntilChanged()

    data class State(
        val isEnabled: Boolean,
        val hasPermission: Boolean,
        val devices: Set<SourceDevice>?,
        val error: Throwable? = null,
    ) {
        val isReady = isEnabled && hasPermission && devices != null && error == null
    }

    val state = combine(
        isEnabled,
        hasPermission,
        pairedDevices,
    ) { enabled, permission, devices ->
        State(
            isEnabled = enabled,
            hasPermission = permission,
            devices = devices ?: emptySet(),
        )
    }
        .retry {
            log(TAG, ERROR) { "Error loading state" }
            delay(1000)
            true
        }
        .replayingShare(appScope + dispatcherProvider.IO)

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH])
    fun renameDevice(address: DeviceAddr, newName: String): Boolean {
        val realDevice = bluetoothAdapter.getRemoteDevice(address) ?: return false
        return if (hasApiLevel(31)) {
            @Suppress("NewApi")
            (realDevice.alias = newName)
            true
        } else {
            try {
                val method = realDevice.javaClass.getMethod("setAlias", String::class.java)
                method.invoke(realDevice, newName) as Boolean
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to set alias: ${e.asLog()}" }
                false
            }
        }
    }


    companion object {
        internal val TAG = logTag("Bluetooth", "Repo")
    }
}