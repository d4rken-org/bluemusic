package eu.darken.bluemusic.bluetooth.core

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.bluemusic.bluetooth.core.speaker.SpeakerDeviceProvider
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.datastore.valueBlocking
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeDisconnectModule
import eu.darken.bluemusic.monitor.core.worker.MonitorControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothEventReceiver : BroadcastReceiver() {

    @Inject lateinit var devicesSettings: DevicesSettings
    @Inject lateinit var streamHelper: StreamHelper
    @Inject lateinit var speakerDeviceProvider: SpeakerDeviceProvider
    @Inject lateinit var deviceRepo: DeviceRepo
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var monitorControl: MonitorControl
    @Inject lateinit var volumeDisconnectModule: VolumeDisconnectModule

    private lateinit var context: Context
    private val validActions = listOf(
        BluetoothDevice.ACTION_ACL_CONNECTED,
        BluetoothDevice.ACTION_ACL_DISCONNECTED
    )

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG, VERBOSE) { "onReceive($context, $intent)" }

        if (!validActions.contains(intent.action ?: "")) {
            log(TAG, ERROR) { "We got called on an invalid intent: $intent" }
            return
        }

        if (!devicesSettings.isEnabled.valueBlocking) {
            log(TAG, INFO) { "We are disabled." }
            return
        }
        this.context = context
        val pendingResult = goAsync()

        val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.IO)
        scope.launch {
            try {
                handleEvent(intent)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Error handling bluetooth event: ${e.asLog()}" }
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private suspend fun handleEvent(intent: Intent) {
        val bluetoothDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (bluetoothDevice == null) {
            log(TAG, WARN) { "Intent didn't contain a bluetooth device!" }
            return
        }

        val devices = deviceRepo.currentDevices()
        log(TAG, DEBUG) { "Current devices: $devices" }

        val managedDevice = devices.singleOrNull { device -> device.address == bluetoothDevice.address }
        if (managedDevice == null) {
            log(TAG, DEBUG) { "Event belongs to an un-managed device" }
            return
        }

        log(TAG, DEBUG) { "Event concerns device $managedDevice" }

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                log(TAG, INFO) { "Device has connected: $bluetoothDevice" }
                monitorControl.startMonitor()
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                log(TAG, INFO) { "Device has disconnected: $bluetoothDevice" }

                // Save volumes before the device fully disconnects
                if (managedDevice.volumeSaveOnDisconnect) {
                    try {
                        volumeDisconnectModule.saveVolumesOnDisconnect(managedDevice)
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "Error saving volumes on disconnect: ${e.asLog()}" }
                    }
                }

                monitorControl.startMonitor()
            }

            else -> {
                log(TAG, WARN) { "Invalid action: ${intent.action}" }
            }
        }
    }

    companion object {
        private val TAG = logTag("Bluetooth", "EventReceiver")
    }
}