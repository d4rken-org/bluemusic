package eu.darken.bluemusic.main.core.service.modules.volume

import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.database.DeviceManager
import eu.darken.bluemusic.main.core.database.ManagedDevice
import eu.darken.bluemusic.main.core.service.BlueMusicServiceComponent
import eu.darken.bluemusic.main.core.service.modules.VolumeModule
import eu.darken.bluemusic.settings.core.Settings
import timber.log.Timber
import java.util.*
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
                .map { deviceMap ->
                    val active = HashSet<ManagedDevice>()
                    for (d in deviceMap.values) {
                        if (d.isActive && d.volumeLock && d.getStreamType(id) != null) {
                            active.add(d)
                        }
                    }
                    active
                }
                .filter { managedDevices -> managedDevices.isNotEmpty() }
                .take(1)
                .flatMapIterable { managedDevices -> managedDevices }
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
