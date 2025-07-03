package eu.darken.bluemusic.bluetooth.core

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.darken.bluemusic.App
import eu.darken.bluemusic.data.device.DeviceManagerFlow
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.database.ManagedDevice
import eu.darken.bluemusic.main.core.service.BlueMusicServiceFlow
import eu.darken.bluemusic.main.core.service.ServiceHelper
import eu.darken.bluemusic.settings.core.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import timber.log.Timber

class BluetoothEventReceiverFlow : BroadcastReceiver() {

    companion object {
        const val EXTRA_DEVICE_EVENT = "eu.darken.bluemusic.core.bluetooth.event"
        val VALID_ACTIONS = listOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        Timber.v("onReceive(%s, %s)", context, intent)

        if (!VALID_ACTIONS.contains(intent.action ?: "")) {
            Timber.e("We got called on an invalid intent: %s", intent)
            return
        }

        val appComponent = (context.applicationContext as App).appComponent
        val settings = appComponent.settings()
        val streamHelper = appComponent.streamHelper()
        val fakeSpeakerDevice = appComponent.fakeSpeakerDevice()
        val deviceManager = appComponent.deviceManagerFlow()
        val dispatcherProvider = appComponent.dispatcherProvider()

        if (!settings.isEnabled) {
            Timber.i("We are disabled.")
            return
        }

        val pendingResult = goAsync()
        
        val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
        scope.launch {
            try {
                handleEvent(context, intent, settings, streamHelper, fakeSpeakerDevice, deviceManager)
            } catch (e: Exception) {
                Timber.e(e, "Error handling bluetooth event")
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
    
    private suspend fun handleEvent(
        context: Context, 
        intent: Intent,
        settings: Settings,
        streamHelper: StreamHelper,
        fakeSpeakerDevice: FakeSpeakerDevice,
        deviceManager: DeviceManagerFlow
    ) {
        val deviceEvent = SourceDevice.Event.createEvent(intent)
        if (deviceEvent == null) {
            Timber.e("Couldn't create device event for $intent")
            return
        }
        
        Timber.d("New event: %s", deviceEvent)
        
        val devices = deviceManager.devices().first()
        Timber.d("Current devices: %s", devices)
        
        val managedDevice = devices[deviceEvent.address]
        if (managedDevice == null) {
            Timber.d("Event %s belongs to an un-managed device", deviceEvent)
            return
        }
        
        Timber.d("Event %s concerns device %s", deviceEvent, managedDevice)
        
        // If we are changing from speaker to bluetooth this routine tries to save the original volume
        if (settings.isSpeakerAutoSaveEnabled && 
            deviceEvent.address != FakeSpeakerDevice.ADDR && 
            deviceEvent.type == SourceDevice.Event.Type.CONNECTED) {
            
            handleSpeakerAutoSave(devices, streamHelper, fakeSpeakerDevice, deviceManager)
        }
        
        // Specific event handling for disconnect (save current volumes)
        if (deviceEvent.type == SourceDevice.Event.Type.DISCONNECTED) {
            handleDisconnect(managedDevice, streamHelper, deviceManager)
        }
        
        // Forward the event to the service
        val serviceIntent = Intent(context, BlueMusicServiceFlow::class.java).apply {
            putExtra(EXTRA_DEVICE_EVENT, deviceEvent)
        }
        
        ServiceHelper(context).run(serviceIntent)
    }
    
    private suspend fun handleSpeakerAutoSave(
        devices: Map<String, ManagedDevice>,
        streamHelper: StreamHelper,
        fakeSpeakerDevice: FakeSpeakerDevice,
        deviceManager: DeviceManagerFlow
    ) {
        val fakeSpeaker = devices[FakeSpeakerDevice.ADDR] ?: run {
            Timber.i("FakeSpeaker device not yet managed, adding.")
            val newDevice = ManagedDevice(FakeSpeakerDevice.ADDR, fakeSpeakerDevice.label)
            deviceManager.updateDevice(newDevice)
            newDevice
        }
        
        // Are we actually replacing the fake speaker device and need to save the volume?
        val activeDevices = devices.values.filter { it.isActive }
        if (activeDevices.size >= 2 && !activeDevices.any { it.address == FakeSpeakerDevice.ADDR }) {
            Timber.d("We are switching to a non-speaker device from speaker, skipping speaker save.")
            return
        }
        
        // Save current volumes to fake speaker
        Timber.d("Saving current speaker volumes.")
        for (id in AudioStream.Id.values()) {
            if (id.ignoreAutoSave()) continue
            
            val volumeInt = streamHelper.getVolumeInt(id)
            if (volumeInt == 0) {
                Timber.v("Speaker volume for %s is at %d, not saving mute.", id, volumeInt)
                continue
            }
            
            val volumePercent = streamHelper.getVolumePercentage(id)
            Timber.v("Speaker volume for %s is at %d (%d)", id, volumeInt, volumePercent)
            fakeSpeaker.setVolume(id, volumePercent)
        }
        
        fakeSpeaker.lastConnected = System.currentTimeMillis()
        deviceManager.updateDevice(fakeSpeaker)
    }
    
    private suspend fun handleDisconnect(
        managedDevice: ManagedDevice,
        streamHelper: StreamHelper,
        deviceManager: DeviceManagerFlow
    ) {
        Timber.d("Handling disconnect for %s", managedDevice)
        
        // Save current volumes
        for (id in AudioStream.Id.values()) {
            if (managedDevice.getStreamType(id) == null) continue
            
            val volumePercent = streamHelper.getVolumePercentage(id)
            Timber.v("Current volume for %s is %d", id, volumePercent)
            managedDevice.setVolume(id, volumePercent)
        }
        
        managedDevice.lastConnected = System.currentTimeMillis()
        deviceManager.updateDevice(managedDevice)
    }
}