package eu.darken.bluemusic.eq.core

import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Resolves what to actually write to an engine with [bandCount] bands and a `[minLevel]..[maxLevel]`
 * gain range.
 *
 * A stored curve is only trusted when its size matches the engine we were handed: band counts differ
 * between devices, and half-applying a curve from another engine would sound wrong in a way the user
 * can't see. Anything else falls back to flat, and every value is clamped into the engine's range.
 */
internal fun resolveBandLevels(
    stored: List<Int>,
    bandCount: Int,
    minLevel: Int,
    maxLevel: Int,
): List<Int> = when {
    bandCount <= 0 -> emptyList()
    stored.size != bandCount -> List(bandCount) { 0 }
    else -> stored.map { it.coerceIn(minLevel, maxLevel) }
}

/**
 * Resolves what boost we are willing to hand to a [LoudnessEnhancer].
 *
 * The slider can only produce `0..[EqEffectController.MAX_BOOST_GAIN_MB]`, but a restored backup can
 * carry any [Int], so an oversized or negative gain is clamped instead of reaching the engine raw.
 */
internal fun resolveBoostGain(stored: Int): Int = stored.coerceIn(0, EqEffectController.MAX_BOOST_GAIN_MB)

/**
 * Owns the actual [Equalizer] and [LoudnessEnhancer] instances attached to other apps' audio sessions.
 *
 * The framework shares a single engine per (effect type, session), so our levels mutate settings a
 * player app may own. Every attach snapshots the engine's original band levels and enabled flag,
 * and every release path puts them back before letting go. The enhancer is treated the same way:
 * its target gain and enabled flag are snapshotted and restored.
 */
