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
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeModeTool
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class VolumeLockModule @Inject constructor(
    private val volumeTool: VolumeTool,
    private val volumeModeTool: VolumeModeTool,
    private val deviceRepo: DeviceRepo,
    private val ownerRegistry: AudioStreamOwnerRegistry,
) : VolumeModule {

    override val tag: String
        get() = TAG

    override suspend fun handle(event: VolumeEvent) {
        val id = event.streamId
        val volume = event.newVolume

        if (volumeTool.wasUs(id, volume)) {
            log(TAG, VERBOSE) { "Volume change was triggered by us, ignoring it." }
            return
        }

        val ownerAddresses = ownerRegistry.ownerAddressesFor(id).toSet()
        if (ownerAddresses.isEmpty()) return

        deviceRepo.currentDevices()
            .filter { device ->
                device.isActive
                        && device.volumeLock
                        && device.address in ownerAddresses
                        && device.getStreamType(id) != null
            }
            .forEach { device ->
                val type = device.getStreamType(id)!!
                val rawFloat = device.getVolume(type) ?: return@forEach
                val mode = VolumeMode.fromFloat(rawFloat)

                if (mode == null) {
                    log(TAG) { "Device $device has corrupt volume $rawFloat for $type, skipping." }
                    return@forEach
                }

                if (volumeModeTool.apply(
                        streamId = device.getStreamId(type),
                        streamType = type,
                        volumeMode = mode,
                        visible = false,
                    )
                ) {
                    log(TAG) { "Engaged volume lock for $type ($mode) due to $device" }
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
