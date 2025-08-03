package eu.darken.bluemusic.monitor.core.modules.volume

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeDisconnectModule @Inject constructor(
    private val streamHelper: StreamHelper,
    private val deviceRepo: DeviceRepo,
) : ConnectionModule {

    override val tag: String
        get() = TAG

    override val priority: Int = 1 // Run early to capture volumes before other modules

    override suspend fun handle(event: DeviceEvent) {
        if (event !is DeviceEvent.Disconnected) return

        val device = event.device

        if (!device.volumeSaveOnDisconnect) {
            log(TAG, VERBOSE) { "Device ${device.label} does not have 'save on disconnect' enabled" }
            return
        }

        log(TAG, INFO) { "Saving volumes on disconnect for device ${device.label}" }

        val volumeUpdates = buildMap {
            AudioStream.Type.entries.forEach { streamType ->
                val currentVolume = device.getVolume(streamType)
                if (currentVolume == null) return@forEach

                val streamId = device.getStreamId(streamType)
                val actualVolume = streamHelper.getVolumePercentage(streamId)
                put(streamType, actualVolume)
                log(TAG, VERBOSE) { "Capturing $streamType volume: $actualVolume for ${device.label}" }
            }
        }

        if (volumeUpdates.isEmpty()) {
            log(TAG, VERBOSE) { "No volume settings to save for ${device.label}" }
            return
        }

        deviceRepo.updateDevice(device.address) { oldConfig ->
            volumeUpdates.entries.fold(oldConfig) { config, (streamType, volume) ->
                config.updateVolume(streamType, volume)
            }
        }

        log(TAG, INFO) { "Saved ${volumeUpdates.size} volume settings on disconnect for ${device.label}: $volumeUpdates" }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: VolumeDisconnectModule): ConnectionModule
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "Disconnect", "Module")
    }
}