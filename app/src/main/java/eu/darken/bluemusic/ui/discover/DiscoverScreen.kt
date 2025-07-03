package eu.darken.bluemusic.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.ui.theme.BlueMusicTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    state: DiscoverState,
    onEvent: (DiscoverEvent) -> Unit,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(state.shouldClose) {
        if (state.shouldClose) {
            onNavigateBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_add_device)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.abc_action_bar_up_description)
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = SnackbarHostState()) {
                state.error?.let { error ->
                    Snackbar(
                        snackbarData = object : SnackbarData {
                            override val visuals = SnackbarVisuals(
                                message = error,
                                actionLabel = null,
                                withDismissAction = true,
                                duration = SnackbarDuration.Short
                            )
                            override fun performAction() {}
                            override fun dismiss() {}
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.availableDevices.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.label_no_devices_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(state.availableDevices) { device ->
                            DeviceItem(
                                device = device,
                                onClick = { onEvent(DiscoverEvent.OnDeviceSelected(device)) }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Dialogs
    if (state.showUpgradeDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(DiscoverEvent.OnDismissDialog) },
            icon = { Icon(Icons.Default.Stars, contentDescription = null) },
            title = { Text(stringResource(R.string.label_premium_version)) },
            text = { Text(stringResource(R.string.description_premium_required_additional_devices)) },
            confirmButton = {
                TextButton(onClick = { /* Handled by ScreenHost */ }) {
                    Text(stringResource(R.string.action_upgrade))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(DiscoverEvent.OnDismissDialog) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun DeviceItem(
    device: SourceDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                text = device.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = device.name?.let {
            {
                Text(
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = if (device.address == FakeSpeakerDevice.ADDR) {
                    Icons.Default.Speaker
                } else {
                    Icons.Default.Bluetooth
                },
                contentDescription = null
            )
        },
        modifier = modifier.clickable(onClick = onClick)
    )
}

@Preview
@Composable
private fun DiscoverScreenPreview() {
    BlueMusicTheme {
        DiscoverScreen(
            state = DiscoverState(
                availableDevices = listOf(
                    object : SourceDevice {
                        override val address = FakeSpeakerDevice.ADDR
                        override val name = "Phone Speaker"
                        override val alias: String? = null
                        override val label = "Phone Speaker"
                        override fun getStreamId(type: eu.darken.bluemusic.main.core.audio.AudioStream.Type) = 
                            eu.darken.bluemusic.main.core.audio.AudioStream.Id.STREAM_MUSIC
                    },
                    object : SourceDevice {
                        override val address = "00:11:22:33:44:55"
                        override val name = "My Bluetooth Headphones"
                        override val alias: String? = null
                        override val label = "My Bluetooth Headphones"
                        override fun getStreamId(type: eu.darken.bluemusic.main.core.audio.AudioStream.Type) = 
                            eu.darken.bluemusic.main.core.audio.AudioStream.Id.STREAM_MUSIC
                    }
                )
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}