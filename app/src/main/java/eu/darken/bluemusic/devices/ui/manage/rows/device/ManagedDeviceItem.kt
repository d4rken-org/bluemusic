package eu.darken.bluemusic.devices.ui.manage.rows.device

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Launch
import androidx.compose.material.icons.twotone.Alarm
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.twotone.Devices
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.MusicNote
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.Phone
import androidx.compose.material.icons.twotone.PhoneInTalk
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import eu.darken.bluemusic.devices.ui.manage.DevicesAction
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import java.text.DateFormat
import java.time.Instant
import java.util.Date

@Composable
fun ManagedDeviceItem(
    device: ManagedDevice,
    launchApps: List<AppInfo> = emptyList(),
    onDeviceAction: (DevicesAction) -> Unit,
    onNavigateToConfig: () -> Unit,
    modifier: Modifier = Modifier,
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
                    imageVector = device.device.deviceType?.toIcon() ?: Icons.TwoTone.Devices,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))

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
                    when {
                        device.isActive -> {
                            Text(
                                text = stringResource(R.string.devices_currently_connected_label),
                                style = MaterialTheme.typography.bodySmall,
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
                                style = MaterialTheme.typography.bodySmall,
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

                AudioStream.Type.entries.map { streamType ->
                    device.getVolume(streamType)?.let { currentVolume ->
                        VolumeControl(
                            streamType = streamType,
                            label = when (streamType) {
                                AudioStream.Type.MUSIC -> stringResource(R.string.devices_stream_music_label)
                                AudioStream.Type.CALL -> stringResource(R.string.devices_audio_stream_call_label)
                                AudioStream.Type.RINGTONE -> stringResource(R.string.devices_audio_stream_ring_label)
                                AudioStream.Type.NOTIFICATION -> stringResource(R.string.devices_audio_stream_notification_label)
                                AudioStream.Type.ALARM -> stringResource(R.string.devices_audio_stream_alarm_label)
                            },
                            volume = currentVolume,
                            onVolumeChange = { newVolume ->
                                onDeviceAction(DevicesAction.AdjustVolume(device.address, streamType, newVolume))
                            }
                        )
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

@Composable
private fun VolumeControl(
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
private fun AudioStream.Type.getIcon(): ImageVector = when (this) {
    AudioStream.Type.MUSIC -> Icons.TwoTone.MusicNote
    AudioStream.Type.CALL -> Icons.TwoTone.PhoneInTalk
    AudioStream.Type.RINGTONE -> Icons.TwoTone.Phone
    AudioStream.Type.NOTIFICATION -> Icons.TwoTone.Notifications
    AudioStream.Type.ALARM -> Icons.TwoTone.Alarm
}

@Composable
private fun OptionIndicators(
    device: ManagedDevice,
    launchApps: List<AppInfo> = emptyList(),
    modifier: Modifier = Modifier
) {
    val indicators = buildList {
        if (device.volumeLock) add(Icons.TwoTone.Lock to stringResource(R.string.devices_device_config_volume_lock_label))
        if (device.keepAwake) add(Icons.TwoTone.BatteryFull to stringResource(R.string.devices_device_config_keep_awake_label))
        if (device.nudgeVolume) add(Icons.TwoTone.GraphicEq to stringResource(R.string.devices_device_config_nudge_volume_label))
        if (device.volumeObserving) add(Icons.TwoTone.Visibility to stringResource(R.string.devices_device_config_volume_observe_label))
        if (device.volumeRateLimiter) add(Icons.TwoTone.Speed to stringResource(R.string.devices_device_config_volume_rate_limiter_label))
        if (device.autoplay) add(Icons.TwoTone.PlayArrow to "Autoplay")
    }

    val hasLaunchApps = launchApps.isNotEmpty()

    if (indicators.isNotEmpty() || hasLaunchApps) {
        FlowRow(
            modifier = modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Show app icons first
            if (hasLaunchApps) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.TwoTone.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    AppIconsRow(
                        appInfos = launchApps,
                        maxIcons = 3
                    )
                }
            }
            indicators.forEach { (icon, _) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = when (icon) {
                            Icons.TwoTone.Lock -> "Lock"
                            Icons.TwoTone.BatteryFull -> "Awake"
                            Icons.TwoTone.GraphicEq -> "Nudge"
                            Icons.TwoTone.Visibility -> "Observe"
                            Icons.TwoTone.Speed -> "Limit"
                            Icons.AutoMirrored.TwoTone.Launch -> "Launch"
                            Icons.TwoTone.PlayArrow -> "Auto"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
            device = MockDevice().toManagedDevice(isActive = true),
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