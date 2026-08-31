package eu.darken.bluemusic.devices.ui.config

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Launch
import androidx.compose.material.icons.automirrored.twotone.VolumeUp
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.twotone.DoNotDisturb
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Notifications
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.UpgradeBadge
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import eu.darken.bluemusic.common.compose.navigationBarBottomPadding
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.ui.config.components.ClickablePreference
import eu.darken.bluemusic.devices.ui.config.components.DeviceHeaderCard
import eu.darken.bluemusic.devices.ui.config.components.DeviceStatusCard
import eu.darken.bluemusic.devices.ui.config.components.SectionHeader
import eu.darken.bluemusic.devices.ui.config.components.SwitchPreference
import eu.darken.bluemusic.devices.ui.config.components.VolumeLimitCard
import eu.darken.bluemusic.devices.ui.config.components.volumeLimitSummaries
import eu.darken.bluemusic.devices.ui.config.dialogs.ConnectionAlertDialog
import eu.darken.bluemusic.devices.ui.config.dialogs.DeleteDeviceDialog
import eu.darken.bluemusic.devices.ui.config.dialogs.DndModeDialog
import eu.darken.bluemusic.devices.ui.config.dialogs.RenameDialog
import eu.darken.bluemusic.devices.ui.config.dialogs.TimingDialog
import eu.darken.bluemusic.devices.ui.AutoplayKeycodes
import eu.darken.bluemusic.devices.ui.icon
import eu.darken.bluemusic.devices.ui.settings.dialogs.AutoplayKeycodesDialog
import eu.darken.bluemusic.eq.core.EqCapabilities
import eu.darken.bluemusic.eq.core.levelsOf
import eu.darken.bluemusic.eq.ui.EqMiniGraph
import eu.darken.bluemusic.eq.ui.formatGain
import eu.darken.bluemusic.monitor.core.alert.AlertType
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
    val state by vm.state.collectAsStateWithLifecycle()
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
    var showConnectionAlertDialog by remember { mutableStateOf(false) }
    var connectionAlertTypeValue by remember { mutableStateOf(AlertType.NONE) }

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

                is ConfigEvent.ShowConnectionAlertDialog -> {
                    connectionAlertTypeValue = event.currentType
                    showConnectionAlertDialog = true
                }

                is ConfigEvent.NavigateBack -> vm.navUp()
                is ConfigEvent.RequiresPro -> vm.navTo(Nav.Main.Upgrade())

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

        if (showConnectionAlertDialog) {
            ConnectionAlertDialog(
                currentType = connectionAlertTypeValue,
                onConfirm = { type ->
                    vm.handleAction(ConfigAction.OnEditConnectionAlertType(type))
                    showConnectionAlertDialog = false
                },
                onDismiss = {
                    showConnectionAlertDialog = false
                }
            )
        }
    }
}

@Composable
private fun getDndModeDescription(mode: DndMode?): String {
    return when (mode) {
        null -> stringResource(R.string.dnd_mode_dont_change)
        // A stale OFF can't turn DND off on API 35+ (discussion #230); present it as "Don't change".
        DndMode.OFF -> if (DndMode.canTurnDndOff()) {
            stringResource(R.string.dnd_mode_off)
        } else {
            stringResource(R.string.dnd_mode_dont_change)
        }
        DndMode.PRIORITY_ONLY -> stringResource(R.string.dnd_mode_priority_only)
        DndMode.ALARMS_ONLY -> stringResource(R.string.dnd_mode_alarms_only)
        DndMode.TOTAL_SILENCE -> stringResource(R.string.dnd_mode_total_silence)
    }
}

@Composable
private fun getConnectionAlertDescription(type: AlertType): String {
    return when (type) {
        AlertType.NONE -> stringResource(R.string.connection_alert_type_none)
        AlertType.SOUND -> stringResource(R.string.connection_alert_type_sound)
        AlertType.VIBRATION -> stringResource(R.string.connection_alert_type_vibration)
        AlertType.BOTH -> stringResource(R.string.connection_alert_type_both)
    }
}

