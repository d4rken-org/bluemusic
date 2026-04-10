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
import kotlin.math.roundToInt

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
        val streamId = device.getStreamId(type)
        val targetLevel = (targetPercentage * volumeTool.getMaxVolume(streamId)).roundToInt()

        val monitorDuration = device.monitoringDuration
        log(tag) { "Monitor($type) active for ${monitorDuration}ms, targetLevel=$targetLevel." }

        // The monitor loop re-enforces the target against Android's own stream-level
        // resets during BT audio route transitions (A2DP/SCO handoff). It must NOT
        // fight deliberate writes from other code paths (user dragging the in-app
        // slider, another module updating via VolumeTool). We detect those by checking
        // wasUs(targetLevel): the loop only writes targetLevel, so lastUs[id] stays
        // at targetLevel as long as we're the sole writer. If any other VolumeTool
        // caller writes a different level, lastUs[id] changes → wasUs returns false
        // → we yield. Android's own platform writes don't go through VolumeTool, so
        // they don't affect lastUs and the loop correctly re-enforces against them.
        //
        // Known limitation: if a user drags during the actionDelay window (before
        // setInitial even runs), setInitial will overwrite them with the connect-time
        // snapshot. That's a separate issue — fixing it would require re-reading
        // DeviceRepo after the delay.
        val targetTime = Instant.now() + monitorDuration
        while (Instant.now() < targetTime) {
            if (!volumeTool.wasUs(streamId, targetLevel)) {
                log(tag, INFO) { "Monitor($type) yielding to external VolumeTool write on $device" }
                return
            }

            if (volumeTool.changeVolume(streamId, targetPercentage)) {
                log(tag) { "Monitor($type) adjusted volume." }
            }

            delay(250)
        }
        log(tag) { "Monitor($type) finished." }
    }


}