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
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
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

    private lateinit var context: Context

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG, VERBOSE) { "onReceive($context, $intent)" }

        if (!VALID_ACTIONS.contains(intent.action ?: "")) {
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
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                log(TAG, INFO) { "Device has connected: $bluetoothDevice" }

            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                log(TAG, INFO) { "Device has disconnected: $bluetoothDevice" }

            }

            else -> {
                log(TAG, WARN) { "Invalid action: ${intent.action}" }
                null
            }
        }


        val devices = deviceRepo.currentDevices()
        log(TAG, DEBUG) { "Current devices: $devices" }

        val managedDevice = devices.singleOrNull { device -> device.address == bluetoothDevice.address }
        if (managedDevice == null) {
            log(TAG, DEBUG) { "Eventbelongs to an un-managed device" }
            return
        }

        log(TAG, DEBUG) { "Event concerns device $managedDevice" }

        // If we are changing from speaker to bluetooth this routine tries to save the original volume
//        if (devicesSettings.speakerAutoSave.value() &&
//            deviceEvent.address != FakeSpeakerDevice.ADDRESS &&
//            deviceEvent.type == SourceDevice.Event.Type.CONNECTED
//        ) {
//            handleSpeakerAutoSave(devices)
//        }

        // Specific event handling for disconnect (save current volumes)
//        if (deviceEvent.type == SourceDevice.Event.Type.DISCONNECTED) {
//            handleDisconnect(managedDevice, streamHelper, deviceRepo)
//        }

        // Forward the event to the service
//        val serviceIntent = Intent(context, BlueMusicService::class.java).apply {
//            putExtra(EXTRA_DEVICE_EVENT, deviceEvent)
//        }

        // TODO
//        ServiceController.startService(context, serviceIntent)
    }

    private suspend fun handleSpeakerAutoSave(
        devices: Collection<ManagedDevice>,
    ) {
//        var updatedDevice = devices.singleOrNull { it.address == FakeSpeakerDevice.ADDRESS } ?: run {
//            log(TAG, INFO) { "FakeSpeaker device not yet managed, adding." }
//            deviceRepo.createDevice(speakerDevice.address)
//            val newDevice = deviceRepo.devices.first().single { it.address == speakerDevice.address }
//            newDevice
//        }
//
//        // Are we actually replacing the fake speaker device and need to save the volume?
//        val activeDevices = devices.filter { it.isActive }
//        if (activeDevices.size >= 2 && !activeDevices.any { it.address == FakeSpeakerDevice.ADDRESS }) {
//            log(TAG, DEBUG) { "We are switching to a non-speaker device from speaker, skipping speaker save." }
//            return
//        }
//
//        // Save current volumes to fake speaker
//        log(TAG, DEBUG) { "Saving current speaker volumes." }
//        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
//        for (id in AudioStream.Id.entries) {
//            // Skip certain stream types for auto-save
//            if (id == AudioStream.Id.STREAM_BLUETOOTH_HANDSFREE || id == AudioStream.Id.STREAM_VOICE_CALL) continue
//
//            val volumeInt = audioManager.getStreamVolume(id.id)
//            if (volumeInt == 0) {
//                log(TAG, VERBOSE) { "Speaker volume for $id is at $volumeInt, not saving mute." }
//                continue
//            }
//
//            val volumePercent = streamHelper.getVolumePercentage(id)
//            log(TAG, VERBOSE) { "Speaker volume for $id is at $volumeInt ($volumePercent)" }
//            // Map Id to Type
//            val type = when (id) {
//                AudioStream.Id.STREAM_MUSIC -> AudioStream.Type.MUSIC
//                AudioStream.Id.STREAM_VOICE_CALL -> AudioStream.Type.CALL
//                AudioStream.Id.STREAM_RINGTONE -> AudioStream.Type.RINGTONE
//                AudioStream.Id.STREAM_NOTIFICATION -> AudioStream.Type.NOTIFICATION
//                AudioStream.Id.STREAM_ALARM -> AudioStream.Type.ALARM
//                else -> continue
//            }
//            updatedDevice = updatedDevice.copy(
//                config = updatedDevice.config.updateVolume(type, volumePercent)
//            )
//        }
//
//        updatedDevice = updatedDevice.copy(
//            config = updatedDevice.config.copy(lastConnected = System.currentTimeMillis())
//        )
//        deviceRepo.updateDevice(updatedDevice.address) { oldConfig ->
//            updatedDevice.config
//        }
    }

    private suspend fun handleDisconnect(
        managedDevice: ManagedDevice,
        streamHelper: StreamHelper,
        deviceRepo: DeviceRepo,
    ) {
        log(TAG, DEBUG) { "Handling disconnect for $managedDevice" }
        var updatedDevice = managedDevice
        // Save current volumes
        for (id in AudioStream.Id.entries) {
            // Map Id to Type
            val type = when (id) {
                AudioStream.Id.STREAM_MUSIC -> AudioStream.Type.MUSIC
                AudioStream.Id.STREAM_VOICE_CALL -> AudioStream.Type.CALL
                AudioStream.Id.STREAM_RINGTONE -> AudioStream.Type.RINGTONE
                AudioStream.Id.STREAM_NOTIFICATION -> AudioStream.Type.NOTIFICATION
                AudioStream.Id.STREAM_ALARM -> AudioStream.Type.ALARM
                else -> continue
            }

            if (managedDevice.getVolume(type) == null) continue

            val volumePercent = streamHelper.getVolumePercentage(id)
            log(TAG, VERBOSE) { "Current volume for $id is $volumePercent" }
            updatedDevice = updatedDevice.copy(
                config = updatedDevice.config.updateVolume(type, volumePercent)
            )
        }

        updatedDevice = updatedDevice.copy(
            config = updatedDevice.config.copy(lastConnected = System.currentTimeMillis())
        )
        deviceRepo.updateDevice(updatedDevice.address) { oldConfig ->
            updatedDevice.config
        }
    }

    companion object {
        private val TAG = logTag("BluetoothEventReceiver")
        const val EXTRA_DEVICE_EVENT = "eu.darken.bluemusic.core.bluetooth.event"
        val VALID_ACTIONS = listOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED
        )
    }
}