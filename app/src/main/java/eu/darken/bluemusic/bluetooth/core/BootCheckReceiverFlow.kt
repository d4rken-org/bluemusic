package eu.darken.bluemusic.bluetooth.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.darken.bluemusic.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class BootCheckReceiverFlow : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Timber.v("onReceive(%s, %s)", context, intent)
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.action)) {
            Timber.e("Triggered with unknown intent: %s", intent)
            return
        }

        val appComponent = (context.applicationContext as App).appComponent
        val settings = appComponent.settings()
        val bluetoothSource = appComponent.bluetoothSourceFlow()
        val eventGenerator = appComponent.eventGenerator()
        val deviceRepository = appComponent.deviceRepository()
        val dispatcherProvider = appComponent.dispatcherProvider()

        if (!settings.isEnabled) {
            Timber.i("We are disabled.")
            return
        }
        if (!settings.isBootRestoreEnabled) {
            Timber.i("Restoring on boot is disabled.")
            return
        }

        Timber.d("We just completed booting, let's see if any Bluetooth device is connected...")
        val pendingResult = goAsync()
        
        val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
        scope.launch {
            try {
                // Wait a bit for Bluetooth to stabilize after boot
                delay(3000)
                
                val connectedDevices = bluetoothSource.connectedDevices.first()
                
                if (connectedDevices.isEmpty()) {
                    Timber.d("No devices were connected on boot.")
                    return@launch
                }
                
                Timber.d("We booted with already connected devices: %s", connectedDevices)
                
                val managedConnectedDevices = mutableListOf<SourceDevice>()
                val managedDevices = deviceRepository.getAllDevices().first()
                
                for ((address, device) in connectedDevices) {
                    if (managedDevices.any { it.address == address }) {
                        managedConnectedDevices.add(device)
                    }
                }
                
                if (managedConnectedDevices.isEmpty()) {
                    Timber.i("Connected devices are not managed by us.")
                    return@launch
                }
                
                Timber.i("Generating connected events for already connected devices %s", managedConnectedDevices)
                for (device in managedConnectedDevices) {
                    eventGenerator.send(device, SourceDevice.Event.Type.CONNECTED)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error during boot check")
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}