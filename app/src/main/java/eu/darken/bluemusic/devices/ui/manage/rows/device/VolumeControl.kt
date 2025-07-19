package eu.darken.bluemusic.devices.ui.manage.rows.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Alarm
import androidx.compose.material.icons.twotone.MusicNote
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.Phone
import androidx.compose.material.icons.twotone.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import eu.darken.bluemusic.monitor.core.audio.AudioStream

@Composable
fun VolumeControl(
    streamType: AudioStream.Type,
    label: String,
    volume: Float?,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track the slider value locally while dragging
    var sliderValue by remember(volume) { mutableStateOf(volume ?: 0.5f) }

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
            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    sliderValue = newValue
                },
                onValueChangeFinished = {
                    // Only update when the user releases the slider
                    onVolumeChange(sliderValue)
                },
                modifier = Modifier.weight(1f),
                enabled = volume != null
            )
            Text(
                text = if (volume != null) "${(sliderValue * 100).toInt()}%" else "-",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .width(40.dp)
                    .padding(start = 8.dp)
            )
        }
    }
}

// Extension function to get the appropriate TwoTone icon for each audio stream type
fun AudioStream.Type.getIcon(): ImageVector = when (this) {
    AudioStream.Type.MUSIC -> Icons.TwoTone.MusicNote
    AudioStream.Type.CALL -> Icons.TwoTone.PhoneInTalk
    AudioStream.Type.RINGTONE -> Icons.TwoTone.Phone
    AudioStream.Type.NOTIFICATION -> Icons.TwoTone.Notifications
    AudioStream.Type.ALARM -> Icons.TwoTone.Alarm
}

@Preview2
@Composable
private fun VolumeControlPreview() {
    PreviewWrapper {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Volume Controls Preview",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            VolumeControl(
                streamType = AudioStream.Type.MUSIC,
                label = "Music",
                volume = 0.75f,
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )

            VolumeControl(
                streamType = AudioStream.Type.CALL,
                label = "Call",
                volume = 0.5f,
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )

            VolumeControl(
                streamType = AudioStream.Type.RINGTONE,
                label = "Ringtone",
                volume = 0.9f,
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )

            VolumeControl(
                streamType = AudioStream.Type.NOTIFICATION,
                label = "Notification",
                volume = 0.3f,
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )

            VolumeControl(
                streamType = AudioStream.Type.ALARM,
                label = "Alarm",
                volume = null, // Disabled state
                onVolumeChange = {},
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}