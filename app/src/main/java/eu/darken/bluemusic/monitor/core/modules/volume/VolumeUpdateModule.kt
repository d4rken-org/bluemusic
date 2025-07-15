package eu.darken.bluemusic.monitor.core.modules.volume

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeUpdateModule @Inject constructor(
    private val streamHelper: StreamHelper,
    private val settings: DevicesSettings,
    private val deviceRepo: DeviceRepo,
    dispatcherProvider: DispatcherProvider
) : VolumeModule {

    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.IO)

    override suspend fun handle(id: AudioStream.Id, volume: Int) {
        val activeDevices = deviceRepo.currentDevices().filter { it.isActive }
        log(TAG) { "Active devices (${activeDevices.size}): $activeDevices" }
        if (!activeDevices.none { it.volumeObserving }) {
            log(TAG, VERBOSE) { "No active device has volume listening enabled." }
            return
        }

        if (streamHelper.wasUs(id, volume)) {
            log(TAG, VERBOSE) { "Volume change was triggered by us, ignoring it." }
            return
        }

        activeDevices.any { it.lastConnected }

        val percentage = streamHelper.getVolumePercentage(id)

        scope.launch {
            try {
                activeDevices
                    .filter { device ->
                        device.isActive &&
                                !device.volumeLock &&
                                device.getStreamType(id) != null &&
                                device.getVolume(device.getStreamType(id)!!) != null
                    }
                    .forEach { device ->
                        deviceRepo.updateDevice(device.address) { oldConfig ->
                            oldConfig.updateVolume(device.getStreamType(id)!!, percentage)
                        }
                    }

            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to update volume: ${e.asLog()}" }
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