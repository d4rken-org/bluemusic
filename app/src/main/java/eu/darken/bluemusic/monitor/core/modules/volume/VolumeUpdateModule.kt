package eu.darken.bluemusic.monitor.core.modules.volume

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.bluetooth.core.SourceDevice
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
import eu.darken.bluemusic.monitor.core.audio.RouteVerdict
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.audio.levelToPercentage
import eu.darken.bluemusic.monitor.core.audio.percentageToLevel
import eu.darken.bluemusic.monitor.core.audio.routeVerdict
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
        log(TAG, DEBUG) { "Media route for $id: ${event.route?.description}" }

        val ownerAddresses = ownerRegistry.ownerAddressesFor(id).toSet()
        if (ownerAddresses.isEmpty()) {
            log(TAG, VERBOSE) { "No owner for $id, skipping persist" }
            return
        }

        val allDevices = deviceRepo.currentDevices()
        val allActive = allDevices.filter { it.isActive }

        val candidates = allActive.filter { dev ->
            if (dev.address !in ownerAddresses) return@filter false
            if (!dev.volumeObservingEffective) return@filter false
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

        // The owner registry is driven by ACL broadcasts alone; on some devices the media route
        // leaves the Bluetooth device over a second before ACL_DISCONNECTED (issue #232). Only
        // MUSIC is gated: the route query asks USAGE_MEDIA/CONTENT_TYPE_MUSIC, which says nothing
        // about who owns call, ringtone, notification or alarm audio.
        val agreeing = if (id == AudioStream.Id.STREAM_MUSIC) {
            val knownAddresses = allDevices.map { it.address }.toSet()
            stable.filter { dev ->
                val verdict = routeVerdict(
                    route = event.route,
                    isPhoneSpeaker = dev.type == SourceDevice.Type.PHONE_SPEAKER,
                    ownerAddresses = ownerAddresses,
                    knownAddresses = knownAddresses,
                )
                when (verdict) {
                    RouteVerdict.DISAGREE -> {
                        log(TAG, INFO) {
                            "Route disagrees with owner, skipping $id=${event.newVolume} for " +
                                "${dev.address}/${dev.label} (route=${event.route?.description}, " +
                                "routedTo=${event.route?.addresses}, owners=$ownerAddresses)"
                        }
                        false
                    }

                    RouteVerdict.UNKNOWN -> {
                        log(TAG, INFO) {
                            "No usable route classification for $id, persisting for " +
                                "${dev.address}/${dev.label} (route=${event.route?.description})"
                        }
                        true
                    }

                    RouteVerdict.AGREE -> true
                }
            }
        } else {
            stable
        }

        val ringerMode = ringerTool.getCurrentRingerMode()
        val min = volumeTool.getMinVolume(id)
        val max = volumeTool.getMaxVolume(id)

        // If VolumeRateLimiterModule (priority 5) was eligible to act on this stream, it may have
        // already corrected the jump this event reports, and its own write only produces a `self`
        // event we'd ignore — so persist the live hardware level instead of the event's value.
        // Without an eligible limiter we keep the event's snapshot: a later live read could catch
        // an unrelated route change instead (issue #232).
        val limiterMayHaveIntervened = allActive.any {
            it.volumeRateLimiterEffective && it.address in ownerAddresses && it.getStreamType(id) != null
        }
        val effectiveVolume = if (limiterMayHaveIntervened) volumeTool.getCurrentVolume(id) else event.newVolume
        if (effectiveVolume != event.newVolume) {
            log(TAG, DEBUG) { "Hardware level for $id is $effectiveVolume, not event's ${event.newVolume}, persisting hardware level" }
        }
        val percentage = levelToPercentage(effectiveVolume, min, max)

        agreeing.forEach { dev ->
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
                        if (effectiveVolume > 0) VolumeMode.Normal(percentage) else null
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
                        if (storedLevel == effectiveVolume) {
                            log(TAG, VERBOSE) {
                                "Stored ${storedMode.percentage} already maps to level $effectiveVolume, skipping $dev"
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
