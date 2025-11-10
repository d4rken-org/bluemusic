package eu.darken.bluemusic.monitor.core.modules.volume

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class VolumeRateLimiterModule @Inject constructor(
    private val volumeTool: VolumeTool,
    private val deviceRepo: DeviceRepo,
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
    private val mutex = Mutex()

    override suspend fun handle(event: VolumeEvent) {
        val id = event.streamId
        val newVolume = event.newVolume
        val oldVolume = event.oldVolume

        // Ignore changes triggered by us
        if (volumeTool.wasUs(id, newVolume)) {
            log(TAG, VERBOSE) { "Volume change was triggered by us, ignoring it." }
            return
        }

        val currentTime = System.currentTimeMillis()
        val eligibleDevices = deviceRepo.currentDevices()
            .filter { it.isActive && it.volumeRateLimiter && it.getStreamType(id) != null }

        mutex.withLock {
            eligibleDevices.forEach { device ->
                processVolumeChange(device, id, oldVolume, newVolume, currentTime)
            }
        }
    }

    private suspend fun processVolumeChange(
        device: ManagedDevice,
        streamId: AudioStream.Id,
        oldVolume: Int,
        newVolume: Int,
        currentTime: Long
    ) {
        val streamType = device.getStreamType(streamId) ?: return

        val currentState = volumeStates[streamId]

        // Determine the reference volume (last allowed or old volume for initial state)
        val referenceVolume = currentState?.lastAllowedVolume ?: oldVolume.takeIf { it != -1 } ?: newVolume

        // Determine direction and select appropriate rate limit
        val volumeDiff = newVolume - referenceVolume
        val rateLimitMs = if (volumeDiff > 0) {
            device.volumeRateLimitIncreaseMs
        } else {
            device.volumeRateLimitDecreaseMs
        }

        // Check rate limiting
        if (currentState != null && (currentTime - currentState.lastChangeTimestamp) < rateLimitMs) {
            log(TAG) { "Volume changed too quickly for $streamType, reverting from $newVolume to $referenceVolume" }
            if (volumeTool.changeVolume(streamId = streamId, targetLevel = referenceVolume)) {
                log(TAG) { "Reverted volume for $streamType to $referenceVolume due to rate limiting" }
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
        }

        if (clampedVolume != newVolume) {
            log(TAG) { "Volume change limited for $streamType: requested=$newVolume, reference=$referenceVolume, limited to=$clampedVolume" }
            if (volumeTool.changeVolume(streamId = streamId, targetLevel = clampedVolume)) {
                log(TAG) { "Applied rate-limited volume for $streamType to $clampedVolume" }
                volumeStates[streamId] = VolumeState(clampedVolume, currentTime)
            }
        } else {
            // Volume change is within allowed range - accept it
            volumeStates[streamId] = VolumeState(newVolume, currentTime)
            log(TAG, VERBOSE) { "Allowed volume change for $streamType to $newVolume" }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: VolumeRateLimiterModule): VolumeModule
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "RateLimiter", "Module")
    }
}