package eu.darken.bluemusic.main.core.service.modules.volume

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.service.modules.VolumeModule
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

    companion object {
        private val TAG = logTag("VolumeUpdateModuleFlow")
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.IO)

    override suspend fun handle(id: AudioStream.Id, volume: Int) {
        if (!settings.volumeListening.value()) {
            log(TAG, VERBOSE) { "Volume listener is disabled." }
            return
        }
        if (streamHelper.wasUs(id, volume)) {
            log(TAG, VERBOSE) { "Volume change was triggered by us, ignoring it." }
            return
        }

        val percentage = streamHelper.getVolumePercentage(id)

        scope.launch {
            try {
                val devices = deviceRepo.currentDevices()

                devices
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
}