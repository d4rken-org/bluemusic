package eu.darken.bluemusic.eq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 * A read-only glance at the configured curve: one bar per band, growing up or down from the zero
 * line. Laid out left-to-right in every locale, bass-left is the convention the curve is read by.
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

    val barColor = when {
        isEnabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val zeroLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val description = stringResource(R.string.eq_mini_graph_desc)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(GRAPH_HEIGHT)
                .semantics { contentDescription = description },
        ) {
            val centerY = size.height / 2f
            drawLine(
                color = zeroLineColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = ZERO_LINE_WIDTH.toPx(),
            )

            val barWidth = BAR_WIDTH.toPx()
            val slot = size.width / levels.size
            // The rounded caps stick out by half a bar width, so the extremes stay inside the canvas.
            val usable = (centerY - barWidth / 2f).coerceAtLeast(0f)
            levels.forEachIndexed { index, level ->
                val x = slot * (index + 0.5f)
                val fraction = levelFraction(level, minLevel, maxLevel)
                drawLine(
                    color = barColor,
                    start = Offset(x, centerY),
                    end = Offset(x, centerY - fraction * usable),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * How far a band reaches out of the zero line, `-1..1`.
 *
 * Both directions are scaled against their own end of the range: engines are not symmetric on every
 * device, and a full cut should look as full as a full boost.
 */
private fun levelFraction(level: Int, minLevel: Int, maxLevel: Int): Float = when {
    level > 0 && maxLevel > 0 -> (level.toFloat() / maxLevel).coerceIn(0f, 1f)
    level < 0 && minLevel < 0 -> -(level.toFloat() / minLevel).coerceIn(0f, 1f)
    else -> 0f
}

private val GRAPH_HEIGHT = 40.dp
private val BAR_WIDTH = 3.dp
private val ZERO_LINE_WIDTH = 1.dp

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
private fun EqMiniGraphDisabledPreview() {
    PreviewWrapper {
        EqMiniGraph(
            levels = List(5) { 0 },
            minLevel = -1500,
            maxLevel = 1500,
            isEnabled = false,
        )
    }
}
