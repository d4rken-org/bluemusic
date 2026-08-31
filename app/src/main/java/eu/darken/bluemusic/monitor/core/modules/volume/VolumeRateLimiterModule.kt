package eu.darken.bluemusic.monitor.core.modules.volume

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.time.MonotonicClock
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeLimitEnforcer
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class VolumeRateLimiterModule @Inject constructor(
    private val volumeTool: VolumeTool,
    private val limitEnforcer: VolumeLimitEnforcer,
    private val deviceRepo: DeviceRepo,
    private val ownerRegistry: AudioStreamOwnerRegistry,
    private val clock: MonotonicClock,
) : VolumeModule {

    override val tag: String
        get() = TAG

    // Run before VolumeUpdateModule (priority 10) so it does not save the sudden change we revert
    override val priority: Int = 5

    private data class VolumeState(
        val lastAllowedVolume: Int,
        val lastChangeTimestamp: Long
    )

    private val volumeStates = mutableMapOf<AudioStream.Id, VolumeState>()
    private var lastSeenGeneration: Long = -1L
    private val mutex = Mutex()

    override suspend fun handle(event: VolumeEvent) {
        val id = event.streamId
        val newVolume = event.newVolume
        val oldVolume = event.oldVolume

        // Ignore changes triggered by us, but update our reference
        if (event.self) {
            log(TAG, VERBOSE) { "Volume change was triggered by us, ignoring it." }
            mutex.withLock {
                volumeStates[id] = VolumeState(newVolume, clock.nowMs())
            }
            return
        }

        val ownerAddresses = ownerRegistry.ownerAddressesFor(id).toSet()
        if (ownerAddresses.isEmpty()) return

        val currentTime = clock.nowMs()
        val allDevices = deviceRepo.currentDevices()
        val eligibleDevices = allDevices
            .filter {
                it.isActive &&
                    it.volumeRateLimiterEffective &&
                    it.address in ownerAddresses &&
                    it.getStreamType(id) != null
            }

        if (eligibleDevices.isEmpty()) return

        // A rate-limited stream can also be a capped one, and the two run as separate modules. The
        // band is resolved across the whole owner group, not just the rate-limited members.
        val allowedLevels = limitEnforcer.allowedLevels(
            streamId = id,
            devices = allDevices,
            ownerAddresses = ownerAddresses,
        )

        // Clear state when ownership changes
        val currentGeneration = ownerRegistry.ownershipGeneration()
        mutex.withLock {
            if (currentGeneration != lastSeenGeneration) {
                log(TAG, VERBOSE) { "Ownership generation changed ($lastSeenGeneration → $currentGeneration), clearing rate limiter state" }
                volumeStates.clear()
                lastSeenGeneration = currentGeneration
            }

            processVolumeChange(eligibleDevices, id, oldVolume, newVolume, currentTime, allowedLevels)
        }
    }

    /**
     * One decision per stream and event: at most one hardware call and at most one state update.
     * Iterating the group instead would mutate the shared per-stream state once per device,
     * making the outcome depend on repository iteration order (and letting a 0ms member
     * step past a sibling's window).
     *
     * [allowedLevels] bounds every level this can write or remember: the reference can predate a
     * newly tightened cap, and a single-step move can still land outside one.
     */
    private suspend fun processVolumeChange(
        devices: Collection<ManagedDevice>,
        streamId: AudioStream.Id,
        oldVolume: Int,
        newVolume: Int,
        currentTime: Long,
        allowedLevels: IntRange?,
    ) {
        val currentState = volumeStates[streamId]

        // Determine the reference volume (last allowed or old volume for initial state)
        val referenceVolume = (currentState?.lastAllowedVolume ?: oldVolume.takeIf { it != -1 } ?: newVolume)
            .within(allowedLevels)

        // Determine direction, then let the most restrictive (longest) window in the owner group
        // govern the whole group. Owner groups are usually paired earbuds, but same-name devices
        // connecting within the grouping window (or bootstrap entries) can group too — they are
        // already treated as one owner everywhere else.
        val volumeDiff = newVolume - referenceVolume
        val rateLimitMs = if (volumeDiff > 0) {
            devices.maxOf { it.volumeRateLimitIncreaseMs }
        } else {
            devices.maxOf { it.volumeRateLimitDecreaseMs }
        }
        val members = devices.map { "${it.address}/${it.label}" }.sorted()

        // Check rate limiting
        if (currentState != null && (currentTime - currentState.lastChangeTimestamp) < rateLimitMs) {
            log(TAG) { "Volume changed too quickly for $streamId $members, reverting from $newVolume to $referenceVolume" }
            if (volumeTool.changeVolume(streamId = streamId, targetLevel = referenceVolume)) {
                log(TAG) { "Reverted volume for $streamId to $referenceVolume due to rate limiting" }
            }
            // Update timestamp to reset the timer
            volumeStates[streamId] = VolumeState(referenceVolume, currentTime)
            return
        }

        // Apply volume step limiting
        val clampedVolume = when {
            volumeDiff > 1 -> referenceVolume + 1
            volumeDiff < -1 -> referenceVolume - 1
            else -> newVolume
        }.within(allowedLevels)

        if (clampedVolume != newVolume) {
            log(TAG) { "Volume change limited for $streamId $members: requested=$newVolume, reference=$referenceVolume, limited to=$clampedVolume" }
            if (volumeTool.changeVolume(streamId = streamId, targetLevel = clampedVolume)) {
                log(TAG) { "Applied rate-limited volume for $streamId to $clampedVolume" }
                volumeStates[streamId] = VolumeState(clampedVolume, currentTime)
            }
        } else {
            // Volume change is within allowed range - accept it
            volumeStates[streamId] = VolumeState(newVolume, currentTime)
            log(TAG, VERBOSE) { "Allowed volume change for $streamId to $newVolume" }
        }
    }

    private fun Int.within(allowedLevels: IntRange?): Int =
        if (allowedLevels == null) this else coerceIn(allowedLevels.first, allowedLevels.last)

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: VolumeRateLimiterModule): VolumeModule
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "RateLimiter", "Module")
    }
}
