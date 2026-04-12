package eu.darken.bluemusic.monitor.core.modules.volume

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerTool
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeUpdateModule @Inject constructor(
    private val volumeTool: VolumeTool,
    private val ringerTool: RingerTool,
    private val deviceRepo: DeviceRepo,
) : VolumeModule {

    override val tag: String
        get() = TAG

    override suspend fun handle(event: VolumeEvent) {
        val id = event.streamId
        val volume = event.newVolume

        if (volumeTool.wasUs(id, volume)) {
            log(TAG, VERBOSE) { "Volume change was triggered by us, ignoring it." }
            return
        } else {
            log(TAG, DEBUG) { "Volume change $event" }
        }

        val allActive = deviceRepo.currentDevices().filter { it.isActive }
        log(TAG, VERBOSE) { "Active devices (${allActive.size}): $allActive" }

        val candidates = allActive.filter { dev ->
            if (!dev.volumeObserving || dev.volumeLock) return@filter false
            val streamType = dev.getStreamType(id) ?: return@filter false
            dev.getVolume(streamType) != null
        }

        val now = Instant.now()
        val stabilizing = candidates.filter {
            Duration.between(it.lastConnected, now) <= it.actionDelay + it.monitoringDuration
        }
        val stable = candidates.filter {
            Duration.between(it.lastConnected, now) > it.actionDelay + it.monitoringDuration
        }

        if (stabilizing.isNotEmpty() && stable.isNotEmpty()) {
            log(TAG, VERBOSE) {
                "Stream candidate in post-connect window alongside stable sibling; " +
                    "skipping persist for $id to avoid cross-device contamination"
            }
            return
        }

        val ringerMode = ringerTool.getCurrentRingerMode()
        val percentage = volumeTool.getVolumePercentage(id).coerceIn(0f, 1f)

        stable.forEach { dev ->
            val streamType = dev.getStreamType(id)!!

            // Map the raw hardware reading to a VolumeMode that accounts
            // for the current ringer mode on streams that have sentinel
            // states (RINGTONE) or unreliable hardware reads in
            // vibrate/silent (NOTIFICATION).
            //
            // Without this, a user flipping the phone to vibrate
            // mid-session would trigger STREAM_RING → 0 observations that
            // overwrite the stored Vibrate sentinel (or a legitimate
            // Normal value) with `Normal(0)`, racing against
            // MonitorService.handleRingerMode() and losing non-deterministically.
            val mode: VolumeMode? = when (streamType) {
                AudioStream.Type.RINGTONE -> when (ringerMode) {
                    // RINGTONE owns first-class Silent/Vibrate sentinels.
                    // Both this path and handleRingerMode() are allowed to
                    // write them; they always agree on the sentinel value,
                    // so last-writer-wins is safe.
                    RingerMode.SILENT -> VolumeMode.Silent
                    RingerMode.VIBRATE -> VolumeMode.Vibrate
                    RingerMode.NORMAL -> VolumeMode.Normal(percentage)
                }

                AudioStream.Type.NOTIFICATION -> when (ringerMode) {
                    RingerMode.NORMAL -> VolumeMode.Normal(percentage)
                    else -> {
                        // Same heuristic as VolumeDisconnectModule: in
                        // non-Normal ringer mode, a 0 reading could be
                        // either a Pixel-style platform clamp or an
                        // intentional user mute on a non-coupling device.
                        // Capture non-zero readings (clearly user intent)
                        // and preserve stored on 0-readings (safer default
                        // for the coupling case).
                        if (event.newVolume > 0) VolumeMode.Normal(percentage) else null
                    }
                }

                else -> VolumeMode.Normal(percentage)
            }

            if (mode == null) {
                log(TAG, VERBOSE) {
                    "Skipping $streamType update for $dev, ringer=$ringerMode hardware=0"
                }
                return@forEach
            }

            log(TAG, INFO) { "Saving new volume ($mode@$id) for $dev" }
            deviceRepo.updateDevice(dev.address) { oldConfig ->
                oldConfig.updateVolume(streamType, mode)
            }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: VolumeUpdateModule): VolumeModule
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "Update", "Module")
    }

}
