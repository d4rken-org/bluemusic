package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeMode.Companion.fromFloat
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.delayForReactionDelay
import kotlinx.coroutines.delay
import java.time.Instant

abstract class BaseVolumeModule(
    private val volumeTool: VolumeTool
) : ConnectionModule {

    abstract val type: AudioStream.Type

    override val tag: String
        get() = logTag("Monitor", "$type", "Volume", "Module")

    open suspend fun areRequirementsMet(): Boolean = true

    override suspend fun handle(event: DeviceEvent) {
        if (event !is DeviceEvent.Connected) return
        val device = event.device
        val volumeFloat = device.getVolume(type)
        val volumeMode = fromFloat(volumeFloat)
        log(tag) { "Desired $type volume is $volumeMode" }
        if (volumeMode == null) return

        if (!areRequirementsMet()) {
            log(tag) { "Requirements not met!" }
            return
        }


        delayForReactionDelay(event)

        setInitial(device, volumeMode)

        monitor(device, volumeMode)
    }

    protected open suspend fun setInitial(device: ManagedDevice, volumeMode: VolumeMode) {
        log(tag, INFO) { "Setting initial volume ($volumeMode) for $device" }

        // Default implementation only handles normal volumes
        if (volumeMode !is VolumeMode.Normal) {
            log(tag) { "Special volume mode $volumeMode not supported in base implementation" }
            return
        }

        val percentage = volumeMode.percentage

        val changed = volumeTool.changeVolume(
            streamId = device.getStreamId(type),
            percent = percentage,
            visible = device.visibleAdjustments,
            delay = device.adjustmentDelay
        )
        if (changed) {
            log(tag) { "Volume($type) adjusted volume." }
        } else if (device.nudgeVolume) {
            log(tag) { "Volume wasn't changed, but we want to nudge it for this device." }
            val currentVolume = volumeTool.getCurrentVolume(device.getStreamId(type))

            log(tag, VERBOSE) { "Current volume is $currentVolume and we will lower then raise it." }
            if (volumeTool.lowerByOne(device.getStreamId(type), true)) {
                log(tag, VERBOSE) { "Volume was nudged lower, now nudging higher, to previous value." }
                delay(500)
                volumeTool.increaseByOne(device.getStreamId(type), true)
            } else if (volumeTool.increaseByOne(device.getStreamId(type), true)) {
                log(tag, VERBOSE) { "Volume was nudged higher, now nudging lower, to previous value." }
                delay(500)
                volumeTool.lowerByOne(device.getStreamId(type), true)
            }
        }
    }

    protected open suspend fun monitor(device: ManagedDevice, volumeMode: VolumeMode) {
        log(tag, INFO) { "Monitoring volume (target=$volumeMode) for $device" }

        // Default implementation only handles normal volumes
        if (volumeMode !is VolumeMode.Normal) {
            log(tag) { "Special volume mode $volumeMode not supported in base monitoring" }
            return
        }

        val targetPercentage = volumeMode.percentage

        val monitorDuration = device.monitoringDuration
        log(tag) { "Monitor($type) active for ${monitorDuration}ms." }

        val streamId = device.getStreamId(type)

        val targetTime = Instant.now() + monitorDuration
        while (Instant.now() < targetTime) {
            if (volumeTool.changeVolume(streamId, targetPercentage)) {
                log(tag) { "Monitor($type) adjusted volume." }
            }

            delay(250)
        }
        log(tag) { "Monitor($type) finished." }
    }


}