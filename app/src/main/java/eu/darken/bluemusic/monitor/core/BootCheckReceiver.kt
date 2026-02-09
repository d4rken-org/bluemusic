package eu.darken.bluemusic.monitor.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.currentState
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
import eu.darken.bluemusic.monitor.core.service.MonitorControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCheckReceiver : BroadcastReceiver() {

    @Inject lateinit var devicesSettings: DevicesSettings
    @Inject lateinit var bluetoothSource: BluetoothRepo
    @Inject lateinit var deviceRepo: DeviceRepo
    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var monitorControl: MonitorControl

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG, VERBOSE) { "onReceive($context, $intent)" }
        if (Intent.ACTION_BOOT_COMPLETED != intent.action) {
            log(TAG, ERROR) { "Triggered with unknown intent: $intent" }
            return
        }

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

                val state = bluetoothSource.currentState()

                if (!state.isReady) {
                    log(TAG) { "Bluetooth is not in a ready state: $state" }
                    return@launch
                }

                val connectedDevices = deviceRepo.devices.first().filter { it.isActive }
                log(TAG) { "We booted with already connected devices: $connectedDevices" }

                if (connectedDevices.isEmpty()) {
                    log(TAG, INFO) { "Connected devices are not managed by us." }
                    return@launch
                }

                monitorControl.startMonitor()
            } catch (e: Exception) {
                log(TAG, ERROR) { "Error during boot check: ${e.asLog()}" }
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        private val TAG = logTag("BootCheckReceiver")
    }
}