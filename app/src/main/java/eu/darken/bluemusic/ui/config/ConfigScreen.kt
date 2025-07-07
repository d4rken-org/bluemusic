package eu.darken.bluemusic.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice
import eu.darken.bluemusic.common.ui.theme.BlueMusicTheme
import eu.darken.bluemusic.data.device.ManagedDevice
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.settings.core.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    state: ConfigState,
    onEvent: (ConfigEvent) -> Unit,
    onNavigateBack: () -> Unit
) {
    val device = state.device
    
    LaunchedEffect(state.shouldFinish) {
        if (state.shouldFinish) {
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = device?.label ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (device != null && device.label != device.name) {
                            Text(
                                text = device.name ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.abc_action_bar_up_description)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (device == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Volume Controls Section
                item {
                    SectionHeader(title = stringResource(R.string.label_volume))
                }
                
                item {
                    VolumePreference(
                        title = stringResource(R.string.label_music_volume),
                        description = stringResource(R.string.description_music_volume),
                        isChecked = device.getVolume(AudioStream.Type.MUSIC) != null,
                        onCheckedChange = { onEvent(ConfigEvent.OnToggleMusicVolume) }
                    )
                }
                
                item {
                    VolumePreference(
                        title = stringResource(R.string.label_call_volume),
                        description = stringResource(R.string.description_call_volume),
                        isChecked = device.getVolume(AudioStream.Type.CALL) != null,
                        onCheckedChange = { onEvent(ConfigEvent.OnToggleCallVolume) }
                    )
                }
                
                item {
                    VolumePreference(
                        title = stringResource(R.string.label_ring_volume),
                        description = stringResource(R.string.description_ring_volume),
                        isChecked = device.getVolume(AudioStream.Type.RINGTONE) != null,
                        isPro = state.isProVersion,
                        onCheckedChange = { onEvent(ConfigEvent.OnToggleRingVolume) }
                    )
                }
                
                item {
                    VolumePreference(
                        title = stringResource(R.string.label_notification_volume),
                        description = stringResource(R.string.description_notification_volume),
                        isChecked = device.getVolume(AudioStream.Type.NOTIFICATION) != null,
                        isPro = state.isProVersion,
                        onCheckedChange = { onEvent(ConfigEvent.OnToggleNotificationVolume) }
                    )
                }
                
                item {
                    VolumePreference(
                        title = stringResource(R.string.label_alarm_volume),
                        description = stringResource(R.string.description_alarm_volume),
                        isChecked = device.getVolume(AudioStream.Type.ALARM) != null,
                        onCheckedChange = { onEvent(ConfigEvent.OnToggleAlarmVolume) }
                    )
                }
                
                // Features Section
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionHeader(title = stringResource(R.string.label_other))
                }
                
                item {
                    SwitchPreference(
                        title = stringResource(R.string.label_autoplay),
                        description = stringResource(R.string.description_autoplay),
                        isChecked = device.autoplay,
                        isPro = state.isProVersion,
                        onCheckedChange = { onEvent(ConfigEvent.OnToggleAutoPlay) }
                    )
                }
                
                if (device.address != FakeSpeakerDevice.ADDR) {
                    item {
                        SwitchPreference(
                            title = stringResource(R.string.label_volume_lock),
                            description = stringResource(R.string.description_volume_lock),
                            isChecked = device.volumeLock,
                            isPro = state.isProVersion,
                            onCheckedChange = { onEvent(ConfigEvent.OnToggleVolumeLock) }
                        )
                    }
                    
                    item {
                        SwitchPreference(
                            title = stringResource(R.string.label_keep_awake),
                            description = stringResource(R.string.description_keep_awake),
                            isChecked = device.keepAwake,
                            isPro = state.isProVersion,
                            onCheckedChange = { onEvent(ConfigEvent.OnToggleKeepAwake) }
                        )
                    }
                }
                
                item {
                    SwitchPreference(
                        title = stringResource(R.string.nudge_volume_label),
                        description = stringResource(R.string.nudge_volume_description),
                        isChecked = device.nudgeVolume,
                        onCheckedChange = { onEvent(ConfigEvent.OnToggleNudgeVolume) }
                    )
                }
                
                item {
                    ClickablePreference(
                        title = stringResource(R.string.label_launch_app),
                        description = state.launchAppLabel ?: stringResource(R.string.description_launch_app),
                        isPro = state.isProVersion,
                        onClick = { onEvent(ConfigEvent.OnLaunchAppClicked) },
                        onLongClick = { onEvent(ConfigEvent.OnClearLaunchApp) }
                    )
                }
                
                // Timing Section
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionHeader(title = stringResource(R.string.label_settings))
                }
                
                item {
                    ClickablePreference(
                        title = stringResource(R.string.label_reaction_delay),
                        description = "${device.actionDelay ?: Settings.DEFAULT_REACTION_DELAY} ms",
                        onClick = { onEvent(ConfigEvent.OnEditReactionDelayClicked) }
                    )
                }
                
                item {
                    ClickablePreference(
                        title = stringResource(R.string.label_adjustment_delay),
                        description = "${device.adjustmentDelay ?: Settings.DEFAULT_ADJUSTMENT_DELAY} ms",
                        onClick = { onEvent(ConfigEvent.OnEditAdjustmentDelayClicked) }
                    )
                }
                
                item {
                    ClickablePreference(
                        title = stringResource(R.string.label_monitoring_duration),
                        description = "${device.monitoringDuration ?: Settings.DEFAULT_MONITORING_DURATION} ms",
                        onClick = { onEvent(ConfigEvent.OnEditMonitoringDurationClicked) }
                    )
                }
                
                // Device Management Section
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionHeader(title = stringResource(R.string.label_general))
                }
                
                if (device.address != FakeSpeakerDevice.ADDR) {
                    item {
                        ClickablePreference(
                            title = stringResource(R.string.label_rename),
                            description = stringResource(R.string.description_rename_device),
                            isPro = state.isProVersion,
                            onClick = { onEvent(ConfigEvent.OnRenameClicked) }
                        )
                    }
                }
                
                item {
                    ClickablePreference(
                        title = stringResource(R.string.action_remove_device),
                        description = null,
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = { onEvent(ConfigEvent.OnDeleteDevice) }
                    )
                }
            }
        }
    }
    
    // Dialogs
    if (state.showPurchaseDialog) {
        PurchaseDialog(
            onDismiss = { onEvent(ConfigEvent.OnDismissDialog) },
            onPurchase = { /* Handled by ScreenHost */ }
        )
    }
    
    state.showMonitoringDurationDialog?.let { duration ->
        TimingDialog(
            title = stringResource(R.string.label_monitoring_duration),
            message = stringResource(R.string.description_monitoring_duration),
            currentValue = duration,
            onConfirm = { onEvent(ConfigEvent.OnEditMonitoringDuration(it)) },
            onReset = { onEvent(ConfigEvent.OnEditMonitoringDuration(-1)) },
            onDismiss = { onEvent(ConfigEvent.OnDismissDialog) }
        )
    }
    
    state.showReactionDelayDialog?.let { delay ->
        TimingDialog(
            title = stringResource(R.string.label_reaction_delay),
            message = stringResource(R.string.description_reaction_delay),
            currentValue = delay,
            onConfirm = { onEvent(ConfigEvent.OnEditReactionDelay(it)) },
            onReset = { onEvent(ConfigEvent.OnEditReactionDelay(-1)) },
            onDismiss = { onEvent(ConfigEvent.OnDismissDialog) }
        )
    }
    
    state.showAdjustmentDelayDialog?.let { delay ->
        TimingDialog(
            title = stringResource(R.string.label_adjustment_delay),
            message = stringResource(R.string.description_adjustment_delay),
            currentValue = delay,
            onConfirm = { onEvent(ConfigEvent.OnEditAdjustmentDelay(it)) },
            onReset = { onEvent(ConfigEvent.OnEditAdjustmentDelay(-1)) },
            onDismiss = { onEvent(ConfigEvent.OnDismissDialog) }
        )
    }
    
    state.showRenameDialog?.let { currentName ->
        RenameDialog(
            currentName = currentName,
            onConfirm = { onEvent(ConfigEvent.OnRename(it)) },
            onDismiss = { onEvent(ConfigEvent.OnDismissDialog) }
        )
    }
    
    if (state.showDeleteDialog) {
        DeleteDeviceDialog(
            deviceName = device?.label ?: "",
            onConfirm = { onEvent(ConfigEvent.OnConfirmDelete(true)) },
            onDismiss = { onEvent(ConfigEvent.OnDismissDialog) }
        )
    }
    
    if (state.showAppPickerDialog) {
        // App picker will be handled by ScreenHost
        // This is just a placeholder
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun VolumePreference(
    title: String,
    description: String,
    isChecked: Boolean,
    isPro: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val fullDescription = if (isPro) {
        description
    } else {
        "$description\n[${stringResource(R.string.label_premium_version_required)}]"
    }
    
    SwitchPreference(
        title = title,
        description = fullDescription,
        isChecked = isChecked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
    )
}

@Composable
private fun SwitchPreference(
    title: String,
    description: String,
    isChecked: Boolean,
    isPro: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = modifier
    )
}

