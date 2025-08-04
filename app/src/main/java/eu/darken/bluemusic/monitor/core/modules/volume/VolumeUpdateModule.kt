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
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeUpdateModule @Inject constructor(
    private val volumeTool: VolumeTool,
    private val deviceRepo: DeviceRepo,
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

        val activeDevices = deviceRepo.currentDevices().filter { it.isActive }
        log(TAG) { "Active devices (${activeDevices.size}): $activeDevices" }

        val percentage = volumeTool.getVolumePercentage(id)

        val now = Instant.now()
        activeDevices
            .filter { Duration.between(it.lastConnected, now) > it.actionDelay }
            .filter { it.volumeObserving && !it.volumeLock }
            .filter { dev -> dev.getStreamType(id) != null && dev.getVolume(dev.getStreamType(id)!!) != null }
            .forEach { dev ->
                log(TAG, INFO) { "Saving new volume ($percentage@$id) for $dev" }
                deviceRepo.updateDevice(dev.address) { oldConfig ->
                    oldConfig.updateVolume(dev.getStreamType(id)!!, percentage)
                }
            }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: VolumeUpdateModule): VolumeModule
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "Update", "Module")
    }

}