package eu.darken.bluemusic.devices.ui.volumelimit

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.devices.core.normalizeVolumeLimitMax
import eu.darken.bluemusic.devices.core.normalizeVolumeLimitMin
import eu.darken.bluemusic.devices.core.toVolumePercent
import eu.darken.bluemusic.monitor.core.audio.AudioStream

/**
 * Picks the lowest and highest volume a stream may reach.
 *
 * [stepCount] is the stream's step count, so the thumbs land on levels the hardware actually has;
 * without one the slider stays continuous.
 */
@Composable
fun VolumeLimitPreference(
    title: String,
    icon: ImageVector,
    min: Float?,
    max: Float?,
    stepCount: Int?,
    onLimitChange: (Float?, Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = (min ?: 0f).coerceIn(0f, 1f)
    val end = (max ?: 1f).coerceIn(start, 1f)
    var range by remember { mutableStateOf(start..end) }
    var dragging by remember { mutableStateOf(false) }

    // The stored bounds arrive as an echo of this screen's own write, so a change can land in the middle
    // of the next gesture. Adopting it there would pull the thumbs out from under the finger and make the
    // release commit the old band.
    LaunchedEffect(start, end) {
        if (!dragging) range = start..end
    }

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
                val (boundMin, boundMax) = range.toBounds()
                Text(
                    text = getVolumeLimitDescription(boundMin, boundMax),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        RangeSlider(
            value = range,
            onValueChange = {
                dragging = true
                range = it
            },
            // Writing on every frame of a drag would hit the database dozens of times per gesture.
            onValueChangeFinished = {
                dragging = false
                val (newMin, newMax) = range.toBounds()
                onLimitChange(newMin, newMax)
            },
            valueRange = 0f..1f,
            steps = stepCount?.let { it - 1 } ?: 0,
        )
    }
}

private fun ClosedFloatingPointRange<Float>.toBounds(): Pair<Float?, Float?> =
    normalizeVolumeLimitMin(start) to normalizeVolumeLimitMax(endInclusive)

@Composable
internal fun getStreamLabel(type: AudioStream.Type): String = when (type) {
    AudioStream.Type.MUSIC -> stringResource(R.string.devices_stream_music_label)
    AudioStream.Type.CALL -> stringResource(R.string.devices_audio_stream_call_label)
    AudioStream.Type.RINGTONE -> stringResource(R.string.devices_audio_stream_ring_label)
    AudioStream.Type.NOTIFICATION -> stringResource(R.string.devices_audio_stream_notification_label)
    AudioStream.Type.ALARM -> stringResource(R.string.devices_audio_stream_alarm_label)
}

@Composable
internal fun getVolumeLimitDescription(min: Float?, max: Float?): String = when {
    min != null && max != null -> stringResource(
        R.string.devices_device_config_volume_limit_range_desc,
        min.toVolumePercent(),
        max.toVolumePercent(),
    )

    min != null -> stringResource(R.string.devices_device_config_volume_limit_min_desc, min.toVolumePercent())
    max != null -> stringResource(R.string.devices_device_config_volume_limit_max_desc, max.toVolumePercent())
    else -> stringResource(R.string.devices_device_config_volume_limit_unset_desc)
}

@Preview2
@Composable
private fun VolumeLimitPreferencePreview() {
    PreviewWrapper {
        Column {
            VolumeLimitPreference(
                title = "Limit for Music",
                icon = Icons.TwoTone.MusicNote,
                min = 0.2f,
                max = 0.7f,
                stepCount = 15,
                onLimitChange = { _, _ -> },
            )
            VolumeLimitPreference(
                title = "Limit for Call",
                icon = Icons.TwoTone.Phone,
                min = null,
                max = null,
                stepCount = null,
                onLimitChange = { _, _ -> },
            )
        }
    }
}
