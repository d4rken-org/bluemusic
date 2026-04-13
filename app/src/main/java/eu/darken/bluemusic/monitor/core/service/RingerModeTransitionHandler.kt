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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RingerModeTransitionHandler @Inject constructor(
    private val deviceRepo: DeviceRepo,
    private val volumeTool: VolumeTool,
    private val observationGate: VolumeObservationGate,
) {

    suspend fun handle(event: RingerModeEvent) {
        log(TAG, VERBOSE) { "handle: $event" }
        val activeDevice = deviceRepo.currentDevices().firstOrNull { it.isActive }
        if (activeDevice == null) {
            log(TAG, INFO) { "No active device, skipping." }
            return
        }
        if (activeDevice.getVolume(AudioStream.Type.RINGTONE) == null) {
            log(TAG, INFO) { "No ringtone volume configured, skipping." }
            return
        }

        val volumeMode = when (event.newMode) {
            RingerMode.SILENT -> VolumeMode.Silent
            RingerMode.VIBRATE -> VolumeMode.Vibrate
            RingerMode.NORMAL -> {
                val currentVolume = activeDevice.getVolume(AudioStream.Type.RINGTONE)
                if (currentVolume != null && currentVolume >= 0f) {
                    VolumeMode.Normal(currentVolume)
                } else {
                    VolumeMode.Normal(0.5f)
                }
            }
        }

        val storedNotification = activeDevice.getVolume(AudioStream.Type.NOTIFICATION)
            ?.takeIf { it >= 0f }
        val suppressNotification = event.newMode == RingerMode.NORMAL && storedNotification != null

        if (suppressNotification) {
            observationGate.suppress(AudioStream.Id.STREAM_NOTIFICATION)
        }
        try {
            deviceRepo.updateDevice(activeDevice.address) {
                it.updateVolume(AudioStream.Type.RINGTONE, volumeMode)
            }

            if (storedNotification != null && suppressNotification) {
                val streamId = activeDevice.getStreamId(AudioStream.Type.NOTIFICATION)
                log(TAG, INFO) { "Re-applying stored notification volume ($storedNotification) for ${activeDevice.label}" }
                volumeTool.changeVolume(
                    streamId = streamId,
                    percent = storedNotification,
                    visible = false,
                )
            }
        } finally {
            if (suppressNotification) {
                observationGate.unsuppress(AudioStream.Id.STREAM_NOTIFICATION)
            }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "RingerMode", "TransitionHandler")
    }
}
