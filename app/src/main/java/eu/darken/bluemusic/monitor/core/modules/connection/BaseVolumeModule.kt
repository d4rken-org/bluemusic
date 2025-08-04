package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.common.datastore.value
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.delayForReactionDelay
import kotlinx.coroutines.delay
import java.time.Instant

abstract class BaseVolumeModule(
    private val settings: DevicesSettings,
    private val streamHelper: StreamHelper
) : ConnectionModule {

    abstract val type: AudioStream.Type

    override val tag: String
        get() = logTag("Monitor", "$type", "Volume", "Module")

    open suspend fun areRequirementsMet(): Boolean = true

    override suspend fun handle(event: DeviceEvent) {
        if (event !is DeviceEvent.Connected) return
        val device = event.device
        val percentage = device.getVolume(type)
        log(tag) { "Desired $type volume is $percentage" }
        if (percentage == null) return

        if (!areRequirementsMet()) {
            log(tag) { "Requirements not met!" }
            return
        }


        delayForReactionDelay(event)

        setInitial(device, percentage)

        monitor(device, percentage)
    }

    protected open suspend fun setInitial(device: ManagedDevice, percentage: Float) {
        log(tag, INFO) { "Setting initial volume ($percentage) for $device" }

        val changed = streamHelper.changeVolume(
            streamId = device.getStreamId(type),
            percent = percentage,
            visible = settings.visibleAdjustments.value(),
            delay = device.adjustmentDelay
        )
        if (changed) {
            log(tag) { "Volume($type) adjusted volume." }
        } else if (device.nudgeVolume) {
            log(tag) { "Volume wasn't changed, but we want to nudge it for this device." }
            val currentVolume = streamHelper.getCurrentVolume(device.getStreamId(type))

            log(tag, VERBOSE) { "Current volume is $currentVolume and we will lower then raise it." }
            if (streamHelper.lowerByOne(device.getStreamId(type), true)) {
                log(tag, VERBOSE) { "Volume was nudged lower, now nudging higher, to previous value." }
                delay(500)
                streamHelper.increaseByOne(device.getStreamId(type), true)
            } else if (streamHelper.increaseByOne(device.getStreamId(type), true)) {
                log(tag, VERBOSE) { "Volume was nudged higher, now nudging lower, to previous value." }
                delay(500)
                streamHelper.lowerByOne(device.getStreamId(type), true)
            }
        }
    }

    protected open suspend fun monitor(device: ManagedDevice, targetPercentage: Float) {
        log(tag, INFO) { "Monitoring volume (target=$targetPercentage) for $device" }

        val monitorDuration = device.monitoringDuration
        log(tag) { "Monitor($type) active for ${monitorDuration}ms." }

        val streamId = device.getStreamId(type)

        val targetTime = Instant.now() + monitorDuration
        while (Instant.now() < targetTime) {
            if (streamHelper.changeVolume(streamId, targetPercentage)) {
                log(tag) { "Monitor($type) adjusted volume." }
            }

            delay(250)
        }
        log(tag) { "Monitor($type) finished." }
    }


}