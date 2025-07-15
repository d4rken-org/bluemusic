package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.EventModule

internal abstract class BaseMonitorModule(
    private val streamHelper: StreamHelper
) : EventModule {

    abstract val type: AudioStream.Type

    override val priority: Int
        get() = 20

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

        val monitorDuration = device.monitoringDuration
        log(TAG) { "Monitor($type) active for ${monitorDuration}ms." }
        if (monitorDuration == null) return

        val targetTime = System.currentTimeMillis() + monitorDuration
        while (System.currentTimeMillis() < targetTime) {
            if (streamHelper.changeVolume(device.getStreamId(type), percentage, false, 0)) {
                log(TAG) { "Monitor($type) adjusted volume." }
            }

            try {
                Thread.sleep(250)
            } catch (e: InterruptedException) {
                log(TAG, WARN) { e.asLog() }
                return
            }
        }
        log(TAG) { "Monitor($type) finished." }
    }

    companion object {
        private val TAG = logTag("Monitor", "BaseEventModule")
    }

}
