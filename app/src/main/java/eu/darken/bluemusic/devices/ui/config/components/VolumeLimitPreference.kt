package eu.darken.bluemusic.devices.ui.config.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.MusicNote
import androidx.compose.material.icons.twotone.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper

/**
 * Picks the lowest and highest volume a stream may reach.
 *
 * [stepCount] is the stream's step count, so the thumbs land on levels the hardware actually has;
 * without one the slider stays continuous.
 */
@Composable
fun VolumeLimitPreference(
    title: String,
    description: String,
    icon: ImageVector,
    min: Float?,
    max: Float?,
    stepCount: Int?,
    onLimitChange: (Float?, Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = (min ?: 0f).coerceIn(0f, 1f)
    val end = (max ?: 1f).coerceIn(start, 1f)
    var range by remember(min, max) { mutableStateOf(start..end) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            // Writing on every frame of a drag would hit the database dozens of times per gesture.
            onValueChangeFinished = {
                // A thumb at its extreme is no bound at all: 0% and 100% resolve to the stream's own
                // bounds. Storing them as values would keep the foreground service alive for a band
                // that constrains nothing.
                onLimitChange(
                    range.start.takeIf { it > 0f },
                    range.endInclusive.takeIf { it < 1f },
                )
            },
            valueRange = 0f..1f,
            steps = stepCount?.let { it - 1 } ?: 0,
        )
    }
}

@Preview2
@Composable
private fun VolumeLimitPreferencePreview() {
    PreviewWrapper {
        Column {
            VolumeLimitPreference(
                title = "Limit for Music",
                description = "Between 20% and 70%",
                icon = Icons.TwoTone.MusicNote,
                min = 0.2f,
                max = 0.7f,
                stepCount = 15,
                onLimitChange = { _, _ -> },
            )
            VolumeLimitPreference(
                title = "Limit for Call",
                description = "No limit set",
                icon = Icons.TwoTone.Phone,
                min = null,
                max = null,
                stepCount = null,
                onLimitChange = { _, _ -> },
            )
        }
    }
}
