package eu.darken.bluemusic.main.core.service.modules.volume

import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.database.DeviceManager
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent
import eu.darken.bluemusic.main.core.service.modules.VolumeModule
import eu.darken.bluemusic.settings.core.Settings
import timber.log.Timber
import javax.inject.Inject

@BlueMusicServiceComponent.Scope
internal class VolumeLockModule @Inject constructor(
        private val streamHelper: StreamHelper,
        private val settings: Settings,
        private val deviceManager: DeviceManager
) : VolumeModule() {

    override fun handle(id: AudioStream.Id, volume: Int) {
        if (!settings.isVolumeChangeListenerEnabled) {
            Timber.v("Volume listener is disabled.")
            return
        }
        if (streamHelper.wasUs(id, volume)) {
            Timber.v("Volume change was triggered by us, ignoring it.")
            return
        }

        deviceManager.devices()
                .take(1)
                .flatMapIterable { it.values }
                .filter { it.isActive && it.volumeLock && it.getStreamType(id) != null }
                .subscribe { device ->
                    val type = device.getStreamType(id)!!
                    val percentage: Float? = device.getVolume(type)
                    if (percentage == null || percentage == -1f) {
                        Timber.d("Device %s has no specified target volume for %s, skipping volume lock.", device, type)
                        return@subscribe
                    }

                    if (streamHelper.changeVolume(device.getStreamId(type), percentage, false, 0)) {
                        Timber.d("Engaged volume lock for %s and due to %s", type, device)
                    }
                }
    }
}
