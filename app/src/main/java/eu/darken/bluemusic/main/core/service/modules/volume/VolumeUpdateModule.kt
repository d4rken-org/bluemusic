package eu.darken.bluemusic.main.core.service.modules.volume

import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.database.DeviceManager
import eu.darken.bluemusic.main.core.database.ManagedDevice
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent
import eu.darken.bluemusic.main.core.service.modules.VolumeModule
import eu.darken.bluemusic.settings.core.Settings
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.*
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
                .map { deviceMap ->
                    val active = HashSet<ManagedDevice>()
                    for (d in deviceMap.values) {
                        if (d.isActive
                                && !d.volumeLock
                                && d.getStreamType(id) != null
                                && d.getVolume(d.getStreamType(id)!!) != null
                        ) {
                            active.add(d)
                        }
                    }
                    active
                }
                .filter { managedDevices -> managedDevices.isNotEmpty() }
                .take(1)
                .flatMapIterable { managedDevices -> managedDevices }
                .map { device ->
                    device.setVolume(device.getStreamType(id)!!, percentage)
                    device
                }
                .toList()
                .subscribe { actives ->
                    deviceManager.save(actives)
                            .subscribeOn(Schedulers.computation())
                            .subscribe()
                }
    }
}
