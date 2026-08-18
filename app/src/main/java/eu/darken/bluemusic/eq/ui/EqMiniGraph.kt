package eu.darken.bluemusic.eq.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper

/**
 * A read-only glance at the configured curve: one pill per band, resting on a common center. A band
 * at zero is a circle, a boosted one grows upwards, a cut one downwards.
 *
 * The cluster has its own width instead of filling the row, so it reads as one object next to
 * whatever it is placed with. Laid out left-to-right in every locale, bass-left is the convention
 * the curve is read by.
 */
@Composable
fun EqMiniGraph(
    levels: List<Int>,
    minLevel: Int,
    maxLevel: Int,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (levels.isEmpty()) return

    val trackColor = when {
        isEnabled -> MaterialTheme.colorScheme.primary.copy(alpha = TRACK_ALPHA_ENABLED)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TRACK_ALPHA_DISABLED)
    }
    val fillColor = when {
        isEnabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = stringResource(R.string.eq_mini_graph_desc)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .height(GRAPH_HEIGHT)
                .semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(PILL_SPACING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            levels.forEach { level ->
                EqPill(
                    fraction = levelFraction(level, minLevel, maxLevel),
                    trackColor = trackColor,
                    fillColor = fillColor,
                )
            }
        }
    }
}

@Composable
private fun EqPill(
    fraction: Float,
    trackColor: Color,
    fillColor: Color,
) {
    // Held as a State and only read inside the draw scope: an animation frame redraws the pill
    // instead of recomposing it. It also starts at its first target, so a preview or a screenshot
    // renders the final curve on the first frame.
    val animated: State<Float> = animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "eqBandLevel",
    )

    Canvas(
        modifier = Modifier
            .width(PILL_WIDTH)
            .fillMaxHeight(),
    ) {
        val radius = size.width / 2f
        drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(radius, radius),
        )

        val centerY = size.height / 2f
        // The round cap reaches half a pill width past the endpoint, so the travel stops that far
        // from the edge and a full band ends flush with the track. The bounce is clamped along the
        // way: an overshoot would otherwise push the fill out of its own track.
        val travel = (centerY - radius).coerceAtLeast(0f)
        val end = centerY - animated.value.coerceIn(-1f, 1f) * travel
        drawLine(
            color = fillColor,
            start = Offset(radius, centerY),
            end = Offset(radius, end),
            strokeWidth = size.width,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * How far a band reaches out of the center, `-1..1`.
 *
 * Both directions are scaled against their own end of the range: engines are not symmetric on every
 * device, and a full cut should look as full as a full boost.
 */
private fun levelFraction(level: Int, minLevel: Int, maxLevel: Int): Float = when {
    level > 0 && maxLevel > 0 -> (level.toFloat() / maxLevel).coerceIn(0f, 1f)
    level < 0 && minLevel < 0 -> -(level.toFloat() / minLevel).coerceIn(0f, 1f)
    else -> 0f
}

private val GRAPH_HEIGHT = 48.dp
private val PILL_WIDTH = 10.dp
private val PILL_SPACING = 8.dp
private const val TRACK_ALPHA_ENABLED = 0.1f
private const val TRACK_ALPHA_DISABLED = 0.08f

@Preview2
@Composable
private fun EqMiniGraphPreview() {
    PreviewWrapper {
        EqMiniGraph(
            levels = listOf(900, 300, 0, -300, -1200),
            minLevel = -1500,
            maxLevel = 1500,
            isEnabled = true,
        )
    }
}

@Preview2
@Composable
private fun EqMiniGraphFlatPreview() {
    PreviewWrapper {
        EqMiniGraph(
            levels = List(5) { 0 },
            minLevel = -1500,
            maxLevel = 1500,
            isEnabled = false,
        )
    }
}

/** The ends of the range: the caps have to stay inside the track, not sit on its edge. */
@Preview2
@Composable
private fun EqMiniGraphExtremesPreview() {
    PreviewWrapper {
        EqMiniGraph(
            levels = listOf(1500, -1500, 1500, -1500, 0),
            minLevel = -1500,
            maxLevel = 1500,
            isEnabled = true,
        )
    }
}

@Preview2
@Composable
private fun EqMiniGraphManyBandsPreview() {
    PreviewWrapper {
        EqMiniGraph(
            levels = listOf(1500, 1100, 700, 300, 0, -300, -700, -1100, -1500, 600),
            minLevel = -1500,
            maxLevel = 1500,
            isEnabled = true,
        )
    }
}
