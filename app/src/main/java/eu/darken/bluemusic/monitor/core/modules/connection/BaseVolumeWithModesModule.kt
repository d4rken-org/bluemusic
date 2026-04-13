package eu.darken.bluemusic.monitor.core.modules.connection

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerModeObserver
import eu.darken.bluemusic.monitor.core.audio.RingerTool
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeModeTool
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeObservationGate
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull

abstract class BaseVolumeWithModesModule(
    volumeTool: VolumeTool,
    volumeObserver: VolumeObserver,
    observationGate: VolumeObservationGate,
    ownerRegistry: AudioStreamOwnerRegistry,
    deviceRepo: DeviceRepo,
    private val volumeModeTool: VolumeModeTool,
    private val ringerTool: RingerTool,
    private val ringerModeObserver: RingerModeObserver,
) : BaseVolumeModule(volumeTool, volumeObserver, observationGate, ownerRegistry, deviceRepo) {

    override suspend fun setInitial(device: ManagedDevice, volumeMode: VolumeMode) {
        log(tag, INFO) { "Setting initial volume/mode ($volumeMode) for $device" }

        when (volumeMode) {
            is VolumeMode.Silent -> {
                log(tag, INFO) { "Applying Silent mode for $device" }
                if (volumeModeTool.alignSystemState(type, volumeMode)) {
                    log(tag) { "Successfully applied Silent mode" }
                }
                return
            }

            is VolumeMode.Vibrate -> {
                log(tag, INFO) { "Applying Vibrate mode for $device" }
                if (volumeModeTool.alignSystemState(type, volumeMode)) {
                    log(tag) { "Successfully applied Vibrate mode" }
                }
                return
            }

            is VolumeMode.Normal -> {
                volumeModeTool.alignSystemState(type, volumeMode)
                super.setInitial(device, volumeMode)
            }
        }
    }

    override suspend fun monitor(device: ManagedDevice, volumeMode: VolumeMode, generationAtStart: Long) {
        when (volumeMode) {
            is VolumeMode.Silent -> monitorRingerMode(device, RingerMode.SILENT)
            is VolumeMode.Vibrate -> monitorRingerMode(device, RingerMode.VIBRATE)
            is VolumeMode.Normal -> super.monitor(device, volumeMode, generationAtStart)
        }
    }

    private suspend fun monitorRingerMode(device: ManagedDevice, targetMode: RingerMode) {
        log(tag, INFO) { "Monitoring ringer mode (target=$targetMode) for $device" }

        // Set to true inside collect to exit cleanly via takeWhile on the next element.
        var yielded = false
        withTimeoutOrNull(device.monitoringDuration.toMillis()) {
            ringerModeObserver.ringerMode
                .filter { it.newMode != targetMode }
                .takeWhile { !yielded }
                .collect { event ->
                    if (!ringerTool.wasUs(targetMode)) {
                        log(tag, INFO) {
                            "Monitor($type) yielding to external ringer mode change on $device"
                        }
                        yielded = true
                        return@collect
                    }

                    log(tag) {
                        "Monitor($type) re-enforcing ringer mode " +
                            "(${event.newMode} → $targetMode)"
                    }
                    ringerTool.setRingerMode(targetMode)
                }
        }

        log(tag) { "Monitor($type) ringer mode finished." }
    }
}
