package eu.darken.bluemusic.bluetooth.ui.discover

import android.bluetooth.BluetoothClass
import android.os.Parcel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Phone
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.speaker.FakeSpeakerDevice
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.ui.waitForState
import eu.darken.bluemusic.main.core.audio.AudioStream

@Composable
fun DiscoverScreenHost(vm: DiscoverViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val state by waitForState(vm.state)

    state?.let { state ->
        DiscoverScreen(
            state = state,
            onDeviceSelected = { vm.onDeviceSelected(it) },
            onNavigateBack = { vm.navUp() }
        )
    }
}

@Composable
fun DiscoverScreen(
    state: DiscoverViewModel.State,
    onDeviceSelected: (SourceDevice) -> Unit,
    onNavigateBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_add_device)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = ""
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
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

                state.devices.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Settings,
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
                        items(state.devices) { device ->
                            DeviceItem(
                                device = device,
                                onClick = { onDeviceSelected(device) }
                            )
                        }
                    }
                }
            }
        }
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
                imageVector = if (device.address == FakeSpeakerDevice.address) {
                    Icons.TwoTone.Phone
                } else {
                    Icons.TwoTone.Settings
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
    PreviewWrapper {
        DiscoverScreen(
            state = DiscoverViewModel.State(
                devices = listOf(
                    object : SourceDevice {
                        override val bluetoothClass: BluetoothClass? = null
                        override val address = FakeSpeakerDevice.address
                        override val name = "Phone Speaker"
                        override val alias: String? = null
                        override val label = "Phone Speaker"
                        override fun getStreamId(type: AudioStream.Type) =
                            AudioStream.Id.STREAM_MUSIC

                        override fun describeContents() = 0
                        override fun writeToParcel(dest: Parcel, flags: Int) {}
                    },
                    object : SourceDevice {
                        override val bluetoothClass: BluetoothClass? = null
                        override val address = "00:11:22:33:44:55"
                        override val name = "My Bluetooth Headphones"
                        override val alias: String? = null
                        override val label = "My Bluetooth Headphones"
                        override fun getStreamId(type: AudioStream.Type) =
                            AudioStream.Id.STREAM_MUSIC

                        override fun describeContents() = 0
                        override fun writeToParcel(dest: Parcel, flags: Int) {}
                    }
                )
            ),
            onDeviceSelected = {},
            onNavigateBack = {},
        )
    }
}