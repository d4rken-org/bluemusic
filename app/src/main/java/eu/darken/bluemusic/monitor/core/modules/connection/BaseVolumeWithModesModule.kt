package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.audio.AudioStream.SOUND_MODE_SILENT
import eu.darken.bluemusic.monitor.core.audio.AudioStream.SOUND_MODE_VIBRATE
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerModeHelper
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import kotlinx.coroutines.delay
import java.time.Instant

abstract class BaseVolumeWithModesModule(
    settings: DevicesSettings,
    streamHelper: StreamHelper,
    private val ringerModeHelper: RingerModeHelper
) : BaseVolumeModule(settings, streamHelper) {

    override suspend fun setInitial(device: ManagedDevice, percentage: Float) {
        log(tag, INFO) { "Setting initial volume/mode ($percentage) for $device" }

        // Handle special sound modes
        when (percentage) {
            SOUND_MODE_SILENT -> {
                log(tag, INFO) { "Setting ringer mode to SILENT for $device" }
                if (ringerModeHelper.setSilentMode()) {
                    log(tag) { "Successfully set ringer mode to SILENT" }
                }
                return
            }

            SOUND_MODE_VIBRATE -> {
                log(tag, INFO) { "Setting ringer mode to VIBRATE for $device" }
                if (ringerModeHelper.setVibrateMode()) {
                    log(tag) { "Successfully set ringer mode to VIBRATE" }
                }
                return
            }

            else -> {
                // For normal volume levels, ensure we're in normal ringer mode
                if (percentage > 0) {
                    ringerModeHelper.setNormalMode()
                }
                // Call parent implementation for normal volume handling
                super.setInitial(device, percentage)
            }
        }
    }

    override suspend fun monitor(device: ManagedDevice, targetPercentage: Float) {
        when (targetPercentage) {
            SOUND_MODE_SILENT -> monitorRingerMode(device, RingerMode.SILENT)
            SOUND_MODE_VIBRATE -> monitorRingerMode(device, RingerMode.VIBRATE)
            else -> super.monitor(device, targetPercentage)
        }
    }

    private suspend fun monitorRingerMode(device: ManagedDevice, targetMode: RingerMode) {
        log(tag, INFO) { "Monitoring ringer mode (target=$targetMode) for $device" }

        val monitorDuration = device.monitoringDuration
        log(tag) { "Monitor($type) ringer mode active for ${monitorDuration}ms." }

        val targetTime = Instant.now() + monitorDuration
        while (Instant.now() < targetTime) {
            val currentMode = ringerModeHelper.getCurrentRingerMode()
            if (currentMode != targetMode) {
                log(tag) { "Ringer mode changed from $currentMode to $targetMode, restoring..." }
                ringerModeHelper.setRingerMode(targetMode)
            }
            delay(250)
        }
        log(tag) { "Monitor($type) ringer mode finished." }
    }
}