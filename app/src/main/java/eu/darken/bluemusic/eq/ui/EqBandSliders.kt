package eu.darken.bluemusic.eq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import java.util.Locale
import kotlin.math.roundToInt

/** One band of the equalizer curve, with its labels already formatted for display. */
@Immutable
data class EqBandUi(
    val frequencyLabel: String,
    val gainLabel: String,
    val level: Int,
)

/**
 * The classic equalizer curve: one vertical slider per band, lowest frequency on the left,
 * max gain at the top. The row is laid out left-to-right in every locale, bass-left is the
 * convention the curve is read by.
 *
 * The row uses every bit of width it is given: horizontal padding is the caller's, so a row nested
 * in a card can keep the bands as wide as they are on the bare screen. The bands share what is left
 * of that width evenly, and each column is a full-height drag target, so a many-band engine gets
 * narrower columns instead of a row that runs off the screen.
 */
@Composable
fun EqBandRow(
    bands: List<EqBandUi>,
    minLevel: Int,
    maxLevel: Int,
    onLevelChange: (Int, Int) -> Unit,
    onLevelChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bands.isEmpty()) return

    val labelStyle = MaterialTheme.typography.labelSmall
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val zeroLineColor = mutedColor.copy(alpha = 0.4f)
    val zeroLabel = stringResource(R.string.eq_gain_db_label, String.format(Locale.getDefault(), "%d", 0))
    val textMeasurer = rememberTextMeasurer()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LABEL_SPACING),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(ZERO_LABEL_GUTTER))
                bands.forEach { band ->
                    Text(
                        text = band.gainLabel,
                        style = labelStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TRACK_HEIGHT),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val inset = TRACK_INSET.toPx()
                    val zeroY = levelToY(0, minLevel, maxLevel, size.height, inset)
                    val gutter = ZERO_LABEL_GUTTER.toPx()
                    // The bands split what is left of the width evenly, so the line can stop under
                    // the last slider instead of running out to the edge of whatever holds us.
                    val columnWidth = (size.width - gutter) / bands.size
                    drawLine(
                        color = zeroLineColor,
                        start = Offset(gutter, zeroY),
                        end = Offset(gutter + columnWidth * (bands.size - 0.5f), zeroY),
                        strokeWidth = ZERO_LINE_WIDTH.toPx(),
                    )
                    // Measured LTR like the rest of the row, an RTL locale would otherwise flip the unit
                    // behind the number while the band labels above stay left-to-right.
                    val measured = textMeasurer.measure(
                        text = zeroLabel,
                        style = labelStyle,
                        maxLines = 1,
                        layoutDirection = LayoutDirection.Ltr,
                    )
                    val labelEnd = gutter - ZERO_LABEL_SPACING.toPx()
                    if (measured.size.width <= labelEnd) {
                        drawText(
                            textLayoutResult = measured,
                            color = mutedColor,
                            topLeft = Offset(labelEnd - measured.size.width, zeroY - measured.size.height / 2f),
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.width(ZERO_LABEL_GUTTER))
                    bands.forEachIndexed { index, band ->
                        EqBandSlider(
                            band = band,
                            minLevel = minLevel,
                            maxLevel = maxLevel,
                            onLevelChange = { level -> onLevelChange(index, level) },
                            onLevelChangeFinished = onLevelChangeFinished,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(ZERO_LABEL_GUTTER))
                bands.forEach { band ->
                    Text(
                        text = band.frequencyLabel,
                        style = labelStyle,
                        color = mutedColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EqBandSlider(
    band: EqBandUi,
    minLevel: Int,
    maxLevel: Int,
    onLevelChange: (Int) -> Unit,
    onLevelChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLevel by rememberUpdatedState(band.level)
    val currentOnLevelChange by rememberUpdatedState(onLevelChange)
    val currentOnFinished by rememberUpdatedState(onLevelChangeFinished)

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary
    val rangeInfo = ProgressBarRangeInfo(
        current = band.level.toFloat().coerceIn(minLevel.toFloat(), maxLevel.toFloat()),
        range = minLevel.toFloat()..maxLevel.toFloat(),
    )

    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = band.frequencyLabel
                stateDescription = band.gainLabel
                progressBarRangeInfo = rangeInfo
                setProgress { target ->
                    currentOnLevelChange(target.roundToInt().coerceIn(minLevel, maxLevel))
                    currentOnFinished()
                    true
                }
            }
            .pointerInput(minLevel, maxLevel) {
                val inset = TRACK_INSET.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // The gesture keeps its own idea of what it last emitted, the composed level lags
                    // behind a fast drag and would swallow the emission for a level the finger left
                    // and came back to before recomposition.
                    var lastEmittedLevel = currentLevel
                    // Every position change is consumed, otherwise the surrounding list would read the
                    // vertical drag as a scroll and take the gesture away mid-curve.
                    fun emit(change: PointerInputChange) {
                        change.consume()
                        val level = yToLevel(change.position.y, minLevel, maxLevel, size.height.toFloat(), inset)
                        if (level == lastEmittedLevel) return
                        lastEmittedLevel = level
                        currentOnLevelChange(level)
                    }
                    try {
                        emit(down)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.positionChanged()) emit(change)
                        }
                    } finally {
                        // Cancellation from outside the gesture loop (rotation, disposal mid-drag) has to
                        // end the live preview too, the value would otherwise stay unpersisted.
                        currentOnFinished()
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = TRACK_INSET.toPx()
            val centerX = size.width / 2f
            val topY = inset
            val bottomY = size.height - inset
            val zeroY = levelToY(0, minLevel, maxLevel, size.height, inset)
            val levelY = levelToY(band.level, minLevel, maxLevel, size.height, inset)
            drawLine(
                color = trackColor,
                start = Offset(centerX, topY),
                end = Offset(centerX, bottomY),
                strokeWidth = TRACK_WIDTH.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = activeColor,
                start = Offset(centerX, zeroY),
                end = Offset(centerX, levelY),
                strokeWidth = TRACK_WIDTH.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = activeColor,
                radius = THUMB_RADIUS.toPx(),
                center = Offset(centerX, levelY),
            )
        }
    }
}

private fun levelFraction(level: Int, minLevel: Int, maxLevel: Int): Float {
    val span = (maxLevel - minLevel).toFloat()
    if (span <= 0f) return 0f
    return ((level - minLevel) / span).coerceIn(0f, 1f)
}

/** Where a level sits on the track, the top of the track being the maximum gain. */
private fun levelToY(level: Int, minLevel: Int, maxLevel: Int, heightPx: Float, insetPx: Float): Float {
    val usable = (heightPx - 2 * insetPx).coerceAtLeast(0f)
    return insetPx + (1f - levelFraction(level, minLevel, maxLevel)) * usable
}

private fun yToLevel(y: Float, minLevel: Int, maxLevel: Int, heightPx: Float, insetPx: Float): Int {
    val usable = (heightPx - 2 * insetPx).coerceAtLeast(1f)
    val fraction = 1f - ((y - insetPx) / usable).coerceIn(0f, 1f)
    return (minLevel + fraction * (maxLevel - minLevel)).roundToInt()
}

private val TRACK_HEIGHT = 140.dp
private val TRACK_WIDTH = 6.dp
private val THUMB_RADIUS = 9.dp

/**
 * How far the ends of the travel stay away from the edges of the drawing area.
 *
 * A thumb at the very end of its range was drawn exactly on the edge before, which reads as a
 * cut-off circle: the room past the thumb radius keeps it whole at both extremes.
 */
private val TRACK_INSET = THUMB_RADIUS + 3.dp

/** Between the gain labels, the sliders and the frequency labels. */
private val LABEL_SPACING = 8.dp

private val ZERO_LINE_WIDTH = 1.dp
private val ZERO_LABEL_GUTTER = 32.dp
private val ZERO_LABEL_SPACING = 4.dp

private fun previewBands(levels: List<Int>): List<EqBandUi> {
    val frequencies = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
    return levels.mapIndexed { index, level ->
        EqBandUi(
            frequencyLabel = frequencies.getOrElse(index) { "?" },
            gainLabel = String.format(Locale.US, "%+.1f dB", level / 100f),
            level = level,
        )
    }
}

@Preview2
@Composable
private fun EqBandRowPreview() {
    PreviewWrapper {
        EqBandRow(
            bands = previewBands(listOf(900, 300, 0, -300, 600)),
            minLevel = -1500,
            maxLevel = 1500,
            onLevelChange = { _, _ -> },
            onLevelChangeFinished = {},
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/** The ends of the range: both thumbs have to be whole circles, not half ones on the edge. */
@Preview2
@Composable
private fun EqBandRowExtremesPreview() {
    PreviewWrapper {
        EqBandRow(
            bands = previewBands(listOf(1500, -1500, 0, 1500, -1500)),
            minLevel = -1500,
            maxLevel = 1500,
            onLevelChange = { _, _ -> },
            onLevelChangeFinished = {},
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Preview2
@Composable
private fun EqBandRowFlatPreview() {
    PreviewWrapper {
        EqBandRow(
            bands = previewBands(List(5) { 0 }),
            minLevel = -1500,
            maxLevel = 1500,
            onLevelChange = { _, _ -> },
            onLevelChangeFinished = {},
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
