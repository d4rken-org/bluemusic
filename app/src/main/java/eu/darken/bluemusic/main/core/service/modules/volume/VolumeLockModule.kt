package eu.darken.bluemusic.main.core.service.modules.volume

import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.data.device.DeviceManagerFlow
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.service.modules.VolumeModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@AppComponent.Scope
internal class VolumeLockModule @Inject constructor(
        private val streamHelper: StreamHelper,
        private val deviceManager: DeviceManagerFlow
) : VolumeModule() {

    override fun handle(id: AudioStream.Id, volume: Int) {
        if (streamHelper.wasUs(id, volume)) {
            Timber.v("Volume change was triggered by us, ignoring it.")
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
                        Timber.d("Device %s has no specified target volume for %s, skipping volume lock.", device, type)
                        return@forEach
                    }

                    if (streamHelper.changeVolume(device.getStreamId(type), percentage, false, 0)) {
                        Timber.d("Engaged volume lock for %s and due to %s", type, device)
                    }
                }
        }
    }
}
