package eu.darken.bluemusic.monitor.core.audio

import androidx.compose.runtime.Immutable

/**
 * Bounds for a stream volume, as percentages of the stream's own level range.
 * A `null` bound means "unbounded in that direction".
 */
@Immutable
data class VolumeBand(
    val min: Float?,
    val max: Float?,
) {
    val isUnbounded: Boolean
        get() = min == null && max == null
}
