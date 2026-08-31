package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.getDevice
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeLimitEnforcer
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeMode.Companion.fromFloat
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
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
    protected val ownerRegistry: AudioStreamOwnerRegistry,
    private val deviceRepo: DeviceRepo,
    private val limitEnforcer: VolumeLimitEnforcer,
) : ConnectionModule {

    abstract val type: AudioStream.Type

    override val tag: String
        get() = logTag("Monitor", "$type", "Volume", "Module")

    open suspend fun unmetRequirement(): String? = null

    private fun isApplicable(event: DeviceEvent): Boolean =
        event is DeviceEvent.Connected && fromFloat(event.device.getVolume(type)) != null

    override fun appliesTo(event: DeviceEvent): Boolean = isApplicable(event)

    override suspend fun handle(event: DeviceEvent) {
        if (!isApplicable(event)) return
        val device = event.device
        val volumeMode = fromFloat(device.getVolume(type)) ?: return
        log(tag) { "Desired $type volume is $volumeMode" }

        val unmet = unmetRequirement()
        if (unmet != null) {
            log(tag, WARN) { "Skipping volume restore — requirement not met: $unmet" }
            return
        }

        val streamId = device.getStreamId(type)
        val token = observationGate.suppress(streamId)
        try {
            // The dispatcher has already paid the actionDelay barrier before invoking us.
            // Re-read the device config in case the user updated it between dispatch and
            // now, and snapshot ownership generation as the baseline for monitor() below.
            val freshDevice = deviceRepo.getDevice(device.address) ?: run {
                log(tag, INFO) { "Device ${device.address} no longer exists, yielding" }
                return
            }
            val freshVolumeMode = fromFloat(freshDevice.getVolume(type)) ?: run {
                log(tag, INFO) { "Device ${device.address} volume no longer configured, yielding" }
                return
            }
            val generationAtStart = ownerRegistry.ownershipGeneration()

            setInitial(freshDevice, freshVolumeMode)

            monitor(freshDevice, freshVolumeMode, generationAtStart)
        } finally {
            observationGate.unsuppress(token)
        }
    }

    /**
     * The bounds of the whole owner group, not just the connecting device: a grouped pair with
     * different caps must land on the strictest one no matter which member's connect event we are
     * serving. The write is a self event, so [eu.darken.bluemusic.monitor.core.modules.volume.VolumeLimitModule]
     * never gets a chance to correct it afterwards.
     */
    private suspend fun allowedLevels(streamId: AudioStream.Id): IntRange? {
        val ownerAddresses = ownerRegistry.ownerAddressesFor(streamId).toSet()
        if (ownerAddresses.isEmpty()) return null
        return limitEnforcer.allowedLevels(
            streamId = streamId,
            devices = deviceRepo.currentDevices(),
            ownerAddresses = ownerAddresses,
        )
    }

    protected open suspend fun setInitial(device: ManagedDevice, volumeMode: VolumeMode) {
        log(tag, INFO) { "Setting initial volume ($volumeMode) for ${device.address}/${device.label}" }

        // Default implementation only handles normal volumes
        if (volumeMode !is VolumeMode.Normal) {
            log(tag) { "Special volume mode $volumeMode not supported in base implementation" }
            return
        }

        val streamId = device.getStreamId(type)
        val band = device.getVolumeBand(type)
        val allowedLevels = allowedLevels(streamId)

        val changed = volumeTool.changeVolume(
            streamId = streamId,
            targetLevel = volumeTool.resolveBoundedLevel(streamId, volumeMode.percentage, band, allowedLevels),
            visible = device.visibleAdjustments,
            delay = device.adjustmentDelay
        )
        if (changed) {
            log(tag) { "Volume($type) adjusted volume." }
        } else if (device.nudgeVolume) {
            log(tag) { "Volume wasn't changed, but we want to nudge it for this device." }
            val currentVolume = volumeTool.getCurrentVolume(streamId)

            log(tag, VERBOSE) { "Current volume is $currentVolume and we will lower then raise it." }
            val visible = device.visibleAdjustments
            // The nudge must not step out of the band, not even for the 500ms it takes to step back.
            val allowed = allowedLevels ?: band?.let { volumeTool.bandLevels(streamId, it) }
            val mayLower = allowed == null || currentVolume > allowed.first
            val mayRaise = allowed == null || currentVolume < allowed.last

            if (mayLower && volumeTool.lowerByOne(streamId, visible)) {
                log(tag, VERBOSE) { "Volume was nudged lower, now nudging higher, to previous value." }
                delay(500)
                // Both legs re-check: the step back is relative to a live read, and an external
                // change during the pause would otherwise carry it out of the band.
                if (allowed == null || volumeTool.getCurrentVolume(streamId) < allowed.last) {
                    volumeTool.increaseByOne(streamId, visible)
                } else {
                    log(tag, VERBOSE) { "Volume moved to the top of $allowed during the nudge, not stepping back." }
                }
            } else if (mayRaise && volumeTool.increaseByOne(streamId, visible)) {
                log(tag, VERBOSE) { "Volume was nudged higher, now nudging lower, to previous value." }
                delay(500)
                if (allowed == null || volumeTool.getCurrentVolume(streamId) > allowed.first) {
                    volumeTool.lowerByOne(streamId, visible)
                } else {
                    log(tag, VERBOSE) { "Volume moved to the bottom of $allowed during the nudge, not stepping back." }
                }
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
     * Known limitation: if a user drags during the dispatcher's settle barrier (before
     * setInitial even runs), setInitial will overwrite them with the connect-time
     * snapshot. The handle path re-reads DeviceRepo after the barrier so a user's
     * config change is picked up, but a transient hardware-volume drag during the
     * barrier is still clobbered.
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
        val targetLevel = volumeTool.resolveBoundedLevel(
            streamId = streamId,
            percent = volumeMode.percentage,
            band = device.getVolumeBand(type),
            allowedLevels = allowedLevels(streamId),
        )

        log(tag, INFO) { "Monitoring volume (target=$volumeMode, level=$targetLevel) for ${device.address}/${device.label}" }

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

                    if (!volumeTool.hasRecentTarget(streamId, targetLevel)) {
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
                    volumeTool.changeVolume(streamId, targetLevel = targetLevel)
                }
        }

        log(tag) { "Monitor($type) finished." }
    }
}
