package eu.darken.bluemusic.bluetooth.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.bluemusic.common.EventGenerator
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.datastore.valueBlocking
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCheckReceiverFlow : BroadcastReceiver() {

    companion object {
        private val TAG = logTag("BootCheckReceiverFlow")
    }

    @Inject lateinit var devicesSettings: DevicesSettings
    @Inject lateinit var bluetoothSource: BluetoothRepo
    @Inject lateinit var eventGenerator: EventGenerator
    @Inject lateinit var deviceRepo: DeviceRepo
    @Inject lateinit var dispatcherProvider: DispatcherProvider

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG, VERBOSE) { "onReceive($context, $intent)" }
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.action)) {
            log(TAG, ERROR) { "Triggered with unknown intent: $intent" }
            return
        }

        // Dependencies are injected by Hilt

        if (!devicesSettings.isEnabled.valueBlocking) {
            log(TAG, INFO) { "We are disabled." }
            return
        }
        if (!devicesSettings.restoreOnBoot.valueBlocking) {
            log(TAG, INFO) { "Restoring on boot is disabled." }
            return
        }

        log(TAG) { "We just completed booting, let's see if any Bluetooth device is connected..." }
        val pendingResult = goAsync()

        val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.IO)
        scope.launch {
            try {
                // Wait a bit for Bluetooth to stabilize after boot
                delay(3000)
                
                val connectedDevices = bluetoothSource.connectedDevices.first()
                
                if (connectedDevices.isEmpty()) {
                    log(TAG) { "No devices were connected on boot." }
                    return@launch
                }

                log(TAG) { "We booted with already connected devices: $connectedDevices" }
                
                val managedConnectedDevices = mutableListOf<SourceDevice>()
                val managedDevices = deviceRepo.devices.first()

                connectedDevices.forEach { (address, device) ->
                    if (managedDevices.any { it.address == address }) {
                        managedConnectedDevices.add(device)
                    }
                }
                
                if (managedConnectedDevices.isEmpty()) {
                    log(TAG, INFO) { "Connected devices are not managed by us." }
                    return@launch
                }

                log(TAG, INFO) { "Generating connected events for already connected devices $managedConnectedDevices" }
                for (device in managedConnectedDevices) {
                    eventGenerator.send(device, SourceDevice.Event.Type.CONNECTED)
                }
                
            } catch (e: Exception) {
                log(TAG, ERROR) { "Error during boot check: ${e.asLog()}" }
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}