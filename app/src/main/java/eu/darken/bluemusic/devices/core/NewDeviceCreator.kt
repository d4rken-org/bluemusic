package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewDeviceCreator @Inject constructor(
    private val deviceRepo: DeviceRepo,
    private val volumeTool: VolumeTool,
    private val bluetoothRepo: BluetoothRepo,
) {

    suspend fun createNewdevice(address: DeviceAddr) {
        log(TAG, INFO) { "createNewdevice: $address" }

        val device = bluetoothRepo.state
            .filter { it.isReady }
            .first()
            .devices!!
            .find { it.address == address } ?: throw IllegalStateException("Device not found: $address")

        var config = DeviceConfigEntity(
            address = address,
            lastConnected = System.currentTimeMillis(),
            musicVolume = volumeTool.getVolumePercentage(AudioStream.Id.STREAM_MUSIC),
        )

        if (device.deviceType == SourceDevice.Type.PHONE_SPEAKER) {
            config = config.copy(
                volumeSaveOnDisconnect = true,
            )
        }
        deviceRepo.updateDevice(address) {
            config
        }
        log(TAG) { "Created new device config: $address" }

    }

    companion object {
        private val TAG = logTag("Devices", "Creator")
    }
}