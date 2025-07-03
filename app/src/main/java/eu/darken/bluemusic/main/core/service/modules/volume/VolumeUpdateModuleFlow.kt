package eu.darken.bluemusic.main.core.service.modules.volume

import eu.darken.bluemusic.common.coroutines.DispatcherProvider
import eu.darken.bluemusic.data.device.DeviceManagerFlow
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent
import eu.darken.bluemusic.main.core.service.modules.VolumeModule
import eu.darken.bluemusic.settings.core.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@BlueMusicServiceComponent.Scope
internal class VolumeUpdateModuleFlow @Inject constructor(
    private val streamHelper: StreamHelper,
    private val settings: Settings,
    private val deviceManager: DeviceManagerFlow,
    private val dispatcherProvider: DispatcherProvider
) : VolumeModule() {
    
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)

    override fun handle(id: AudioStream.Id, volume: Int) {
        if (!settings.isVolumeChangeListenerEnabled) {
            Timber.v("Volume listener is disabled.")
            return
        }
        if (streamHelper.wasUs(id, volume)) {
            Timber.v("Volume change was triggered by us, ignoring it.")
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
                        device.setVolume(device.getStreamType(id)!!, percentage)
                        deviceManager.updateDevice(device)
                    }
                    
            } catch (e: Exception) {
                Timber.e(e, "Failed to update volume.")
            }
        }
    }
}