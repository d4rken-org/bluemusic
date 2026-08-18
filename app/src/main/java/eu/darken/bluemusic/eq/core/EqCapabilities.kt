package eu.darken.bluemusic.eq.core

import android.media.AudioManager
import android.media.audiofx.Equalizer
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the device's equalizer engine can actually do: band count, gain range and band frequencies.
 *
 * Probed on a throwaway audio session, never on a session we or another app is playing on, and the
 * probe effect is released immediately and never enabled.
 */
@Singleton
class EqCapabilities @Inject constructor(
    private val audioManager: AudioManager,
    private val dispatcherProvider: DispatcherProvider,
) {

    data class Caps(
        /** Number of bands the engine exposes. */
        val bandCount: Int,
        /** Lowest supported band level in millibel. */
        val minLevel: Int,
        /** Highest supported band level in millibel. */
        val maxLevel: Int,
        /** Center frequency per band in milliHertz, ordered by band index. */
        val centerFrequencies: List<Int>,
    )

    private val probeLock = Mutex()

    private val _capabilities = MutableStateFlow<Caps?>(null)
    val capabilities: StateFlow<Caps?> = _capabilities.asStateFlow()

    /**
     * Probes the engine unless we already have a result. A failed probe is not cached: a transient
     * failure (e.g. the effect framework being busy) must not brand the device as unsupported.
     */
    suspend fun refreshIfNeeded(): Caps? {
        _capabilities.value?.let { return it }
        return probeLock.withLock {
            _capabilities.value?.let { return@withLock it }
            val probed = probe()
            if (probed != null) _capabilities.value = probed
            probed
        }
    }

    private suspend fun probe(): Caps? = withContext(dispatcherProvider.Default) {
        var equalizer: Equalizer? = null
        try {
            val sessionId = audioManager.generateAudioSessionId()
            if (sessionId == AudioManager.ERROR) throw IllegalStateException("Could not generate an audio session id")

            equalizer = Equalizer(PROBE_PRIORITY, sessionId)
            val bandCount = equalizer.numberOfBands.toInt()
            val range = equalizer.bandLevelRange
            val caps = Caps(
                bandCount = bandCount,
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                centerFrequencies = (0 until bandCount).map { equalizer.getCenterFreq(it.toShort()) },
            )
            log(TAG, INFO) { "probe(): $caps" }
            if (caps.bandCount <= 0 || caps.minLevel >= caps.maxLevel) {
                log(TAG, WARN) { "probe(): Unusable equalizer engine: $caps" }
                return@withContext null
            }
            caps
        } catch (e: Throwable) {
            log(TAG, WARN) { "probe(): Failed, will retry on next request: ${e.asLog()}" }
            null
        } finally {
            try {
                equalizer?.release()
            } catch (e: Exception) {
                log(TAG, WARN) { "probe(): release() failed: ${e.asLog()}" }
            }
        }
    }

    companion object {
        private val TAG = logTag("Eq", "Capabilities")

        // Low priority, the probe never enables the effect and lets go of it immediately.
        private const val PROBE_PRIORITY = 0
    }
}

/**
 * The stored curve as the UI should draw it: a curve that doesn't fit the engine we have falls back
 * to flat, exactly like the one we would write to the effect.
 */
fun EqCapabilities.Caps?.levelsOf(stored: List<Int>?): List<Int> {
    if (this == null) return emptyList()
    return resolveBandLevels(stored ?: emptyList(), bandCount, minLevel, maxLevel)
}
