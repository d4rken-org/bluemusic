package eu.darken.bluemusic.main.core.service.modules.volume

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.service.modules.VolumeModule
import eu.darken.bluemusic.main.core.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.devices.core.DeviceManagerFlowAdapter
import javax.inject.Inject

import javax.inject.Singleton

@Singleton
class VolumeUpdateModuleFlow @Inject constructor(
    private val streamHelper: StreamHelper,
    private val settings: Settings,
    private val deviceManager: DeviceManagerFlowAdapter,
    private val dispatcherProvider: DispatcherProvider
) : VolumeModule {

    companion object {
        private val TAG = logTag("VolumeUpdateModuleFlow")
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.IO)

    override fun handle(id: AudioStream.Id, volume: Int) {
        if (!settings.isVolumeChangeListenerEnabled) {
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
                val devices = deviceManager.devices().first()
                
                devices.values
                    .filter { device ->
                        device.isActive && 
                        !device.volumeLock && 
                        device.getStreamType(id) != null && 
                        device.getVolume(device.getStreamType(id)!!) != null
                    }
                    .forEach { device ->
                        deviceManager.updateDevice(device.withUpdatedVolume(device.getStreamType(id)!!, percentage))
                    }
                    
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to update volume: ${e.asLog()}" }
            }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: VolumeUpdateModuleFlow): VolumeModule
    }
}