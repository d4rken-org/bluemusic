package eu.darken.bluemusic.main.core.service.modules.events

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.database.ManagedDevice
import eu.darken.bluemusic.main.core.service.modules.EventModule
import timber.log.Timber

internal abstract class BaseMonitorModule(
        private val streamHelper: StreamHelper
) : EventModule() {

    abstract val type: AudioStream.Type

    override fun getPriority(): Int = 20

    override fun handle(device: ManagedDevice, event: SourceDevice.Event) {
        if (event.type != SourceDevice.Event.Type.CONNECTED) return

        val percentage = device.getVolume(type)
        Timber.d("Desired %s volume is %s", type, percentage)
        if (percentage == null) return

        if (!areRequirementsMet()) {
            Timber.d("Requirements not met!")
            return
        }

        if (percentage == -1f) {
            Timber.d("Device %s has no specified target volume yet, skipping adjustments.", device)
            return
        }

        val monitorDuration = device.monitoringDuration
        Timber.d("Monitor(%s) active for %dms.", type, monitorDuration)
        if (monitorDuration == null) return

        val targetTime = System.currentTimeMillis() + monitorDuration
        while (System.currentTimeMillis() < targetTime) {
            if (streamHelper.changeVolume(device.getStreamId(type), percentage, false, 0)) {
                Timber.d("Monitor(%s) adjusted volume.", type)
            }

            try {
                Thread.sleep(250)
            } catch (e: InterruptedException) {
                Timber.w(e)
                return
            }
        }
        Timber.d("Monitor(%s) finished.", type)
    }

}
