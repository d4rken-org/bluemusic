package eu.darken.bluemusic.devices.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Launch
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Devices
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Update
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.bluetooth.core.toIcon
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.ui.waitForState
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DevicesSettings
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.ui.icon
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.util.Date


@Composable
fun DeviceConfigScreenHost(
    addr: DeviceAddr,
    vm: DeviceConfigViewModel = hiltViewModel(
        key = addr,
        creationCallback = { factory: DeviceConfigViewModel.Factory -> factory.create(deviceAddress = addr) }
    ),
) {
    val state by waitForState(vm.state)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var showMonitoringDurationDialog by remember { mutableStateOf<Long?>(null) }
    var showReactionDelayDialog by remember { mutableStateOf<Long?>(null) }
    var showAdjustmentDelayDialog by remember { mutableStateOf<Long?>(null) }
    var showAppPickerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                is ConfigEvent.ShowDeleteDialog -> showDeleteDialog = true
                is ConfigEvent.ShowPurchaseSnackbar -> {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Premium feature - upgrade required")
                    }
                }

                is ConfigEvent.ShowAppPickerDialog -> showAppPickerDialog = true
                is ConfigEvent.ShowRenameDialog -> showRenameDialog = event.currentName
                is ConfigEvent.ShowMonitoringDurationDialog -> showMonitoringDurationDialog = event.currentValue
                is ConfigEvent.ShowReactionDelayDialog -> showReactionDelayDialog = event.currentValue
                is ConfigEvent.ShowAdjustmentDelayDialog -> showAdjustmentDelayDialog = event.currentValue
                is ConfigEvent.NavigateBack -> vm.navUp()
            }
        }
    }

    state?.let { state ->
        DeviceConfigScreen(
            state = state,
            onAction = { vm.handleAction(it) },
            onNavigateBack = { vm.navUp() },
            snackbarHostState = snackbarHostState
        )

        // Dialogs
        showMonitoringDurationDialog?.let { duration ->
            TimingDialog(
                title = stringResource(R.string.devices_device_config_monitoring_duration_label),
                message = stringResource(R.string.devices_device_config_monitoring_duration_desc),
                currentValue = duration,
                onConfirm = { vm.handleAction(ConfigAction.OnEditMonitoringDuration(it)) },
                onReset = { vm.handleAction(ConfigAction.OnEditMonitoringDuration(-1)) },
                onDismiss = {
                    showMonitoringDurationDialog = null
                }
            )
        }

        showReactionDelayDialog?.let { delay ->
            TimingDialog(
                title = stringResource(R.string.devices_device_config_reaction_delay_label),
                message = stringResource(R.string.devices_device_config_reaction_delay_desc),
                currentValue = delay,
                onConfirm = { vm.handleAction(ConfigAction.OnEditReactionDelay(it)) },
                onReset = { vm.handleAction(ConfigAction.OnEditReactionDelay(-1)) },
                onDismiss = {
                    showReactionDelayDialog = null
                }
            )
        }

        showAdjustmentDelayDialog?.let { delay ->
            TimingDialog(
                title = stringResource(R.string.devices_device_config_adjustment_delay_label),
                message = stringResource(R.string.devices_device_config_adjustment_delay_desc),
                currentValue = delay,
                onConfirm = { vm.handleAction(ConfigAction.OnEditAdjustmentDelay(it)) },
                onReset = { vm.handleAction(ConfigAction.OnEditAdjustmentDelay(-1)) },
                onDismiss = {
                    showAdjustmentDelayDialog = null
                }
            )
        }

        showRenameDialog?.let { currentName ->
            RenameDialog(
                currentName = currentName,
                onConfirm = { vm.handleAction(ConfigAction.OnRename(it)) },
                onDismiss = {
                    showRenameDialog = null
                }
            )
        }

        if (showDeleteDialog) {
            DeleteDeviceDialog(
                deviceName = state.device.label,
                onConfirm = { vm.handleAction(ConfigAction.OnConfirmDelete(true)) },
                onDismiss = {
                    showDeleteDialog = false
                }
            )
        }

        // TODO: App picker implementation for showAppPickerDialog
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConfigScreen(
    state: DeviceConfigViewModel.State,
    onAction: (ConfigAction) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val device = state.device
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.devices_device_config_label),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = device.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Device Header Card
            item {
                DeviceHeaderCard(
                    device = device,
                    onRenameClick = { onAction(ConfigAction.OnRenameClicked) },
                    onDeleteClick = { onAction(ConfigAction.DeleteDevice()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            // Volume Controls Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        SectionHeader(
                            title = stringResource(R.string.devices_device_config_section_volume_label),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_music_volume_label),
                            description = stringResource(R.string.devices_device_config_music_volume_desc),
                            isChecked = device.getVolume(AudioStream.Type.MUSIC) != null,
                            icon = AudioStream.Type.MUSIC.icon,
                            onCheckedChange = { onAction(ConfigAction.OnToggleVolume(AudioStream.Type.MUSIC)) }
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_call_volume_label),
                            description = stringResource(R.string.devices_device_config_call_volume_desc),
                            isChecked = device.getVolume(AudioStream.Type.CALL) != null,
                            icon = AudioStream.Type.CALL.icon,
                            onCheckedChange = { onAction(ConfigAction.OnToggleVolume(AudioStream.Type.CALL)) }
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_ring_volume_label),
                            description = stringResource(R.string.devices_device_config_ring_volume_desc),
                            isChecked = device.getVolume(AudioStream.Type.RINGTONE) != null,
                            icon = AudioStream.Type.RINGTONE.icon,
                            onCheckedChange = { onAction(ConfigAction.OnToggleVolume(AudioStream.Type.RINGTONE)) }
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_notification_volume_label),
                            description = stringResource(R.string.devices_device_config_notification_volume_desc),
                            isChecked = device.getVolume(AudioStream.Type.NOTIFICATION) != null,
                            icon = AudioStream.Type.NOTIFICATION.icon,
                            onCheckedChange = { onAction(ConfigAction.OnToggleVolume(AudioStream.Type.NOTIFICATION)) }
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_alarm_volume_label),
                            description = stringResource(R.string.devices_device_config_alarm_volume_desc),
                            isChecked = device.getVolume(AudioStream.Type.ALARM) != null,
                            icon = AudioStream.Type.ALARM.icon,
                            onCheckedChange = { onAction(ConfigAction.OnToggleVolume(AudioStream.Type.ALARM)) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Features Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        SectionHeader(
                            title = stringResource(R.string.devices_device_config_section_reaction_label),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_autoplay_label),
                            description = stringResource(R.string.devices_device_config_autoplay_desc),
                            isChecked = device.autoplay,
                            icon = Icons.TwoTone.PlayArrow,
                            onCheckedChange = { onAction(ConfigAction.OnToggleAutoPlay) }
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_volume_lock_label),
                            description = stringResource(R.string.devices_device_config_volume_lock_desc),
                            isChecked = device.volumeLock,
                            icon = Icons.TwoTone.Lock,
                            onCheckedChange = { onAction(ConfigAction.OnToggleVolumeLock) }
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_volume_observe_label),
                            description = stringResource(R.string.devices_device_config_volume_observe_desc),
                            isChecked = device.volumeObserving,
                            icon = Icons.TwoTone.Visibility,
                            onCheckedChange = { onAction(ConfigAction.OnToggleVolumeObserving) }
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_keep_awake_label),
                            description = stringResource(R.string.devices_device_config_keep_awake_desc),
                            isChecked = device.keepAwake,
                            icon = Icons.TwoTone.BatteryFull,
                            onCheckedChange = { onAction(ConfigAction.OnToggleKeepAwake) }
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_nudge_volume_label),
                            description = stringResource(R.string.devices_device_config_nudge_volume_description),
                            isChecked = device.nudgeVolume,
                            icon = Icons.TwoTone.GraphicEq,
                            onCheckedChange = { onAction(ConfigAction.OnToggleNudgeVolume) }
                        )

                        ClickablePreference(
                            title = stringResource(R.string.devices_device_config_launch_app_label),
                            description = state.launchAppLabel ?: stringResource(R.string.devices_device_config_launch_app_desc),
                            icon = Icons.AutoMirrored.TwoTone.Launch,
                            onClick = { onAction(ConfigAction.OnLaunchAppClicked) },
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Timing Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        SectionHeader(
                            title = stringResource(R.string.devices_device_config_section_timing_label),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )

                        ClickablePreference(
                            title = stringResource(R.string.devices_device_config_reaction_delay_label),
                            description = "${device.actionDelay ?: DevicesSettings.DEFAULT_REACTION_DELAY} ms",
                            icon = Icons.TwoTone.Timer,
                            onClick = { onAction(ConfigAction.OnEditReactionDelayClicked) }
                        )

                        ClickablePreference(
                            title = stringResource(R.string.devices_device_config_adjustment_delay_label),
                            description = "${device.adjustmentDelay ?: DevicesSettings.DEFAULT_ADJUSTMENT_DELAY} ms",
                            icon = Icons.TwoTone.Tune,
                            onClick = { onAction(ConfigAction.OnEditAdjustmentDelayClicked) }
                        )

                        ClickablePreference(
                            title = stringResource(R.string.devices_device_config_monitoring_duration_label),
                            description = "${device.monitoringDuration ?: DevicesSettings.DEFAULT_MONITORING_DURATION} ms",
                            icon = Icons.TwoTone.Update,
                            onClick = { onAction(ConfigAction.OnEditMonitoringDurationClicked) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

        }
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
private fun DeviceHeaderCard(
    device: ManagedDevice,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device icon
                Icon(
                    imageVector = device.device?.deviceType?.toIcon() ?: Icons.TwoTone.Devices,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Device info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Connection status
                    Spacer(modifier = Modifier.height(4.dp))
                    when {
                        device.isActive -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.devices_currently_connected_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        device.lastConnected != Instant.EPOCH -> {
                            Text(
                                text = stringResource(
                                    R.string.devices_last_connected_label,
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                        .format(Date(device.lastConnected.toEpochMilli()))
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Action buttons
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRenameClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.devices_device_config_rename_device_action),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                FilledTonalButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.devices_device_config_remove_device_action),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}



@Composable
private fun SwitchPreference(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading icon
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        // Title and description
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Switch
        Switch(
            checked = isChecked,
            onCheckedChange = null, // Disable direct switch interaction
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun ClickablePreference(
    title: String,
    description: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    textColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )

        // Title and description
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor ?: MaterialTheme.colorScheme.onSurface
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
        icon = { Icon(Icons.TwoTone.Refresh, contentDescription = null) },
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
        title = { Text(stringResource(R.string.devices_device_config_rename_device_action)) },
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
        icon = { Icon(Icons.TwoTone.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.devices_device_config_remove_device_label)) },
        text = { Text("Remove $deviceName from managed devices?") },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(
                    text = stringResource(R.string.devices_device_config_remove_device_action),
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

@Preview
@Composable
private fun ConfigScreenPreview() {
    PreviewWrapper {
        DeviceConfigScreen(
            state = DeviceConfigViewModel.State(
                device = MockDevice().toManagedDevice(),
                isProVersion = true,
                isLoading = false
            ),
            onAction = {},
            onNavigateBack = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}