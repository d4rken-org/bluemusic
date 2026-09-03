package eu.darken.bluemusic.devices.ui.config.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.UpgradeBadge
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.normalizeVolumeLimitMax
import eu.darken.bluemusic.devices.core.normalizeVolumeLimitMin
import eu.darken.bluemusic.devices.ui.icon
import eu.darken.bluemusic.devices.ui.volumelimit.getVolumeLimitDescription
import eu.darken.bluemusic.monitor.core.audio.AudioStream

/** One bounded stream, i.e. a row of the card's summary. */
data class VolumeLimitSummary(
    val type: AudioStream.Type,
    val min: Float?,
    val max: Float?,
)

/** The bounds this device carries, in stream order; streams without a bound have nothing to show. */
fun ManagedDevice.volumeLimitSummaries(): List<VolumeLimitSummary> = AudioStream.Type.entries
    .filter { getVolume(it) != null }
    .mapNotNull { type ->
        val min = normalizeVolumeLimitMin(getVolumeMin(type))
        val max = normalizeVolumeLimitMax(getVolumeMax(type))
        if (min == null && max == null) null else VolumeLimitSummary(type, min, max)
    }

// Tapping the card opens the volume limit screen, the switch only flips whether the bounds are
// applied, so the summary is what tells the user what they are without opening the screen.
@Composable
fun VolumeLimitCard(
    isEnabled: Boolean,
    isProVersion: Boolean,
    summaries: List<VolumeLimitSummary>,
    onCardClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onCardClick,
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(
                    title = stringResource(R.string.devices_device_config_volume_limit_label),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                if (!isProVersion) UpgradeBadge()

                Spacer(modifier = Modifier.weight(1f))

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() },
                )
            }

            Text(
                text = stringResource(R.string.devices_device_config_volume_limit_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            summaries.forEach { summary ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = summary.type.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    VolumeBandBar(
                        min = summary.min,
                        max = summary.max,
                        isEnabled = isEnabled,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = getVolumeLimitDescription(summary.min, summary.max),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * A read-only glance at one stream's band: the volumes it may reach, filled in on the range it
 * could otherwise use.
 *
 * The bar has its own width instead of filling the row, so it reads as one object next to the text
 * that spells the bounds out.
 */
@Composable
private fun VolumeBandBar(
    min: Float?,
    max: Float?,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackColor = when {
        isEnabled -> MaterialTheme.colorScheme.primary.copy(alpha = TRACK_ALPHA_ENABLED)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TRACK_ALPHA_DISABLED)
    }
    val fillColor = when {
        isEnabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = stringResource(R.string.devices_volume_limit_band_bar_desc)

    val start = (min ?: 0f).coerceIn(0f, 1f)
    val end = (max ?: 1f).coerceIn(start, 1f)

    Canvas(
        modifier = modifier
            .width(BAR_WIDTH)
            .height(BAR_HEIGHT)
            .semantics { contentDescription = description },
    ) {
        val radius = size.height / 2f
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = CornerRadius(radius, radius),
        )

        // Quiet is at the reading start of the row, the same way the slider on the limit screen
        // puts it, so both mirror together in a right-to-left layout.
        val (from, to) = when (layoutDirection) {
            LayoutDirection.Rtl -> 1f - end to 1f - start
            else -> start to end
        }
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(from * size.width, 0f),
            size = Size((to - from) * size.width, size.height),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

private val BAR_WIDTH = 72.dp
private val BAR_HEIGHT = 8.dp
private const val TRACK_ALPHA_ENABLED = 0.1f
private const val TRACK_ALPHA_DISABLED = 0.08f

@Preview2
@Composable
private fun VolumeLimitCardPreview() {
    PreviewWrapper {
        Column {
            VolumeLimitCard(
                isEnabled = true,
                isProVersion = true,
                summaries = listOf(
                    VolumeLimitSummary(AudioStream.Type.MUSIC, min = 0.2f, max = 0.7f),
                    VolumeLimitSummary(AudioStream.Type.CALL, min = 0.3f, max = null),
                ),
                onCardClick = {},
                onToggle = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            VolumeLimitCard(
                isEnabled = false,
                isProVersion = true,
                summaries = listOf(
                    VolumeLimitSummary(AudioStream.Type.MUSIC, min = null, max = 0.5f),
                ),
                onCardClick = {},
                onToggle = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            // Nothing bounded yet, so the description is all the card has to say.
            VolumeLimitCard(
                isEnabled = false,
                isProVersion = false,
                summaries = emptyList(),
                onCardClick = {},
                onToggle = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