@Composable
private fun ClickablePreference(
    title: String,
    description: String?,
    isPro: Boolean = true,
    textColor: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val fullDescription = if (!isPro && description != null) {
        "[${stringResource(R.string.label_premium_version_required)}]"
    } else {
        description
    }
    
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = textColor ?: MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = fullDescription?.let { { Text(it) } },
        modifier = modifier.clickable { onClick() }
    )
}

@Composable
private fun TimingDialog(
    title: String,
    message: String,
    currentValue: Long,
    onConfirm: (Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(currentValue.toString()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    value.toLongOrNull()?.let { onConfirm(it) }
                }
            ) {
                Text(stringResource(R.string.action_set))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.action_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name)
                    }
                }
            ) {
                Text(stringResource(R.string.action_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DeleteDeviceDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.action_remove_device)) },
        text = { Text("Remove $deviceName from managed devices?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_remove_device),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun PurchaseDialog(
    onDismiss: () -> Unit,
    onPurchase: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Star, contentDescription = null) },
        title = { Text(stringResource(R.string.label_premium_version)) },
        text = { Text(stringResource(R.string.description_premium_required_this_extra_option)) },
        confirmButton = {
            TextButton(onClick = onPurchase) {
                Text(stringResource(R.string.action_upgrade))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Preview
@Composable
private fun ConfigScreenPreview() {
    BlueMusicTheme {
        ConfigScreen(
            state = ConfigState(
                device = ManagedDevice(
                    address = "00:11:22:33:44:55",
                    musicVolume = 0.75f,
                    callVolume = 0.5f,
                    autoplay = true,
                    volumeLock = false,
                    keepAwake = true,
                    nudgeVolume = false
                ),
                isProVersion = true,
                isLoading = false
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}