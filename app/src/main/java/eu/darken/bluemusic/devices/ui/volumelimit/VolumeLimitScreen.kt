package eu.darken.bluemusic.devices.ui.volumelimit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.ui.icon
import eu.darken.bluemusic.monitor.core.audio.AudioStream

@Composable
fun VolumeLimitScreenHost(
    addr: DeviceAddr,
    vm: VolumeLimitViewModel = hiltViewModel(
        key = addr,
        creationCallback = { factory: VolumeLimitViewModel.Factory -> factory.create(deviceAddress = addr) }
    ),
) {
    ErrorEventHandler(vm)

    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                is VolumeLimitViewModel.Event.RequiresPro -> vm.navTo(Nav.Main.Upgrade())
            }
        }
    }

    state?.let {
        VolumeLimitScreen(
            state = it,
            onNavigateBack = { vm.navUp() },
            onToggleLimit = { vm.onToggleLimit() },
            onLimitChange = { type, min, max -> vm.onLimitChanged(type, min, max) },
        )
    }
}

@Composable
fun VolumeLimitScreen(
    state: VolumeLimitViewModel.State,
    onNavigateBack: () -> Unit,
    onToggleLimit: () -> Unit,
    onLimitChange: (AudioStream.Type, Float?, Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val device = state.device
    // Bounds only apply to streams this device manages.
    val managedStreams = AudioStream.Type.entries.filter { device.getVolume(it) != null }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.devices_volume_limit_screen_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = device.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.general_navigate_back_action),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { paddingValues ->
        val navBarPadding = navigationBarBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .horizontalCutoutPadding(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp + navBarPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "enable") {
                EnableCard(
                    isEnabled = device.volumeLimit,
                    isProVersion = state.isProVersion,
                    onToggleLimit = onToggleLimit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            if (managedStreams.isEmpty()) {
                item(key = "no_streams") { NoStreamsCard() }
                return@LazyColumn
            }

            item(key = "limits") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        managedStreams.forEach { streamType ->
                            VolumeLimitPreference(
                                title = stringResource(
                                    R.string.devices_device_config_volume_limit_stream_label,
                                    getStreamLabel(streamType),
                                ),
                                icon = streamType.icon,
                                min = device.getVolumeMin(streamType),
                                max = device.getVolumeMax(streamType),
                                stepCount = state.volumeStepCounts[streamType],
                                onLimitChange = { min, max -> onLimitChange(streamType, min, max) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The whole row is one toggle target, so the switch itself takes no click of its own: two
 * accessibility actions for the same thing would only be read out twice.
 */
@Composable
private fun EnableCard(
    isEnabled: Boolean,
    isProVersion: Boolean,
    onToggleLimit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = isEnabled,
                    role = Role.Switch,
                    onValueChange = { onToggleLimit() },
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.devices_device_config_volume_limit_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                // The sliders stay editable while the limit is off, so this is the only thing
                // telling the user why nothing they set is applied yet.
                Text(
                    text = when {
                        isEnabled -> stringResource(R.string.devices_device_config_volume_limit_desc)
                        else -> stringResource(R.string.devices_volume_limit_disabled_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isProVersion) {
                UpgradeBadge(modifier = Modifier.padding(horizontal = 8.dp))
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun NoStreamsCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = Icons.TwoTone.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.devices_volume_limit_no_streams_msg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun previewState(
    isEnabled: Boolean = true,
    isProVersion: Boolean = true,
    managesStreams: Boolean = true,
): VolumeLimitViewModel.State {
    val device = MockDevice(label = "Sony WH-1000XM5", address = "AA:BB:CC:DD:EE:01")
        .toManagedDevice(isConnected = true)
    return VolumeLimitViewModel.State(
        device = device.copy(
            config = device.config.copy(
                volumeLimit = isEnabled,
                musicVolume = if (managesStreams) 0.7f else null,
                callVolume = if (managesStreams) 0.6f else null,
                ringVolume = null,
                notificationVolume = null,
                alarmVolume = null,
                musicVolumeMin = 0.2f,
                musicVolumeMax = 0.7f,
                callVolumeMin = 0.3f,
            )
        ),
        isProVersion = isProVersion,
        volumeStepCounts = mapOf(AudioStream.Type.MUSIC to 15),
    )
}

@Preview2
@Composable
private fun VolumeLimitScreenPreview() {
    PreviewWrapper {
        VolumeLimitScreen(
            state = previewState(),
            onNavigateBack = {},
            onToggleLimit = {},
            onLimitChange = { _, _, _ -> },
        )
    }
}

@Preview2
@Composable
private fun VolumeLimitScreenDisabledPreview() {
    PreviewWrapper {
        VolumeLimitScreen(
            state = previewState(isEnabled = false, isProVersion = false),
            onNavigateBack = {},
            onToggleLimit = {},
            onLimitChange = { _, _, _ -> },
        )
    }
}

@Preview2
@Composable
private fun VolumeLimitScreenNoStreamsPreview() {
    PreviewWrapper {
        VolumeLimitScreen(
            state = previewState(managesStreams = false),
            onNavigateBack = {},
            onToggleLimit = {},
            onLimitChange = { _, _, _ -> },
        )
    }
}