@Composable
fun DeviceConfigScreen(
    state: DeviceConfigViewModel.State,
    onAction: (ConfigAction) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState = rememberLazyListState(),
) {
    val device = state.device
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
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
        contentWindowInsets = WindowInsets.statusBars,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { paddingValues ->
        val navBarPadding = navigationBarBottomPadding()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .horizontalCutoutPadding(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp + navBarPadding)
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

            // Equalizer Section
            item {
                EqualizerCard(
                    isEnabled = device.eqEnabled,
                    isProVersion = state.isProVersion,
                    capabilities = state.eqCapabilities,
                    bandLevels = device.eqBandLevels,
                    boostGain = device.eqBoostGain,
                    onCardClick = { onAction(ConfigAction.OnEqClicked) },
                    onToggle = { onAction(ConfigAction.OnToggleEq) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Volume Limit Section
            item {
                VolumeLimitCard(
                    isEnabled = device.volumeLimit,
                    isProVersion = state.isProVersion,
                    summaries = device.volumeLimitSummaries(),
                    onCardClick = { onAction(ConfigAction.OnVolumeLimitClicked) },
                    onToggle = { onAction(ConfigAction.OnToggleVolumeLimit) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
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

                        AnimatedVisibility(visible = device.volumeObservingOverridden) {
                            FeatureOverriddenByVolumeLockCard()
                        }

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_volume_save_on_disconnect_label),
                            description = stringResource(R.string.devices_device_config_volume_save_on_disconnect_desc),
                            isChecked = device.volumeSaveOnDisconnect,
                            icon = Icons.TwoTone.PowerOff,
                            onCheckedChange = { onAction(ConfigAction.OnToggleVolumeSaveOnDisconnect) }
                        )

                        AnimatedVisibility(visible = device.volumeSaveOnDisconnect) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.devices_device_config_volume_save_on_disconnect_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }

                        SwitchPreference(
                            title = stringResource(R.string.devices_device_config_volume_rate_limiter_label),
                            description = stringResource(R.string.devices_device_config_volume_rate_limiter_desc),
                            isChecked = device.volumeRateLimiter,
                            icon = Icons.TwoTone.Speed,
                            onCheckedChange = { onAction(ConfigAction.OnToggleVolumeRateLimiter) },
                            requiresPro = true,
                            isProVersion = state.isProVersion
                        )

                        AnimatedVisibility(visible = device.volumeRateLimiterOverridden) {
                            FeatureOverriddenByVolumeLockCard()
                        }

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
                            val codes = device.autoplayKeycodes
                            ClickablePreference(
                                title = stringResource(R.string.devices_device_config_autoplay_keycodes_label),
                                description = when {
                                    codes.isEmpty() -> stringResource(R.string.devices_device_config_autoplay_keycodes_none_set)
                                    codes.size > 3 -> pluralStringResource(
                                        R.plurals.devices_indicator_auto_count,
                                        codes.size,
                                        codes.size,
                                    )
                                    else -> codes
                                        .map { AutoplayKeycodes.resolve(it).label() }
                                        .joinToString(", ")
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

                        ClickablePreference(
                            title = stringResource(R.string.devices_device_config_connection_alert_label),
                            description = getConnectionAlertDescription(device.connectionAlertType),
                            icon = Icons.TwoTone.Notifications,
                            onClick = { onAction(ConfigAction.OnEditConnectionAlertClicked) },
                            requiresPro = true,
                            isProVersion = state.isProVersion
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

// Tapping the card opens the equalizer screen, the switch only flips whether it is applied, so the
// caveat about app support is body text here instead of a line the user has to open the screen to read.
@Composable
private fun EqualizerCard(
    isEnabled: Boolean,
    isProVersion: Boolean,
    capabilities: EqCapabilities.Caps?,
    bandLevels: List<Int>?,
    boostGain: Int?,
    onCardClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onCardClick,
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(
                    title = stringResource(R.string.devices_device_config_equalizer_label),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                if (!isProVersion) UpgradeBadge()

                Spacer(modifier = Modifier.weight(1f))

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() },
                )
            }

            Text(
                text = stringResource(R.string.devices_device_config_equalizer_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Without an engine there is no curve to draw: the equalizer screen explains why.
            val boost = boostGain ?: 0
            if (capabilities != null || boost > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (capabilities != null) {
                        EqMiniGraph(
                            levels = capabilities.levelsOf(bandLevels),
                            minLevel = capabilities.minLevel,
                            maxLevel = capabilities.maxLevel,
                            isEnabled = isEnabled,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (boost > 0) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                R.string.devices_device_config_equalizer_boost_label,
                                formatGain(boost),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FeatureOverriddenByVolumeLockCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.TwoTone.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.devices_device_config_volume_lock_override_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private val eqPreviewCaps = EqCapabilities.Caps(
    bandCount = 5,
    minLevel = -1500,
    maxLevel = 1500,
    centerFrequencies = listOf(60_000, 230_000, 910_000, 3_600_000, 14_000_000),
)

@Preview2
@Composable
private fun EqualizerCardPreview() {
    PreviewWrapper {
        Column {
            EqualizerCard(
                isEnabled = true,
                isProVersion = true,
                capabilities = eqPreviewCaps,
                bandLevels = listOf(900, 300, 0, -300, 600),
                boostGain = 300,
                onCardClick = {},
                onToggle = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            EqualizerCard(
                isEnabled = false,
                isProVersion = true,
                capabilities = eqPreviewCaps,
                bandLevels = null,
                boostGain = null,
                onCardClick = {},
                onToggle = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            EqualizerCard(
                isEnabled = true,
                isProVersion = true,
                capabilities = eqPreviewCaps,
                bandLevels = listOf(1500, -1500, 1500, -1500, 0),
                boostGain = 900,
                onCardClick = {},
                onToggle = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            // No engine yet, so only the boost the user configured has anything to say.
            EqualizerCard(
                isEnabled = false,
                isProVersion = false,
                capabilities = null,
                bandLevels = null,
                boostGain = 300,
                onCardClick = {},
                onToggle = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
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
