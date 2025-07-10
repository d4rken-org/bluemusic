package eu.darken.bluemusic.monitor.core.modules.events

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.modules.EventModule

abstract class BaseVolumeModule(
    private val settings: DevicesSettings,
    private val streamHelper: StreamHelper
) : EventModule {

    companion object {
        private val TAG = logTag("BaseVolumeModule")
    }

    abstract val type: AudioStream.Type

    override suspend fun handle(device: ManagedDevice, event: SourceDevice.Event) {
        if (event.type != SourceDevice.Event.Type.CONNECTED) return

        val percentage = device.getVolume(type)
        log(TAG) { "Desired $type volume is $percentage" }
        if (percentage == null) return

        if (!areRequirementsMet()) {
            log(TAG) { "Requirements not met!" }
            return
        }

        if (percentage == -1f) {
            log(TAG) { "Device $device has no specified target volume yet, skipping adjustments." }
            return
        }

        val adjustmentDelay = device.adjustmentDelay ?: DevicesSettings.DEFAULT_ADJUSTMENT_DELAY

        if (streamHelper.changeVolume(device.getStreamId(type), percentage, settings.visibleAdjustments.value(), adjustmentDelay)) {
            log(TAG) { "Volume($type) adjusted volume." }
        } else if (device.nudgeVolume) {
            log(TAG) { "Volume wasn't changed, but we want to nudge it for this device." }
            val currentVolume = streamHelper.getCurrentVolume(device.getStreamId(type))

            log(TAG, VERBOSE) { "Current volume is $currentVolume and we will lower then raise it." }
            if (streamHelper.lowerByOne(device.getStreamId(type), true)) {
                log(TAG, VERBOSE) { "Volume was nudged lower, now nudging higher, to previous value." }
                Thread.sleep(500)
                streamHelper.increaseByOne(device.getStreamId(type), true)
            } else if (streamHelper.increaseByOne(device.getStreamId(type), true)) {
                log(TAG, VERBOSE) { "Volume was nudged higher, now nudging lower, to previous value." }
                Thread.sleep(500)
                streamHelper.lowerByOne(device.getStreamId(type), true)
            }
        }
    }

}