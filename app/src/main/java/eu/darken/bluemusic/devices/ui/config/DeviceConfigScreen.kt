package eu.darken.bluemusic.devices.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Launch
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.PowerOff
import androidx.compose.material.icons.twotone.Schedule
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.Update
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.ui.waitForState
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.ui.config.components.ClickablePreference
import eu.darken.bluemusic.devices.ui.config.components.DeviceHeaderCard
import eu.darken.bluemusic.devices.ui.config.components.SectionHeader
import eu.darken.bluemusic.devices.ui.config.components.SwitchPreference
import eu.darken.bluemusic.devices.ui.config.dialogs.DeleteDeviceDialog
import eu.darken.bluemusic.devices.ui.config.dialogs.RenameDialog
import eu.darken.bluemusic.devices.ui.config.dialogs.TimingDialog
import eu.darken.bluemusic.devices.ui.icon
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import java.time.Duration


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
    rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var showMonitoringDurationDialog by remember { mutableStateOf<Duration?>(null) }
    var showReactionDelayDialog by remember { mutableStateOf<Duration?>(null) }
    var showAdjustmentDelayDialog by remember { mutableStateOf<Duration?>(null) }
    var showVolumeRateLimitDialog by remember { mutableStateOf<Duration?>(null) }

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                is ConfigEvent.ShowDeleteDialog -> showDeleteDialog = true
                is ConfigEvent.ShowRenameDialog -> showRenameDialog = event.currentName
                is ConfigEvent.ShowMonitoringDurationDialog -> showMonitoringDurationDialog = event.currentValue
                is ConfigEvent.ShowReactionDelayDialog -> showReactionDelayDialog = event.currentValue
                is ConfigEvent.ShowAdjustmentDelayDialog -> showAdjustmentDelayDialog = event.currentValue
                is ConfigEvent.ShowVolumeRateLimitDialog -> showVolumeRateLimitDialog = event.currentValue
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
                onReset = { vm.handleAction(ConfigAction.OnEditMonitoringDuration(null)) },
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
                onReset = { vm.handleAction(ConfigAction.OnEditReactionDelay(null)) },
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
                onReset = { vm.handleAction(ConfigAction.OnEditAdjustmentDelay(null)) },
                onDismiss = {
                    showAdjustmentDelayDialog = null
                }
            )
        }

        showVolumeRateLimitDialog?.let { delay ->
            TimingDialog(
                title = stringResource(R.string.devices_device_config_volume_rate_limit_duration_label),
                message = stringResource(R.string.devices_device_config_volume_rate_limit_duration_desc, delay.toMillis()),
                currentValue = delay,
                onConfirm = { vm.handleAction(ConfigAction.OnEditVolumeRateLimit(it)) },
                onReset = { vm.handleAction(ConfigAction.OnEditVolumeRateLimit(null)) },
                onDismiss = {
                    showVolumeRateLimitDialog = null
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
    }
}

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
                            title = stringResource(R.string.devices_device_config_volume_save_on_disconnect_label),
                            description = stringResource(R.string.devices_device_config_volume_save_on_disconnect_desc),
                            isChecked = device.volumeSaveOnDisconnect,
                            icon = Icons.TwoTone.PowerOff,
                            onCheckedChange = { onAction(ConfigAction.OnToggleVolumeSaveOnDisconnect) }
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_volume_rate_limiter_label),
                            description = stringResource(R.string.devices_device_config_volume_rate_limiter_desc),
                            isChecked = device.volumeRateLimiter,
                            icon = Icons.TwoTone.Speed,
                            onCheckedChange = { onAction(ConfigAction.OnToggleVolumeRateLimiter) }
                        )

                        if (device.volumeRateLimiter) {
                            ClickablePreference(
                                title = stringResource(R.string.devices_device_config_volume_rate_limit_duration_label),
                                description = stringResource(
                                    R.string.devices_device_config_volume_rate_limit_duration_desc,
                                    device.volumeRateLimitMs
                                ),
                                icon = Icons.TwoTone.Schedule,
                                onClick = { onAction(ConfigAction.OnEditVolumeRateLimitClicked) }
                            )
                        }

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
                            description = when {
                                state.launchAppLabels.isEmpty() -> stringResource(R.string.devices_device_config_launch_app_desc)
                                state.launchAppLabels.size == 1 -> state.launchAppLabels.first()
                                else -> stringResource(
                                    R.string.devices_device_config_launch_app_multiple_desc,
                                    state.launchAppLabels.size
                                )
                            },
                            icon = Icons.AutoMirrored.TwoTone.Launch,
                            onClick = { onAction(ConfigAction.OnLaunchAppClicked) },
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_show_home_screen_label),
                            description = stringResource(R.string.devices_device_config_show_home_screen_desc),
                            isChecked = device.showHomeScreen,
                            icon = Icons.TwoTone.Home,
                            onCheckedChange = { onAction(ConfigAction.OnToggleShowHomeScreen) }
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
                            description = "${device.actionDelay.toMillis()} ms",
                            icon = Icons.TwoTone.Timer,
                            onClick = { onAction(ConfigAction.OnEditReactionDelayClicked) }
                        )

                        ClickablePreference(
                            title = stringResource(R.string.devices_device_config_adjustment_delay_label),
                            description = "${device.adjustmentDelay.toMillis()} ms",
                            icon = Icons.TwoTone.Tune,
                            onClick = { onAction(ConfigAction.OnEditAdjustmentDelayClicked) }
                        )

                        ClickablePreference(
                            title = stringResource(R.string.devices_device_config_monitoring_duration_label),
                            description = "${device.monitoringDuration.toMillis()} ms",
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