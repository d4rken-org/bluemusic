package eu.darken.bluemusic.devices.ui.config

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Launch
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.twotone.DoNotDisturb
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.ui.waitForState
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.ui.config.components.ClickablePreference
import eu.darken.bluemusic.devices.ui.config.components.DeviceHeaderCard
import eu.darken.bluemusic.devices.ui.config.components.DeviceStatusCard
import eu.darken.bluemusic.devices.ui.config.components.SectionHeader
import eu.darken.bluemusic.devices.ui.config.components.SwitchPreference
import eu.darken.bluemusic.devices.ui.config.dialogs.DeleteDeviceDialog
import eu.darken.bluemusic.devices.ui.config.dialogs.DndModeDialog
import eu.darken.bluemusic.devices.ui.config.dialogs.RenameDialog
import eu.darken.bluemusic.devices.ui.config.dialogs.TimingDialog
import eu.darken.bluemusic.devices.ui.icon
import eu.darken.bluemusic.devices.ui.settings.dialogs.AutoplayKeycodesDialog
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.DndMode
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
    val context = LocalContext.current

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var showMonitoringDurationDialog by remember { mutableStateOf<Duration?>(null) }
    var showReactionDelayDialog by remember { mutableStateOf<Duration?>(null) }
    var showAdjustmentDelayDialog by remember { mutableStateOf<Duration?>(null) }
    var showVolumeRateLimitIncreaseDialog by remember { mutableStateOf<Duration?>(null) }
    var showVolumeRateLimitDecreaseDialog by remember { mutableStateOf<Duration?>(null) }
    var showAutoplayKeycodesDialog by remember { mutableStateOf(false) }
    var showDndModeDialog by remember { mutableStateOf(false) }
    var dndModeValue by remember { mutableStateOf<DndMode?>(null) }

    val upgradeMessage = stringResource(R.string.upgrade_feature_requires_pro)
    val upgradeAction = stringResource(R.string.upgrade_prompt_upgrade_action)
    val notificationPolicyAction = stringResource(R.string.devices_device_config_notification_policy_action)
    val notificationPolicyRingtoneMessage = stringResource(R.string.devices_device_config_notification_policy_required_ringtone)
    val notificationPolicyNotificationMessage = stringResource(R.string.devices_device_config_notification_policy_required_notification)
    val notificationPolicyDndMessage = stringResource(R.string.devices_device_config_notification_policy_required_dnd)

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                is ConfigEvent.ShowDeleteDialog -> showDeleteDialog = true
                is ConfigEvent.ShowRenameDialog -> showRenameDialog = event.currentName
                is ConfigEvent.ShowMonitoringDurationDialog -> showMonitoringDurationDialog = event.currentValue
                is ConfigEvent.ShowReactionDelayDialog -> showReactionDelayDialog = event.currentValue
                is ConfigEvent.ShowAdjustmentDelayDialog -> showAdjustmentDelayDialog = event.currentValue
                is ConfigEvent.ShowVolumeRateLimitIncreaseDialog -> showVolumeRateLimitIncreaseDialog = event.currentValue
                is ConfigEvent.ShowVolumeRateLimitDecreaseDialog -> showVolumeRateLimitDecreaseDialog = event.currentValue
                is ConfigEvent.ShowAutoplayKeycodesDialog -> showAutoplayKeycodesDialog = true
                is ConfigEvent.ShowDndModeDialog -> {
                    dndModeValue = event.currentMode
                    showDndModeDialog = true
                }
                is ConfigEvent.NavigateBack -> vm.navUp()
                is ConfigEvent.RequiresPro -> {
                    val result = snackbarHostState.showSnackbar(
                        message = upgradeMessage,
                        actionLabel = upgradeAction,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.navTo(Nav.Main.Upgrade)
                    }
                }
                is ConfigEvent.RequiresNotificationPolicyAccess -> {
                    val message = when (event.feature) {
                        ConfigEvent.RequiresNotificationPolicyAccess.Feature.RINGTONE ->
                            notificationPolicyRingtoneMessage

                        ConfigEvent.RequiresNotificationPolicyAccess.Feature.NOTIFICATION ->
                            notificationPolicyNotificationMessage

                        ConfigEvent.RequiresNotificationPolicyAccess.Feature.DND ->
                            notificationPolicyDndMessage
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = notificationPolicyAction,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        context.startActivity(event.intent)
                    }
                }
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

        showVolumeRateLimitIncreaseDialog?.let { delay ->
            TimingDialog(
                title = stringResource(R.string.devices_device_config_volume_rate_limit_increase_duration_label),
                message = stringResource(R.string.devices_device_config_volume_rate_limit_increase_duration_desc, delay.toMillis()),
                currentValue = delay,
                onConfirm = { vm.handleAction(ConfigAction.OnEditVolumeRateLimitIncrease(it)) },
                onReset = { vm.handleAction(ConfigAction.OnEditVolumeRateLimitIncrease(null)) },
                onDismiss = {
                    showVolumeRateLimitIncreaseDialog = null
                }
            )
        }

        showVolumeRateLimitDecreaseDialog?.let { delay ->
            TimingDialog(
                title = stringResource(R.string.devices_device_config_volume_rate_limit_decrease_duration_label),
                message = stringResource(R.string.devices_device_config_volume_rate_limit_decrease_duration_desc, delay.toMillis()),
                currentValue = delay,
                onConfirm = { vm.handleAction(ConfigAction.OnEditVolumeRateLimitDecrease(it)) },
                onReset = { vm.handleAction(ConfigAction.OnEditVolumeRateLimitDecrease(null)) },
                onDismiss = {
                    showVolumeRateLimitDecreaseDialog = null
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

        if (showAutoplayKeycodesDialog) {
            AutoplayKeycodesDialog(
                currentKeycodes = state.device.autoplayKeycodes,
                onConfirm = { keycodes ->
                    vm.handleAction(ConfigAction.OnEditAutoplayKeycodes(keycodes))
                },
                onDismiss = {
                    showAutoplayKeycodesDialog = false
                }
            )
        }

        if (showDndModeDialog) {
            DndModeDialog(
                currentMode = dndModeValue,
                onConfirm = { mode ->
                    vm.handleAction(ConfigAction.OnEditDndMode(mode))
                    showDndModeDialog = false
                },
                onDismiss = {
                    showDndModeDialog = false
                }
            )
        }
    }
}

@Composable
private fun getDndModeDescription(mode: DndMode?): String {
    return when (mode) {
        null -> stringResource(R.string.dnd_mode_dont_change)
        DndMode.OFF -> stringResource(R.string.dnd_mode_off)
        DndMode.PRIORITY_ONLY -> stringResource(R.string.dnd_mode_priority_only)
        DndMode.ALARMS_ONLY -> stringResource(R.string.dnd_mode_alarms_only)
        DndMode.TOTAL_SILENCE -> stringResource(R.string.dnd_mode_total_silence)
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
                .padding(paddingValues)
                .horizontalCutoutPadding(),
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

            // Device Status Card
            item {
                DeviceStatusCard(
                    device = device,
                    onToggleEnabled = { onAction(ConfigAction.OnToggleEnabled) },
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
                            title = stringResource(R.string.devices_device_config_volume_lock_label),
                            description = stringResource(R.string.devices_device_config_volume_lock_desc),
                            isChecked = device.volumeLock,
                            icon = Icons.TwoTone.Lock,
                            onCheckedChange = { onAction(ConfigAction.OnToggleVolumeLock) },
                            requiresPro = true,
                            isProVersion = state.isProVersion
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
                            onCheckedChange = { onAction(ConfigAction.OnToggleVolumeRateLimiter) },
                            requiresPro = true,
                            isProVersion = state.isProVersion
                        )

                        if (device.volumeRateLimiter) {
                            ClickablePreference(
                                title = stringResource(R.string.devices_device_config_volume_rate_limit_increase_duration_label),
                                description = stringResource(
                                    R.string.devices_device_config_volume_rate_limit_increase_duration_desc,
                                    device.volumeRateLimitIncreaseMs
                                ),
                                icon = Icons.TwoTone.Schedule,
                                onClick = { onAction(ConfigAction.OnEditVolumeRateLimitIncreaseClicked) }
                            )

                            ClickablePreference(
                                title = stringResource(R.string.devices_device_config_volume_rate_limit_decrease_duration_label),
                                description = stringResource(
                                    R.string.devices_device_config_volume_rate_limit_decrease_duration_desc,
                                    device.volumeRateLimitDecreaseMs
                                ),
                                icon = Icons.TwoTone.Schedule,
                                onClick = { onAction(ConfigAction.OnEditVolumeRateLimitDecreaseClicked) }
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

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_visible_adjustments_label),
                            description = stringResource(R.string.devices_device_config_visible_adjustments_desc),
                            isChecked = device.visibleAdjustments,
                            icon = Icons.TwoTone.Visibility,
                            onCheckedChange = { onAction(ConfigAction.OnToggleVisibleAdjustments) }
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_autoplay_label),
                            description = stringResource(R.string.devices_device_config_autoplay_desc),
                            isChecked = device.autoplay,
                            icon = Icons.TwoTone.PlayArrow,
                            onCheckedChange = { onAction(ConfigAction.OnToggleAutoPlay) },
                            requiresPro = true,
                            isProVersion = state.isProVersion
                        )

                        if (device.autoplay) {
                            ClickablePreference(
                                title = stringResource(R.string.devices_device_config_autoplay_keycodes_label),
                                description = if (device.autoplayKeycodes.isEmpty()) {
                                    stringResource(R.string.devices_device_config_autoplay_keycodes_none_set)
                                } else {
                                    val keycodeNames = device.autoplayKeycodes.mapNotNull { keycode ->
                                        when (keycode) {
                                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> "Play"
                                            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Play/Pause"
                                            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> "Next"
                                            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Previous"
                                            android.view.KeyEvent.KEYCODE_MEDIA_STOP -> "Stop"
                                            android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> "Rewind"
                                            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "Fast Forward"
                                            else -> null
                                        }
                                    }
                                    keycodeNames.joinToString(", ")
                                },
                                icon = Icons.TwoTone.Tune,
                                onClick = { onAction(ConfigAction.OnEditAutoplayKeycodesClicked) },
                                requiresPro = true,
                                isProVersion = state.isProVersion
                            )
                        }

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
                            requiresPro = true,
                            isProVersion = state.isProVersion
                        )

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_show_home_screen_label),
                            description = stringResource(R.string.devices_device_config_show_home_screen_desc),
                            isChecked = device.showHomeScreen,
                            icon = Icons.TwoTone.Home,
                            onCheckedChange = { onAction(ConfigAction.OnToggleShowHomeScreen) },
                            requiresPro = true,
                            isProVersion = state.isProVersion
                        )

                        if (hasApiLevel(Build.VERSION_CODES.M)) {
                            ClickablePreference(
                                title = stringResource(R.string.devices_device_config_dnd_on_connect_label),
                                description = getDndModeDescription(device.dndMode),
                                icon = Icons.TwoTone.DoNotDisturb,
                                onClick = { onAction(ConfigAction.OnEditDndModeClicked) }
                            )
                        }

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