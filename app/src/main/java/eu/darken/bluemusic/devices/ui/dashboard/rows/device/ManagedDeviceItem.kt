package eu.darken.bluemusic.devices.ui.dashboard.rows.device

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.bluetooth.core.toIcon
import eu.darken.bluemusic.common.apps.AppInfo
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.ui.dashboard.DashboardAction
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeMode
import eu.darken.bluemusic.monitor.core.audio.VolumeMode.Companion.fromFloat
import java.text.DateFormat
import java.time.Instant
import java.util.Date

@Composable
fun ManagedDeviceItem(
    modifier: Modifier = Modifier,
    device: ManagedDevice,
    launchApps: List<AppInfo> = emptyList(),
    onDeviceAction: (DashboardAction) -> Unit,
    onNavigateToConfig: () -> Unit,
    isOnlyDevice: Boolean = false
) {
    var expanded by remember { mutableStateOf(device.isActive || isOnlyDevice) }

    // Update expanded state when device active state changes
    LaunchedEffect(device.isActive, isOnlyDevice) {
        expanded = device.isActive || isOnlyDevice
        // Collapse when device becomes inactive (unless it's the only device)
    }

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
                // Device type icon
                Icon(
                    imageVector = device.device.deviceType.toIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = device.label,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!device.isEnabled) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.TwoTone.Block,
                                contentDescription = stringResource(R.string.devices_device_disabled_label),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    when {
                        !device.isEnabled -> {
                            Text(
                                text = stringResource(R.string.devices_device_disabled_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        device.isActive -> {
                            Text(
                                text = stringResource(R.string.devices_currently_connected_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        device.lastConnected != Instant.EPOCH -> {
                            Text(
                                text = stringResource(
                                    R.string.devices_last_connected_label,
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                        .format(Date(device.lastConnected.toEpochMilli()))
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Expand/collapse indicator
                val rotationAngle by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "expand_icon_rotation"
                )
                Icon(
                    imageVector = Icons.TwoTone.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Volume controls when expanded
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))

                // Option indicators
                OptionIndicators(
                    device = device,
                    launchApps = launchApps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                val hasAnyVolumes = AudioStream.Type.entries.any { device.getVolume(it) != null }

                if (!hasAnyVolumes) {
                    Text(
                        text = stringResource(R.string.devices_no_volumes_configured),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }

                AudioStream.Type.entries.forEach { streamType ->
                    device.getVolume(streamType)?.let { currentVolume ->
                        val label = when (streamType) {
                            AudioStream.Type.MUSIC -> stringResource(R.string.devices_stream_music_label)
                            AudioStream.Type.CALL -> stringResource(R.string.devices_audio_stream_call_label)
                            AudioStream.Type.RINGTONE -> stringResource(R.string.devices_audio_stream_ring_label)
                            AudioStream.Type.NOTIFICATION -> stringResource(R.string.devices_audio_stream_notification_label)
                            AudioStream.Type.ALARM -> stringResource(R.string.devices_audio_stream_alarm_label)
                        }

                        val volumeMode = fromFloat(currentVolume)

                        // Use VolumeControlWithModes only for streams that support sound modes
                        if (streamType == AudioStream.Type.RINGTONE || streamType == AudioStream.Type.NOTIFICATION) {
                            VolumeControlWithModes(
                                streamType = streamType,
                                label = label,
                                volumeMode = volumeMode,
                                onVolumeChange = { newMode ->
                                    onDeviceAction(DashboardAction.AdjustVolume(device.address, streamType, newMode))
                                }
                            )
                        } else {
                            VolumeControl(
                                streamType = streamType,
                                label = label,
                                volume = currentVolume,
                                onVolumeChange = { newVolume ->
                                    onDeviceAction(
                                        DashboardAction.AdjustVolume(
                                            device.address,
                                            streamType,
                                            VolumeMode.Normal(newVolume)
                                        )
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Configure button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onNavigateToConfig
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.general_configure_action))
                    }
                }
            }
        }
    }
}


@Preview2
@Composable
private fun ManagedDeviceItemExpandedPreview() {
    PreviewWrapper {
        ManagedDeviceItem(
            device = MockDevice().toManagedDevice(isConnected = true),
            onDeviceAction = {},
            onNavigateToConfig = {},
        )
    }
}

@Preview2
@Composable
private fun ManagedDeviceItemPreview() {
    PreviewWrapper {
        ManagedDeviceItem(
            device = MockDevice().toManagedDevice(),
            onDeviceAction = {},
            onNavigateToConfig = {},
        )
    }
}

@Preview2
@Composable
private fun ManagedDeviceItemDisabledPreview() {
    PreviewWrapper {
        ManagedDeviceItem(
            device = MockDevice().toManagedDevice(isEnabled = false),
            onDeviceAction = {},
            onNavigateToConfig = {},
        )
    }
}