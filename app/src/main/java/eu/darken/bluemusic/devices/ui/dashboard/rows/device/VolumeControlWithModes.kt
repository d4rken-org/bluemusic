package eu.darken.bluemusic.devices.ui.dashboard.rows.device

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.AudioStream.SOUND_MODE_SILENT
import eu.darken.bluemusic.monitor.core.audio.AudioStream.SOUND_MODE_VIBRATE
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeMode.Companion.fromFloat
import kotlin.math.roundToInt

@Composable
fun VolumeControlWithModes(
    streamType: AudioStream.Type,
    label: String,
    volumeMode: VolumeMode?,
    onVolumeChange: (VolumeMode) -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current

    var sliderValue by remember(volumeMode) {
        mutableFloatStateOf(
            when (volumeMode) {
                null -> 0.5f
                is VolumeMode.Silent -> SOUND_MODE_SILENT
                is VolumeMode.Vibrate -> SOUND_MODE_VIBRATE
                is VolumeMode.Normal -> volumeMode.percentage
            }
        )
    }

    var showVolumeInput by remember { mutableStateOf(false) }

    Column(modifier = modifier.alpha(if (isLocked) 0.5f else 1f)) {
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

            Box(
                modifier = Modifier.width(88.dp),
                contentAlignment = Alignment.Center
            ) {
                if (volumeMode != null) {
                    Row {
                    Icon(
                        imageVector = Icons.AutoMirrored.TwoTone.VolumeOff,
                        contentDescription = "Silent",
                        modifier = Modifier
                            .size(24.dp)
                            .then(
                                if (!isLocked) {
                                    Modifier.clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        sliderValue = SOUND_MODE_SILENT
                                        onVolumeChange(VolumeMode.Silent)
                                    }
                                } else {
                                    Modifier
                                }
                            )
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
                            .then(
                                if (!isLocked) {
                                    Modifier.clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        sliderValue = SOUND_MODE_VIBRATE
                                        onVolumeChange(VolumeMode.Vibrate)
                                    }
                                } else {
                                    Modifier
                                }
                            )
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
                            .then(
                                if (!isLocked) {
                                    Modifier.clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        sliderValue = if (sliderValue < 0) 0.5f else sliderValue
                                        onVolumeChange(VolumeMode.Normal(sliderValue))
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(4.dp),
                        tint = if (sliderValue >= 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (sliderValue < 0) {
                    val currentMode = fromFloat(sliderValue)
                    Text(
                        text = when (currentMode) {
                            is VolumeMode.Silent -> "Silent"
                            is VolumeMode.Vibrate -> "Vibrate"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Slider(
                        value = if (sliderValue < 0) 0f else sliderValue,
                        onValueChange = { newValue ->
                            if (newValue > 0.01f) {
                                sliderValue = newValue
                            }
                        },
                        onValueChangeFinished = {
                            if (sliderValue > 0.01f) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onVolumeChange(VolumeMode.Normal(sliderValue))
                            }
                        },
                        enabled = volumeMode != null && !isLocked,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            val isNormal = volumeMode is VolumeMode.Normal
            val canTap = isNormal && !isLocked
            Text(
                text = when {
                    volumeMode == null -> "-"
                    volumeMode is VolumeMode.Silent -> "~"
                    volumeMode is VolumeMode.Vibrate -> "~"
                    sliderValue >= 0 -> "${(sliderValue * 100).roundToInt()}%"
                    else -> "-"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (canTap) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 40.dp)
                    .then(
                        if (canTap) {
                            Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .clickable { showVolumeInput = true }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }

    if (showVolumeInput && volumeMode is VolumeMode.Normal) {
        VolumeInputDialog(
            streamLabel = label,
            currentPercentage = (volumeMode.percentage * 100).roundToInt(),
            minValue = 1,
            onConfirm = { newVolume ->
                sliderValue = newVolume
                onVolumeChange(VolumeMode.Normal(newVolume))
            },
            onDismiss = { showVolumeInput = false },
        )
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
                volumeMode = VolumeMode.Normal(0.75f),
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )

            VolumeControlWithModes(
                streamType = AudioStream.Type.RINGTONE,
                label = "Ringtone",
                volumeMode = VolumeMode.Normal(0.5f),
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )

            VolumeControlWithModes(
                streamType = AudioStream.Type.RINGTONE,
                label = "Ring (Silent)",
                volumeMode = VolumeMode.Silent,
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )

            VolumeControlWithModes(
                streamType = AudioStream.Type.NOTIFICATION,
                label = "Notif (Vibrate)",
                volumeMode = VolumeMode.Vibrate,
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )

            VolumeControlWithModes(
                streamType = AudioStream.Type.ALARM,
                label = "Alarm",
                volumeMode = null,
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Text(
                text = "Locked State",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            VolumeControlWithModes(
                streamType = AudioStream.Type.RINGTONE,
                label = "Ringtone",
                volumeMode = VolumeMode.Normal(0.5f),
                onVolumeChange = {},
                isLocked = true,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
