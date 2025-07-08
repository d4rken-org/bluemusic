package eu.darken.bluemusic.main.core.service.modules.volume

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceManagerFlowAdapter
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.service.modules.VolumeModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class VolumeLockModule @Inject constructor(
        private val streamHelper: StreamHelper,
        private val deviceManager: DeviceManagerFlowAdapter
) : VolumeModule {

    companion object {
        private val TAG = logTag("VolumeLockModule")
    }

    override suspend fun handle(id: AudioStream.Id, volume: Int) {
        if (streamHelper.wasUs(id, volume)) {
            log(TAG, VERBOSE) { "Volume change was triggered by us, ignoring it." }
            return
        }

        runBlocking {
            deviceManager.devices()
                .first()
                .values
                .filter { device -> device.isActive && device.volumeLock && device.getStreamType(id) != null }
                .forEach { device ->
                    val type = device.getStreamType(id)!!
                    val percentage: Float? = device.getVolume(type)
                    if (percentage == null || percentage == -1f) {
                        log(TAG) { "Device $device has no specified target volume for $type, skipping volume lock." }
                        return@forEach
                    }

                    if (streamHelper.changeVolume(device.getStreamId(type), percentage, false, 0)) {
                        log(TAG) { "Engaged volume lock for $type and due to $device" }
                    }
                }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: VolumeLockModule): VolumeModule
    }
}
