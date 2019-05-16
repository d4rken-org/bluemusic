package eu.darken.bluemusic.main.core.service.modules.volume

import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.database.DeviceManager
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent
import eu.darken.bluemusic.main.core.service.modules.VolumeModule
import eu.darken.bluemusic.settings.core.Settings
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

@BlueMusicServiceComponent.Scope
internal class VolumeUpdateModule @Inject constructor(
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

        val percentage = streamHelper.getVolumePercentage(id)
        deviceManager.devices()
                .take(1)
                .flatMapIterable { it.values }
                .filter { it.isActive && !it.volumeLock && it.getStreamType(id) != null && it.getVolume(it.getStreamType(id)!!) != null }
                .map { device ->
                    device.setVolume(device.getStreamType(id)!!, percentage)
                    return@map device
                }
                .toList()
                .subscribe { actives ->
                    deviceManager.save(actives)
                            .subscribeOn(Schedulers.computation())
                            .subscribe()
                }
    }
}
