package eu.darken.bluemusic.devices.ui.manage

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.main.core.audio.AudioStream
import java.text.DateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagedDeviceItem(
    device: ManagedDevice,
    onEvent: (ManagedDevicesEvent) -> Unit,
    onNavigateToConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = device.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (device.lastConnected > 0) {
                        Text(
                            text = stringResource(
                                R.string.managed_devices_last_connected_label,
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(device.lastConnected))
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.general_delete_action),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // Volume controls when expanded
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Music volume
                VolumeControl(
                    label = stringResource(R.string.audio_stream_music_label),
                    volume = device.getVolume(AudioStream.Type.MUSIC),
                    onVolumeChange = { volume ->
                        onEvent(ManagedDevicesEvent.OnUpdateMusicVolume(device, volume))
                    }
                )
                
                // Call volume
                VolumeControl(
                    label = stringResource(R.string.audio_stream_call_label),
                    volume = device.getVolume(AudioStream.Type.CALL),
                    onVolumeChange = { volume ->
                        onEvent(ManagedDevicesEvent.OnUpdateCallVolume(device, volume))
                    }
                )
                
                // Ring volume
                VolumeControl(
                    label = stringResource(R.string.audio_stream_ring_label),
                    volume = device.getVolume(AudioStream.Type.RINGTONE),
                    onVolumeChange = { volume ->
                        onEvent(ManagedDevicesEvent.OnUpdateRingVolume(device, volume))
                    }
                )
                
                // Notification volume
                VolumeControl(
                    label = stringResource(R.string.audio_stream_notification_label),
                    volume = device.getVolume(AudioStream.Type.NOTIFICATION),
                    onVolumeChange = { volume ->
                        onEvent(ManagedDevicesEvent.OnUpdateNotificationVolume(device, volume))
                    }
                )
                
                // Alarm volume
                VolumeControl(
                    label = stringResource(R.string.audio_stream_alarm_label),
                    volume = device.getVolume(AudioStream.Type.ALARM),
                    onVolumeChange = { volume ->
                        onEvent(ManagedDevicesEvent.OnUpdateAlarmVolume(device, volume))
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Configure button
                Button(
                    onClick = onNavigateToConfig,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.general_configure_action))
                }
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.managed_devices_delete_dialog_title)) },
            text = { 
                Text(
                    stringResource(
                        R.string.managed_devices_delete_dialog_message,
                        device.label
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(ManagedDevicesEvent.OnDeleteDevice(device))
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.general_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.general_cancel_action))
                }
            }
        )
    }
}

@Composable
private fun VolumeControl(
    label: String,
    volume: Float?,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
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
                value = volume ?: 0.5f,
                onValueChange = onVolumeChange,
                modifier = Modifier.weight(1f),
                enabled = volume != null
            )
            Text(
                text = if (volume != null) "${(volume * 100).toInt()}%" else "-",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp)
            )
        }
    }
}

@Preview
@Composable
private fun ManagedDeviceItemPreview() {
    PreviewWrapper {
        ManagedDeviceItem(
            device = ManagedDevice(
                address = "00:11:22:33:44:55",
                lastConnected = System.currentTimeMillis(),
                musicVolume = 0.75f,
                callVolume = 0.5f,
                ringVolume = 0.8f,
                notificationVolume = 0.6f,
                alarmVolume = 0.9f
            ),
            onEvent = {},
            onNavigateToConfig = {}
        )
    }
}