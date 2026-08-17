package eu.darken.bluemusic.eq.core

import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.ca.CaString
import eu.darken.bluemusic.common.ca.toCaString
import kotlin.math.roundToInt

/**
 * Equalizer presets as normalized curves, independent of what the device's engine offers.
 *
 * A curve is five gains in `-1.0..+1.0` sitting at the relative positions `[0, .25, .5, .75, 1]` of
 * the frequency range. Curves are linearly interpolated to the engine's actual band count and then
 * scaled into its gain range, so the same preset works on a 5-band and a 10-band engine.
 */
class EqPresets {

    enum class Id {
        FLAT,
        BASS_BOOST,
        BASS_REDUCER,
        TREBLE_BOOST,
        VOCAL,
        LOUDNESS,
        ;
    }

    data class Preset(
        val id: Id,
        val label: CaString,
        val curve: List<Double>,
    )

    val presets: List<Preset> = listOf(
        Preset(Id.FLAT, R.string.eq_preset_flat_label.toCaString(), listOf(0.0, 0.0, 0.0, 0.0, 0.0)),
        Preset(Id.BASS_BOOST, R.string.eq_preset_bass_boost_label.toCaString(), listOf(1.0, 0.6, 0.2, 0.0, 0.0)),
        Preset(Id.BASS_REDUCER, R.string.eq_preset_bass_reducer_label.toCaString(), listOf(-1.0, -0.6, -0.2, 0.0, 0.0)),
        Preset(Id.TREBLE_BOOST, R.string.eq_preset_treble_boost_label.toCaString(), listOf(0.0, 0.0, 0.2, 0.6, 1.0)),
        Preset(Id.VOCAL, R.string.eq_preset_vocal_label.toCaString(), listOf(-0.2, 0.3, 0.6, 0.3, -0.2)),
        Preset(Id.LOUDNESS, R.string.eq_preset_loudness_label.toCaString(), listOf(0.8, 0.3, 0.0, 0.3, 0.8)),
    )

    fun byId(id: Id): Preset = presets.first { it.id == id }

    /**
     * Interpolates [id]'s curve to [caps]'s band count and scales it into the engine's gain range.
     */
    fun levelsFor(id: Id, caps: EqCapabilities.Caps): List<Int> = levelsFor(byId(id).curve, caps)

    fun levelsFor(curve: List<Double>, caps: EqCapabilities.Caps): List<Int> {
        if (caps.bandCount <= 0 || curve.isEmpty()) return emptyList()

        return (0 until caps.bandCount).map { band ->
            val position = if (caps.bandCount == 1) 0.5 else band.toDouble() / (caps.bandCount - 1)
            val gain = interpolate(curve, position)
            val scaled = if (gain >= 0) gain * caps.maxLevel else gain * -caps.minLevel
            scaled.roundToInt().coerceIn(caps.minLevel, caps.maxLevel)
        }
    }

    /** Sample [curve] at [position] (0..1), where the curve's points are spread evenly across 0..1. */
    private fun interpolate(curve: List<Double>, position: Double): Double {
        if (curve.size == 1) return curve.single()

        val clamped = position.coerceIn(0.0, 1.0)
        val scaled = clamped * (curve.size - 1)
        val lower = scaled.toInt().coerceAtMost(curve.size - 2)
        val fraction = scaled - lower
        return curve[lower] + (curve[lower + 1] - curve[lower]) * fraction
    }
}
