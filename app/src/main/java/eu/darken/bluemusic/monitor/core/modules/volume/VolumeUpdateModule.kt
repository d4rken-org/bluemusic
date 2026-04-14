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
import eu.darken.bluemusic.monitor.core.audio.levelToPercentage
import eu.darken.bluemusic.monitor.core.audio.percentageToLevel
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeUpdateModule @Inject constructor(
    private val volumeTool: VolumeTool,
    private val ringerTool: RingerTool,
    private val deviceRepo: DeviceRepo,
    private val observationGate: VolumeObservationGate,
    private val ownerRegistry: AudioStreamOwnerRegistry,
) : VolumeModule {

    override val tag: String
        get() = TAG

    override suspend fun handle(event: VolumeEvent) {
        val id = event.streamId

        if (event.self) {
            log(TAG, VERBOSE) { "Volume change was triggered by us, ignoring it." }
            return
        }

        if (observationGate.isSuppressed(id)) {
            log(TAG, VERBOSE) { "Observation suppressed for $id, skipping persist" }
            return
        }

        log(TAG, DEBUG) { "Volume change $event" }

        val ownerAddresses = ownerRegistry.ownerAddressesFor(id).toSet()
        if (ownerAddresses.isEmpty()) {
            log(TAG, VERBOSE) { "No owner for $id, skipping persist" }
            return
        }

        val allActive = deviceRepo.currentDevices().filter { it.isActive }

        val candidates = allActive.filter { dev ->
            if (dev.address !in ownerAddresses) return@filter false
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
                "Owner group member in post-connect window alongside stable sibling; " +
                    "skipping persist for $id to avoid intra-group contamination"
            }
            return
        }

        val ringerMode = ringerTool.getCurrentRingerMode()
        val min = volumeTool.getMinVolume(id)
        val max = volumeTool.getMaxVolume(id)
        val percentage = levelToPercentage(event.newVolume, min, max)

        stable.forEach { dev ->
            val streamType = dev.getStreamType(id)!!

            val mode: VolumeMode? = when (streamType) {
                AudioStream.Type.RINGTONE -> when (ringerMode) {
                    RingerMode.SILENT -> VolumeMode.Silent
                    RingerMode.VIBRATE -> VolumeMode.Vibrate
                    RingerMode.NORMAL -> VolumeMode.Normal(percentage)
                }

                AudioStream.Type.NOTIFICATION -> when (ringerMode) {
                    RingerMode.NORMAL -> VolumeMode.Normal(percentage)
                    else -> {
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

            // Skip persist if stored percentage already maps to the observed
            // hardware level — avoids the percent→level→percent round-trip that makes dashboard sliders jump.
            if (mode is VolumeMode.Normal) {
                val storedVolume = dev.getVolume(streamType)
                if (storedVolume != null) {
                    val storedMode = VolumeMode.fromFloat(storedVolume)
                    if (storedMode is VolumeMode.Normal) {
                        val storedLevel = percentageToLevel(storedMode.percentage, min, max)
                        if (storedLevel == event.newVolume) {
                            log(TAG, VERBOSE) {
                                "Stored ${storedMode.percentage} already maps to level ${event.newVolume}, skipping $dev"
                            }
                            return@forEach
                        }
                    }
                }
            }

            log(TAG, INFO) { "Saving new volume ($mode@$id) for ${dev.address}/${dev.label}" }
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
