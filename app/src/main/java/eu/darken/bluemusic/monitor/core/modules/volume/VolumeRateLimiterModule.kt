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
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.StreamHelper
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class VolumeRateLimiterModule @Inject constructor(
    private val streamHelper: StreamHelper,
    private val deviceRepo: DeviceRepo,
) : VolumeModule {

    private data class VolumeState(
        val lastAllowedVolume: Int,
        val lastChangeTimestamp: Long
    )

    private val volumeStates = mutableMapOf<AudioStream.Id, VolumeState>()
    private val mutex = Mutex()

    override suspend fun handle(id: AudioStream.Id, volume: Int) {
        // Check if this change was triggered by us
        if (streamHelper.wasUs(id, volume)) {
            log(TAG, VERBOSE) { "Volume change was triggered by us, ignoring it." }
            return
        }

        val currentTime = System.currentTimeMillis()

        deviceRepo.currentDevices()
            .filter { device -> device.isActive && device.volumeRateLimiter && device.getStreamType(id) != null }
            .forEach { device ->
                val type = device.getStreamType(id)!!

                mutex.withLock {
                    val state = volumeStates[id]

                    if (state != null) {
                        val timeSinceLastChange = currentTime - state.lastChangeTimestamp
                        val rateLimitMs = device.volumeRateLimitMs

                        // Check if enough time has passed for any change
                        if (timeSinceLastChange < rateLimitMs) {
                            // Not enough time has passed - revert to last allowed volume
                            log(TAG) { "Volume changed too quickly for $type (${timeSinceLastChange}ms < ${rateLimitMs}ms), reverting from $volume to ${state.lastAllowedVolume}" }

                            if (streamHelper.changeVolume(streamId = id, targetLevel = state.lastAllowedVolume)) {
                                log(TAG) { "Reverted volume for $type to ${state.lastAllowedVolume} due to rate limiting" }
                            }
                            return@forEach
                        }

                        // Enough time has passed - limit change to 1 step
                        val volumeDiff = volume - state.lastAllowedVolume
                        val clampedVolume = when {
                            volumeDiff > 1 -> state.lastAllowedVolume + 1
                            volumeDiff < -1 -> state.lastAllowedVolume - 1
                            else -> volume
                        }

                        if (clampedVolume != volume) {
                            log(TAG) { "Volume change limited for $type: requested=$volume, last=${state.lastAllowedVolume}, limited to=$clampedVolume" }

                            if (streamHelper.changeVolume(streamId = id, targetLevel = clampedVolume)) {
                                log(TAG) { "Applied rate-limited volume for $type to $clampedVolume" }
                                // Update state with the limited volume
                                volumeStates[id] = VolumeState(clampedVolume, currentTime)
                            }
                            return@forEach
                        }
                    }

                    // Volume change is within allowed range - accept it
                    volumeStates[id] = VolumeState(volume, currentTime)
                    log(TAG, VERBOSE) { "Allowed volume change for $type to $volume at $currentTime" }
                }
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
