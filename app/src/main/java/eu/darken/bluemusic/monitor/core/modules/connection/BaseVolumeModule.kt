package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.EventModule
import kotlinx.coroutines.delay

abstract class BaseVolumeModule(
    private val settings: DevicesSettings,
    private val streamHelper: StreamHelper
) : EventModule {

    abstract val type: AudioStream.Type

    override suspend fun handle(event: DeviceEvent) {
        if (event !is DeviceEvent.Connected) return
        val device = event.device
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

        setInitial(device, percentage)

        monitor(device, percentage)
    }

    private suspend fun setInitial(device: ManagedDevice, percentage: Float) {
        log(TAG, INFO) { "Setting initial volume ($percentage) for $device" }

        val adjustmentDelay = device.adjustmentDelay ?: DevicesSettings.DEFAULT_ADJUSTMENT_DELAY

        if (streamHelper.changeVolume(device.getStreamId(type), percentage, settings.visibleAdjustments.value(), adjustmentDelay)) {
            log(TAG) { "Volume($type) adjusted volume." }
        } else if (device.nudgeVolume) {
            log(TAG) { "Volume wasn't changed, but we want to nudge it for this device." }
            val currentVolume = streamHelper.getCurrentVolume(device.getStreamId(type))

            log(TAG, VERBOSE) { "Current volume is $currentVolume and we will lower then raise it." }
            if (streamHelper.lowerByOne(device.getStreamId(type), true)) {
                log(TAG, VERBOSE) { "Volume was nudged lower, now nudging higher, to previous value." }
                delay(500)
                streamHelper.increaseByOne(device.getStreamId(type), true)
            } else if (streamHelper.increaseByOne(device.getStreamId(type), true)) {
                log(TAG, VERBOSE) { "Volume was nudged higher, now nudging lower, to previous value." }
                delay(500)
                streamHelper.lowerByOne(device.getStreamId(type), true)
            }
        }
    }

    private suspend fun monitor(device: ManagedDevice, percentage: Float) {
        log(TAG, INFO) { "Monitoring volume (target=$percentage) for $device" }

        val monitorDuration = device.monitoringDuration
        log(TAG) { "Monitor($type) active for ${monitorDuration}ms." }
        if (monitorDuration == null) return

        val targetTime = System.currentTimeMillis() + monitorDuration
        while (System.currentTimeMillis() < targetTime) {
            if (streamHelper.changeVolume(device.getStreamId(type), percentage, false, 0)) {
                log(TAG) { "Monitor($type) adjusted volume." }
            }

            try {
                delay(250)
            } catch (e: InterruptedException) {
                log(TAG, WARN) { e.asLog() }
                return
            }
        }
        log(TAG) { "Monitor($type) finished." }
    }

    companion object {
        private val TAG = logTag("Monitor", "BaseVolume", "Module")
    }
}