package eu.darken.bluemusic.main.core.service.modules.events

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.database.ManagedDevice
import eu.darken.bluemusic.main.core.service.modules.EventModule
import eu.darken.bluemusic.settings.core.Settings
import timber.log.Timber

abstract class BaseVolumeModule(
        private val settings: Settings,
        private val streamHelper: StreamHelper
) : EventModule() {

    abstract val type: AudioStream.Type

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

        val adjustmentDelay = device.adjustmentDelay ?: Settings.DEFAULT_ADJUSTMENT_DELAY

        if (streamHelper.changeVolume(device.getStreamId(type), percentage, settings.isVolumeAdjustedVisibly, adjustmentDelay)) {
            Timber.d("Volume(%s) adjusted volume.", type)
        } else if (device.nudgeVolume) {
            Timber.d("Volume wasn't changed, but we want to nudge it for this device.")
            val currentVolume = streamHelper.getCurrentVolume(device.getStreamId(type))

            Timber.v("Current volume is %d and we will lower then raise it.", currentVolume)
            if (streamHelper.lowerByOne(device.getStreamId(type), true)) {
                Timber.v("Volume was nudged lower, now nudging higher, to previous value.")
                Thread.sleep(500)
                streamHelper.increaseByOne(device.getStreamId(type), true)
            } else if (streamHelper.increaseByOne(device.getStreamId(type), true)) {
                Timber.v("Volume was nudged higher, now nudging lower, to previous value.")
                Thread.sleep(500)
                streamHelper.lowerByOne(device.getStreamId(type), true)
            }
        }
    }

}