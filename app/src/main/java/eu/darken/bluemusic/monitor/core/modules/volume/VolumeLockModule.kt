package eu.darken.bluemusic.monitor.core.modules.volume

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class VolumeLockModule @Inject constructor(
    private val streamHelper: StreamHelper,
    private val deviceRepo: DeviceRepo,
) : VolumeModule {

    override val tag: String
        get() = TAG

    override suspend fun handle(event: VolumeEvent) {
        val id = event.streamId
        val volume = event.newVolume
        
        if (streamHelper.wasUs(id, volume)) {
            log(TAG, VERBOSE) { "Volume change was triggered by us, ignoring it." }
            return
        }

        deviceRepo.currentDevices()
            .filter { device -> device.isActive && device.volumeLock && device.getStreamType(id) != null }
            .forEach { device ->
                val type = device.getStreamType(id)!!
                val percentage: Float? = device.getVolume(type)
                if (percentage == null) {
                    log(TAG) { "Device $device has no specified target volume for $type, skipping volume lock." }
                    return@forEach
                }

                if (streamHelper.changeVolume(device.getStreamId(type), percentage)) {
                    log(TAG) { "Engaged volume lock for $type and due to $device" }
                }
            }

    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: VolumeLockModule): VolumeModule
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "Lock", "Module")
    }
}