@Singleton
class EqEffectController @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val tracker: EqSessionTracker,
) {

    private data class AttachedEffect(
        val equalizer: Equalizer,
        val originalLevels: List<Short>,
        val originalEnabled: Boolean,
        val booster: AttachedBooster? = null,
    )

    /** The loudness enhancer of a session, present only while a boost is configured. */
    private data class AttachedBooster(
        val enhancer: LoudnessEnhancer,
        val originalTargetGain: Float,
        val originalEnabled: Boolean,
    )

    private val lock = Any()
    private val effects = mutableMapOf<Int, AttachedEffect>()

    fun attachedSessionIds(): Set<Int> = synchronized(lock) { effects.keys.toSet() }

    /**
     * Attaches an equalizer to [sessionId], applies [levels] and, when [boostGain] is positive, also
     * attaches a loudness enhancer with that gain in millibel.
     *
     * [levels] is what the user configured. It is validated against the instance we actually get:
     * a stored curve that doesn't match the engine's band count is dropped in favour of a flat one.
     */
    suspend fun attach(sessionId: Int, levels: List<Int>, boostGain: Int): Unit = withContext(dispatcherProvider.Default) {
        val boost = resolveBoostGain(boostGain)
        synchronized(lock) {
            if (sessionId <= 0) {
                tracker.onAttachFailed(sessionId, "Invalid session id")
                return@synchronized
            }

            effects.remove(sessionId)?.let {
                log(TAG) { "attach($sessionId): Replacing existing effect ${it.equalizer.id}" }
                it.restoreAndReleaseQuietly()
            }

            if (effects.size >= MAX_ATTACHED) {
                log(TAG, WARN) { "attach($sessionId): Already at the $MAX_ATTACHED effect cap, ignoring" }
                tracker.onAttachFailed(sessionId, "Attached session cap reached")
                return@synchronized
            }

            var equalizer: Equalizer? = null
            var pendingEffect: AttachedEffect? = null
            try {
                equalizer = Equalizer(EFFECT_PRIORITY, sessionId)
                val attached = AttachedEffect(
                    equalizer = equalizer,
                    originalLevels = (0 until equalizer.numberOfBands).map { equalizer.getBandLevel(it.toShort()) },
                    originalEnabled = equalizer.enabled,
                )
                // From here on the failure path has to restore, not just release.
                pendingEffect = attached

                val detail = equalizer.applyLevels(sessionId, levels)

                val status = equalizer.setEnabled(true)
                if (status != AudioEffect.SUCCESS) throw IllegalStateException("setEnabled failed: $status")

                equalizer.setControlStatusListener { effect, controlGranted ->
                    synchronized(lock) {
                        val current = effects[sessionId]
                        if (current == null || current.equalizer !== effect) {
                            log(TAG, WARN) { "Stale control status for $sessionId: granted=$controlGranted, ignoring" }
                            return@synchronized
                        }
                        log(TAG) { "Control status for $sessionId: granted=$controlGranted" }
                        tracker.onControlChanged(sessionId, controlGranted)
                    }
                }

                // Non-fatal: an enhancer we cannot get costs the boost, not the curve.
                val booster = if (boost > 0) createBooster(sessionId, boost) else null
                val boosted = attached.copy(booster = booster)
                pendingEffect = boosted

                effects[sessionId] = boosted
                tracker.onAttached(sessionId, detail + boostDetail(boost, booster))

                // The listener only fires on later ownership changes, so the initial value has to be read.
                tracker.onControlChanged(sessionId, equalizer.hasControl())
            } catch (e: Throwable) {
                log(TAG, ERROR) { "attach($sessionId): Failed: ${e.asLog()}" }
                val cleanup = effects.remove(sessionId) ?: pendingEffect
                if (cleanup != null) cleanup.restoreAndReleaseQuietly() else equalizer?.releaseQuietly()
                tracker.onAttachFailed(sessionId, e.message ?: e.toString())
            }
        }
    }

    /** Applies [levels] to every currently attached effect. */
    suspend fun updateLevels(levels: List<Int>): Unit = withContext(dispatcherProvider.Default) {
        synchronized(lock) {
            if (effects.isEmpty()) return@synchronized
            log(TAG, VERBOSE) { "updateLevels($levels): ${effects.size} effects" }
            effects.forEach { (sessionId, effect) ->
                try {
                    effect.equalizer.applyLevels(sessionId, levels)
                } catch (e: Exception) {
                    log(TAG, WARN) { "updateLevels($sessionId) failed: ${e.asLog()}" }
                }
            }
        }
    }

    /**
     * Applies [gain] in millibel to every currently attached effect, creating or releasing the
     * loudness enhancer as the value crosses zero.
     */
    suspend fun updateBoost(gain: Int): Unit = withContext(dispatcherProvider.Default) {
        val boost = resolveBoostGain(gain)
        synchronized(lock) {
            if (effects.isEmpty()) return@synchronized
            log(TAG, VERBOSE) { "updateBoost($boost): ${effects.size} effects" }
            effects.keys.toList().forEach { sessionId ->
                val effect = effects[sessionId] ?: return@forEach
                effects[sessionId] = effect.withBoost(sessionId, boost)
            }
        }
    }

    suspend fun detach(sessionId: Int): Unit = withContext(dispatcherProvider.Default) {
        synchronized(lock) {
            val effect = effects.remove(sessionId)
            log(TAG) { "detach($sessionId): effect=${effect?.equalizer?.id}" }
            effect?.restoreAndReleaseQuietly()
            tracker.onDetached(sessionId, if (effect != null) "Released effect" else "Nothing attached")
        }
    }

    suspend fun detachAll(): Unit = withContext(dispatcherProvider.Default) {
        synchronized(lock) {
            if (effects.isEmpty()) return@synchronized
            log(TAG, INFO) { "detachAll(): ${effects.size} effects" }
            effects.keys.toList().forEach { sessionId ->
                effects.remove(sessionId)?.restoreAndReleaseQuietly()
                tracker.onDetached(sessionId, "Released effect")
            }
            effects.clear()
        }
    }

    /**
     * Applies [levels] to this instance. A stored curve is only trusted when its size matches the
     * instance's band count, otherwise the engine is flattened instead of half-applying a curve.
     */
    private fun Equalizer.applyLevels(sessionId: Int, levels: List<Int>): String {
        val bandCount = numberOfBands.toInt()
        val range = bandLevelRange
        val min = range[0]
        val max = range[1]

        if (levels.isNotEmpty() && levels.size != bandCount) {
            log(TAG, WARN) {
                "applyLevels($sessionId): Stored ${levels.size} levels but engine has $bandCount bands, using flat"
            }
        }

        val effective = resolveBandLevels(levels, bandCount, min.toInt(), max.toInt())

        effective.forEachIndexed { band, level ->
            setBandLevel(band.toShort(), level.toShort())
        }

        val applied = (0 until bandCount).map { getBandLevel(it.toShort()) }
        log(TAG, VERBOSE) { "applyLevels($sessionId): id=$id bands=$bandCount levels=$applied range=$min..$max" }
        return "id=$id bands=$bandCount levels=$applied"
    }

    /**
     * Moves this effect to [gain]: the enhancer is created when the boost turns positive, released
     * when it drops to zero, and retargeted in between. A failure keeps the equalizer as it is.
     */
    private fun AttachedEffect.withBoost(sessionId: Int, gain: Int): AttachedEffect {
        if (gain <= 0) {
            booster?.restoreAndReleaseQuietly()
            return copy(booster = null)
        }

        val current = booster ?: return copy(booster = createBooster(sessionId, gain))

        return try {
            current.enhancer.applyGain(gain)
            log(TAG, VERBOSE) { "updateBoost($sessionId): gain=$gain" }
            this
        } catch (e: Exception) {
            log(TAG, WARN) { "updateBoost($sessionId) failed: ${e.asLog()}" }
            this
        }
    }

    /**
     * Creates a loudness enhancer for [sessionId] at [gain], or `null` when the device won't give us
     * one. The enhancer is optional: a session keeps its curve either way.
     */
    private fun createBooster(sessionId: Int, gain: Int): AttachedBooster? {
        var enhancer: LoudnessEnhancer? = null
        var pendingBooster: AttachedBooster? = null
        return try {
            enhancer = LoudnessEnhancer(sessionId)
            // Shared engine again, so the snapshot has to exist before the first write.
            val booster = AttachedBooster(
                enhancer = enhancer,
                originalTargetGain = enhancer.targetGain,
                originalEnabled = enhancer.enabled,
            )
            // From here on the failure path has to restore, not just release.
            pendingBooster = booster

            booster.enhancer.applyGain(gain)
            log(TAG, VERBOSE) { "createBooster($sessionId): id=${booster.enhancer.id} gain=$gain" }
            booster
        } catch (e: Throwable) {
            // A snapshot read that throws leaves an engine we own but have nothing to restore from.
            val cleanup = pendingBooster
            if (cleanup != null) cleanup.restoreAndReleaseQuietly() else enhancer?.releaseQuietly()
            log(TAG, WARN) { "createBooster($sessionId, $gain): Failed, continuing without boost: ${e.asLog()}" }
            null
        }
    }

    private fun LoudnessEnhancer.applyGain(gain: Int) {
        setTargetGain(gain)
        val status = setEnabled(true)
        if (status != AudioEffect.SUCCESS) throw IllegalStateException("Boost setEnabled failed: $status")
    }

    private fun boostDetail(boostGain: Int, booster: AttachedBooster?): String = when {
        boostGain <= 0 -> ""
        booster == null -> " boost=failed"
        else -> " boost=${boostGain}mB"
    }

    /**
     * Puts the engine back the way we found it before letting go of it, otherwise our curve would
     * stick around for whoever else is using this shared engine.
     */
    private fun AttachedEffect.restoreAndReleaseQuietly() {
        booster?.restoreAndReleaseQuietly()
        try {
            if (equalizer.hasControl()) {
                originalLevels.forEachIndexed { band, level ->
                    try {
                        equalizer.setBandLevel(band.toShort(), level)
                    } catch (e: Exception) {
                        log(TAG, WARN) { "restore(): setBandLevel($band) failed: ${e.asLog()}" }
                    }
                }
                try {
                    equalizer.setEnabled(originalEnabled)
                } catch (e: Exception) {
                    log(TAG, WARN) { "restore(): setEnabled($originalEnabled) failed: ${e.asLog()}" }
                }
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "restore() failed: ${e.asLog()}" }
        } finally {
            equalizer.releaseQuietly()
        }
    }

    /** Same contract as the equalizer's restore: the enhancer engine is shared with whoever else uses it. */
    private fun AttachedBooster.restoreAndReleaseQuietly() {
        try {
            if (enhancer.hasControl()) {
                try {
                    enhancer.setTargetGain(originalTargetGain.roundToInt())
                } catch (e: Exception) {
                    log(TAG, WARN) { "restoreBoost(): setTargetGain($originalTargetGain) failed: ${e.asLog()}" }
                }
                try {
                    enhancer.setEnabled(originalEnabled)
                } catch (e: Exception) {
                    log(TAG, WARN) { "restoreBoost(): setEnabled($originalEnabled) failed: ${e.asLog()}" }
                }
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "restoreBoost() failed: ${e.asLog()}" }
        } finally {
            enhancer.releaseQuietly()
        }
    }

    private fun Equalizer.releaseQuietly() = try {
        release()
    } catch (e: Exception) {
        log(TAG, WARN) { "release() failed: ${e.asLog()}" }
    }

    private fun LoudnessEnhancer.releaseQuietly() = try {
        release()
    } catch (e: Exception) {
        log(TAG, WARN) { "Boost release() failed: ${e.asLog()}" }
    }

    companion object {
        internal val TAG = logTag("Eq", "EffectController")
        private const val EFFECT_PRIORITY = 1000

        /** Upper bound on concurrently attached effects, a misbehaving app can spam OPEN broadcasts. */
        const val MAX_ATTACHED = 8

        /** Highest boost we hand to a loudness enhancer in millibel, +10 dB. Also the slider's top end. */
        const val MAX_BOOST_GAIN_MB = 1000
    }
}
