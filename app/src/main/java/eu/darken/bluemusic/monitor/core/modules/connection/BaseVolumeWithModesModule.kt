package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerTool
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import kotlinx.coroutines.delay
import java.time.Instant

abstract class BaseVolumeWithModesModule(
    settings: DevicesSettings,
    volumeTool: VolumeTool,
    private val ringerTool: RingerTool
) : BaseVolumeModule(settings, volumeTool) {

    override suspend fun setInitial(device: ManagedDevice, volumeMode: VolumeMode) {
        log(tag, INFO) { "Setting initial volume/mode ($volumeMode) for $device" }

        when (volumeMode) {
            is VolumeMode.Silent -> {
                log(tag, INFO) { "Setting ringer mode to SILENT for $device" }
                if (ringerTool.setRingerMode(RingerMode.SILENT)) {
                    log(tag) { "Successfully set ringer mode to SILENT" }
                }
                return
            }

            is VolumeMode.Vibrate -> {
                log(tag, INFO) { "Setting ringer mode to VIBRATE for $device" }
                if (ringerTool.setRingerMode(RingerMode.VIBRATE)) {
                    log(tag) { "Successfully set ringer mode to VIBRATE" }
                }
                return
            }

            is VolumeMode.Normal -> {
                // For normal volume levels, ensure we're in normal ringer mode
                if (volumeMode.percentage > 0) {
                    ringerTool.setRingerMode(RingerMode.NORMAL)
                }
                // Call parent implementation for normal volume handling
                super.setInitial(device, volumeMode)
            }
        }
    }

    override suspend fun monitor(device: ManagedDevice, volumeMode: VolumeMode) {
        when (volumeMode) {
            is VolumeMode.Silent -> monitorRingerMode(device, RingerMode.SILENT)
            is VolumeMode.Vibrate -> monitorRingerMode(device, RingerMode.VIBRATE)
            is VolumeMode.Normal -> super.monitor(device, volumeMode)
        }
    }

    private suspend fun monitorRingerMode(device: ManagedDevice, targetMode: RingerMode) {
        log(tag, INFO) { "Monitoring ringer mode (target=$targetMode) for $device" }

        val monitorDuration = device.monitoringDuration
        log(tag) { "Monitor($type) ringer mode active for ${monitorDuration}ms." }

        val targetTime = Instant.now() + monitorDuration
        while (Instant.now() < targetTime) {
            val currentMode = ringerTool.getCurrentRingerMode()
            if (currentMode != targetMode) {
                log(tag) { "Ringer mode changed from $currentMode to $targetMode, restoring..." }
                ringerTool.setRingerMode(targetMode)
            }
            delay(250)
        }
        log(tag) { "Monitor($type) ringer mode finished." }
    }
}