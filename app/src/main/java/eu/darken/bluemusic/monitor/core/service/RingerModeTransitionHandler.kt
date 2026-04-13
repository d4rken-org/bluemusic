package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerModeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeObservationGate
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RingerModeTransitionHandler @Inject constructor(
    private val deviceRepo: DeviceRepo,
    private val volumeTool: VolumeTool,
    private val observationGate: VolumeObservationGate,
    private val ownerRegistry: AudioStreamOwnerRegistry,
) {

    suspend fun handle(event: RingerModeEvent) {
        log(TAG, VERBOSE) { "handle: $event" }

        val ownerAddresses = ownerRegistry.ownerAddressesFor(AudioStream.Id.STREAM_RINGTONE).toSet()
        val ownerDevices = deviceRepo.currentDevices()
            .filter { it.isActive && it.address in ownerAddresses }

        if (ownerDevices.isEmpty()) {
            log(TAG, INFO) { "No owner devices for ringtone, skipping." }
            return
        }

        val eligibleDevices = ownerDevices.filter { it.getVolume(AudioStream.Type.RINGTONE) != null }
        if (eligibleDevices.isEmpty()) {
            log(TAG, INFO) { "No owner devices with ringtone volume configured, skipping." }
            return
        }

        // Use the first eligible device's config for notification suppression decision.
        // All group members should have similar config (earbuds are the same device).
        val primaryDevice = eligibleDevices.first()

        val volumeMode = when (event.newMode) {
            RingerMode.SILENT -> VolumeMode.Silent
            RingerMode.VIBRATE -> VolumeMode.Vibrate
            RingerMode.NORMAL -> {
                val currentVolume = primaryDevice.getVolume(AudioStream.Type.RINGTONE)
                if (currentVolume != null && currentVolume >= 0f) {
                    VolumeMode.Normal(currentVolume)
                } else {
                    VolumeMode.Normal(0.5f)
                }
            }
        }

        val storedNotification = primaryDevice.getVolume(AudioStream.Type.NOTIFICATION)
            ?.takeIf { it >= 0f }
        val suppressNotification = event.newMode == RingerMode.NORMAL && storedNotification != null

        val token = if (suppressNotification) {
            observationGate.suppress(AudioStream.Id.STREAM_NOTIFICATION)
        } else null
        try {
            for (device in eligibleDevices) {
                deviceRepo.updateDevice(device.address) {
                    it.updateVolume(AudioStream.Type.RINGTONE, volumeMode)
                }
            }

            if (storedNotification != null && suppressNotification) {
                val streamId = primaryDevice.getStreamId(AudioStream.Type.NOTIFICATION)
                log(TAG, INFO) { "Re-applying stored notification volume ($storedNotification) for ${primaryDevice.label}" }
                volumeTool.changeVolume(
                    streamId = streamId,
                    percent = storedNotification,
                    visible = false,
                )
            }
        } finally {
            token?.let { observationGate.unsuppress(it) }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "RingerMode", "TransitionHandler")
    }
}
