package eu.darken.bluemusic.monitor.core.modules.volume

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeDisconnectModule @Inject constructor(
    private val streamHelper: StreamHelper,
    private val deviceRepo: DeviceRepo,
) {

    suspend fun saveVolumesOnDisconnect(device: ManagedDevice) {
        if (!device.volumeSaveOnDisconnect) {
            log(TAG, VERBOSE) { "Device ${device.label} does not have 'save on disconnect' enabled" }
            return
        }

        log(TAG, INFO) { "Saving volumes on disconnect for device ${device.label}" }

        val volumeUpdates = mutableMapOf<AudioStream.Type, Float>()

        AudioStream.Type.entries.forEach { streamType ->
            val currentVolume = device.getVolume(streamType)
            if (currentVolume != null && currentVolume != -1f) {
                val streamId = device.getStreamId(streamType)
                val actualVolume = streamHelper.getVolumePercentage(streamId)
                volumeUpdates[streamType] = actualVolume
                log(TAG, VERBOSE) { "Capturing $streamType volume: $actualVolume for ${device.label}" }
            }
        }

        if (volumeUpdates.isNotEmpty()) {
            deviceRepo.updateDevice(device.address) { oldConfig ->
                var updatedConfig = oldConfig
                volumeUpdates.forEach { (streamType, volume) ->
                    updatedConfig = updatedConfig.updateVolume(streamType, volume)
                }
                updatedConfig
            }
            log(TAG, INFO) { "Saved ${volumeUpdates.size} volume settings on disconnect for ${device.label}" }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "Disconnect", "Module")
    }
}