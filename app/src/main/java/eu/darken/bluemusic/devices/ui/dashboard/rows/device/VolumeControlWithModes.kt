package eu.darken.bluemusic.devices.ui.dashboard.rows.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.VolumeOff
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import kotlin.math.roundToInt

const val SOUND_MODE_SILENT = -2f
const val SOUND_MODE_VIBRATE = -3f

@Composable
fun VolumeControlWithModes(
    streamType: AudioStream.Type,
    label: String,
    volume: Float?,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val supportsSpecialModes = streamType == AudioStream.Type.RINGTONE || streamType == AudioStream.Type.NOTIFICATION
    val haptics = LocalHapticFeedback.current

    // Track the slider value locally while dragging
    var sliderValue by remember(volume) {
        mutableStateOf(
            when {
                volume == null -> 0.5f
                volume < 0 && supportsSpecialModes -> volume
                else -> volume.coerceIn(0f, 1f)
            }
        )
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = streamType.getIcon(),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(80.dp)
            )

            if (supportsSpecialModes && volume != null) {
                // Show mode icons for quick selection
                Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.TwoTone.VolumeOff,
                        contentDescription = "Silent",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                sliderValue = SOUND_MODE_SILENT
                                onVolumeChange(SOUND_MODE_SILENT)
                            }
                            .padding(4.dp),
                        tint = if (sliderValue == SOUND_MODE_SILENT)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Icon(
                        imageVector = Icons.TwoTone.Vibration,
                        contentDescription = "Vibrate",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                sliderValue = SOUND_MODE_VIBRATE
                                onVolumeChange(SOUND_MODE_VIBRATE)
                            }
                            .padding(4.dp),
                        tint = if (sliderValue == SOUND_MODE_VIBRATE)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Icon(
                        imageVector = Icons.TwoTone.PhoneAndroid,
                        contentDescription = "Normal",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                sliderValue = if (sliderValue < 0) 0.5f else sliderValue
                                onVolumeChange(sliderValue)
                            }
                            .padding(4.dp),
                        tint = if (sliderValue >= 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp), // Fixed height to maintain consistency
                contentAlignment = Alignment.Center
            ) {
                if (supportsSpecialModes && sliderValue < 0) {
                    // Show mode text instead of slider for special modes
                    Text(
                        text = when (sliderValue) {
                            SOUND_MODE_SILENT -> "Silent"
                            SOUND_MODE_VIBRATE -> "Vibrate"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    // Regular slider for volume
                    Slider(
                        value = if (supportsSpecialModes && sliderValue < 0) 0f else sliderValue,
                        onValueChange = { newValue ->
                            if (!supportsSpecialModes || newValue > 0.01f) {
                                sliderValue = newValue
                            }
                        },
                        onValueChangeFinished = {
                            // Only update when the user releases the slider
                            if (!supportsSpecialModes || sliderValue > 0.01f) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onVolumeChange(sliderValue)
                            }
                        },
                        enabled = volume != null,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Text(
                text = when {
                    volume == null -> "-"
                    supportsSpecialModes && volume == SOUND_MODE_SILENT -> "~"
                    supportsSpecialModes && volume == SOUND_MODE_VIBRATE -> "~"
                    volume < 0 -> "-"
                    else -> "${(volume * 100).roundToInt()}%"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .width(50.dp)
                    .padding(start = 8.dp)
            )
        }
    }
}

@Preview2
@Composable
private fun VolumeControlWithModesPreview() {
    PreviewWrapper {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Volume Controls with Sound Modes",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            VolumeControlWithModes(
                streamType = AudioStream.Type.MUSIC,
                label = "Music",
                volume = 0.75f,
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )

            VolumeControlWithModes(
                streamType = AudioStream.Type.RINGTONE,
                label = "Ringtone",
                volume = 0.5f,
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )

            VolumeControlWithModes(
                streamType = AudioStream.Type.RINGTONE,
                label = "Ring (Silent)",
                volume = SOUND_MODE_SILENT,
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )

            VolumeControlWithModes(
                streamType = AudioStream.Type.NOTIFICATION,
                label = "Notif (Vibrate)",
                volume = SOUND_MODE_VIBRATE,
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )

            VolumeControlWithModes(
                streamType = AudioStream.Type.ALARM,
                label = "Alarm",
                volume = null, // Disabled state
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}