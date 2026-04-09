package eu.darken.bluemusic.monitor.core.modules.volume

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.getVolume
import eu.darken.bluemusic.devices.core.updateVolume
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerMode
import eu.darken.bluemusic.monitor.core.audio.RingerTool
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class VolumeDisconnectModule @Inject constructor(
    private val volumeTool: VolumeTool,
    private val ringerTool: RingerTool,
    private val deviceRepo: DeviceRepo,
) : ConnectionModule {

    override val tag: String
        get() = TAG

    override val priority: Int = 1 // Run early to capture volumes before other modules

    override suspend fun handle(event: DeviceEvent) {
        if (event !is DeviceEvent.Disconnected) return

        val device = event.device

        if (!device.volumeSaveOnDisconnect) {
            log(TAG, VERBOSE) { "Device ${device.label} does not have 'save on disconnect' enabled" }
            return
        }

        log(TAG, INFO) { "Saving volumes on disconnect for device ${device.label}" }

        // TODO: RingerTool.getCurrentRingerMode() falls back to NORMAL on unknown
        // Android modes. If Android ever adds a new ringer mode, this module will
        // treat unreliable RINGTONE/NOTIFICATION reads as reliable. Broaden
        // RingerTool to expose an unknown/null state if the need arises.
        val ringerMode = ringerTool.getCurrentRingerMode()

        // Phase 1: capture hardware facts only — no DB access yet. Using
        // event.device (a snapshot) to decide "is this stream configured at all?"
        // is acceptable because an unset-at-dispatch-time stream cannot
        // legitimately become set again by the time the disconnect module runs
        // at priority 1.
        val snapshots = AudioStream.Type.entries.mapNotNull { type ->
            if (device.getVolume(type) == null) return@mapNotNull null

            val streamId = device.getStreamId(type)
            val maxLevel = volumeTool.getMaxVolume(streamId)
            val currentLevel = volumeTool.getCurrentVolume(streamId)
            if (maxLevel <= 0 || currentLevel !in 0..maxLevel) {
                log(TAG, WARN) {
                    "Skipping $type, bad hardware read (level=$currentLevel max=$maxLevel)"
                }
                return@mapNotNull null
            }

            Snapshot(type, currentLevel, maxLevel)
        }

        if (snapshots.isEmpty()) {
            log(TAG, VERBOSE) { "No streams to capture for ${device.label}" }
            return
        }

        // Phase 2: compare-and-write inside the updateDevice transaction so we
        // decide against the DB-fresh oldConfig, not the stale event.device.config
        // snapshot. A concurrent VolumeUpdateModule write (priority 10) between
        // dispatch and our turn would otherwise be clobbered.
        var writeCount = 0
        deviceRepo.updateDevice(device.address) { oldConfig ->
            var updated = oldConfig
            for (snap in snapshots) {
                val rawStored = oldConfig.getVolume(snap.type) ?: continue
                val savedMode = VolumeMode.fromFloat(rawStored)

                val percent = (snap.currentLevel.toFloat() / snap.maxLevel).coerceIn(0f, 1f)
                val hardwareNormal = VolumeMode.Normal(percent)

                val currentMode: VolumeMode? = when (snap.type) {
                    AudioStream.Type.RINGTONE -> when (ringerMode) {
                        // RINGTONE has first-class Silent/Vibrate sentinels;
                        // we own the persistence path together with
                        // MonitorService.handleRingerMode(). Both writers agree
                        // on the sentinel value for a given ringer state, so
                        // dual-writing is safe and closes the race where
                        // handleRingerMode() bails out (no active device) before
                        // our disconnect save runs.
                        RingerMode.SILENT -> VolumeMode.Silent
                        RingerMode.VIBRATE -> VolumeMode.Vibrate
                        RingerMode.NORMAL -> hardwareNormal
                    }

                    AudioStream.Type.NOTIFICATION -> when (ringerMode) {
                        RingerMode.NORMAL -> hardwareNormal
                        else -> {
                            // Non-Normal ringer mode + STREAM_NOTIFICATION is
                            // ambiguous: some devices (Pixel) clamp it to 0 as
                            // a side effect of vibrate/silent coupling, other
                            // devices leave it under independent user control.
                            //
                            // Heuristic: if the hardware reports > 0, trust the
                            // user (they set it deliberately). If it reports 0,
                            // preserve the stored value — we can't distinguish
                            // "coupling clamp" from "user muted notification"
                            // on a single read, and preserving the last
                            // explicit value is the safer default on devices
                            // that couple.
                            //
                            // Known limitation: on non-coupling devices, a
                            // user intentionally setting notification to 0
                            // while in vibrate/silent ringer mode will NOT be
                            // captured by save-on-disconnect. This heuristic
                            // intentionally prefers preserving pre-existing
                            // values on coupling devices over guessing at the
                            // meaning of a single 0-read in non-Normal mode.
                            if (snap.currentLevel > 0) hardwareNormal else null
                        }
                    }

                    else -> hardwareNormal
                }

                if (currentMode == null) {
                    log(TAG, VERBOSE) {
                        "Skipping ${snap.type}, ringer=$ringerMode hardware=0 (preserving stored $savedMode)"
                    }
                    continue
                }

                val shouldWrite = when {
                    // Corrupt / unparseable float on disk — heal the record.
                    savedMode == null -> {
                        log(TAG, WARN) {
                            "Corrupt stored ${snap.type} value ($rawStored) for " +
                                "${device.label}, healing with $currentMode"
                        }
                        true
                    }
                    // Both Normal: compare discretized levels so we don't
                    // rewrite a higher-precision stored float with the
                    // discrete level/max ratio on every disconnect cycle.
                    savedMode is VolumeMode.Normal && currentMode is VolumeMode.Normal -> {
                        val savedLevel = (savedMode.percentage * snap.maxLevel).roundToInt()
                        savedLevel != snap.currentLevel
                    }
                    // Structural equality on Silent/Vibrate (data objects).
                    savedMode == currentMode -> false
                    // Mode changed (e.g. Normal → Vibrate, or Vibrate → Normal
                    // after a user-driven ringer mode flip mid-session).
                    else -> true
                }

                if (shouldWrite) {
                    updated = updated.updateVolume(snap.type, currentMode)
                    writeCount++
                    log(TAG, VERBOSE) { "Capturing ${snap.type}: $currentMode for ${device.label}" }
                } else {
                    log(TAG, VERBOSE) { "Skipping ${snap.type}, no change for ${device.label}" }
                }
            }
            updated
        }

        if (writeCount == 0) {
            log(TAG, VERBOSE) { "No volume changes to save on disconnect for ${device.label}" }
        } else {
            log(TAG, INFO) { "Saved $writeCount volume settings on disconnect for ${device.label}" }
        }
    }

    private data class Snapshot(
        val type: AudioStream.Type,
        val currentLevel: Int,
        val maxLevel: Int,
    )

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: VolumeDisconnectModule): ConnectionModule
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "Disconnect", "Module")
    }
}
