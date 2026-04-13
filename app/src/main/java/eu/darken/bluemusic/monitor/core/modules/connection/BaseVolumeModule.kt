package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.getDevice
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeMode.Companion.fromFloat
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.audio.percentageToLevel
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.delayForReactionDelay
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeObservationGate
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull

abstract class BaseVolumeModule(
    private val volumeTool: VolumeTool,
    private val volumeObserver: VolumeObserver,
    private val observationGate: VolumeObservationGate,
    private val ownerRegistry: AudioStreamOwnerRegistry,
    private val deviceRepo: DeviceRepo,
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

        val generationAtStart = ownerRegistry.ownershipGeneration()

        val streamId = device.getStreamId(type)
        val token = observationGate.suppress(streamId)
        try {
            delayForReactionDelay(event)

            if (ownerRegistry.ownershipGeneration() != generationAtStart) {
                log(tag, INFO) { "Ownership changed during actionDelay, yielding" }
                return
            }

            val freshDevice = deviceRepo.getDevice(device.address) ?: run {
                log(tag, INFO) { "Device ${device.address} no longer exists after delay, yielding" }
                return
            }
            val freshVolumeMode = fromFloat(freshDevice.getVolume(type)) ?: run {
                log(tag, INFO) { "Device ${device.address} volume no longer configured after delay, yielding" }
                return
            }

            setInitial(freshDevice, freshVolumeMode)

            monitor(freshDevice, freshVolumeMode, generationAtStart)
        } finally {
            observationGate.unsuppress(token)
        }
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

    /**
     * Monitors the stream volume for [device.monitoringDuration] after [setInitial].
     *
     * Subscribes to [VolumeObserver.volumes] instead of polling. This is event-driven:
     * only wakes when a volume actually changes (~5ms latency via ContentObserver,
     * vs up to 250ms with the previous polling loop). Zero CPU work when idle.
     *
     * Re-enforcement logic:
     * - External platform writes (Android route transition) → re-enforce our target.
     * - Writes from other VolumeTool callers (user slider drag) → yield and exit.
     * - Our own re-enforcement landing → ignore (event.newVolume == targetLevel).
     *
     * Known limitation: if a user drags during the actionDelay window (before
     * setInitial even runs), setInitial will overwrite them with the connect-time
     * snapshot. Fixing that would require re-reading DeviceRepo after the delay.
     */
    protected open suspend fun monitor(
        device: ManagedDevice,
        volumeMode: VolumeMode,
        generationAtStart: Long = -1L,
    ) {
        if (volumeMode !is VolumeMode.Normal) {
            log(tag) { "Special volume mode $volumeMode not supported in base monitoring" }
            return
        }

        val streamId = device.getStreamId(type)
        val targetPercentage = volumeMode.percentage
        val targetLevel = percentageToLevel(targetPercentage, volumeTool.getMinVolume(streamId), volumeTool.getMaxVolume(streamId))

        log(tag, INFO) { "Monitoring volume (target=$volumeMode, level=$targetLevel) for $device" }

        var yielded = false
        withTimeoutOrNull(device.monitoringDuration.toMillis()) {
            volumeObserver.volumes
                .filter { it.streamId == streamId }
                .filter { it.newVolume != targetLevel }
                .takeWhile { !yielded }
                .collect { event ->
                    if (generationAtStart >= 0 && ownerRegistry.ownershipGeneration() != generationAtStart) {
                        log(tag, INFO) { "Monitor($type) yielding, ownership changed" }
                        yielded = true
                        return@collect
                    }

                    if (!volumeTool.wasUs(streamId, targetLevel)) {
                        log(tag, INFO) {
                            "Monitor($type) yielding to external VolumeTool write on $device"
                        }
                        yielded = true
                        return@collect
                    }

                    log(tag) {
                        "Monitor($type) re-enforcing against external write " +
                            "(${event.oldVolume} → ${event.newVolume}, target=$targetLevel)"
                    }
                    volumeTool.changeVolume(streamId, targetPercentage)
                }
        }

        log(tag) { "Monitor($type) finished." }
    }
}
