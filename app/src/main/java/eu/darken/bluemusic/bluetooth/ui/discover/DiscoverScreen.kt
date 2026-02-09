package eu.darken.bluemusic.bluetooth.ui.discover

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import eu.darken.bluemusic.common.compose.navigationBarBottomPadding
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.ui.waitForState

@Composable
fun DiscoverScreenHost(vm: DiscoverViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val state by waitForState(vm.state)
    val snackbarHostState = remember { SnackbarHostState() }

    val upgradeMessage = stringResource(R.string.upgrade_feature_requires_pro)
    val upgradeAction = stringResource(R.string.upgrade_prompt_upgrade_action)

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is DiscoverEvent.RequiresUpgrade -> {
                    val result = snackbarHostState.showSnackbar(
                        message = upgradeMessage,
                        actionLabel = upgradeAction,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.navTo(Nav.Main.Upgrade)
                    }
                }
            }
        }
    }

    state?.let { state ->
        DiscoverScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onDeviceSelected = { vm.onDeviceSelected(it) },
            onNavigateBack = { vm.navUp() }
        )
    }
}

@Composable
fun DiscoverScreen(
    state: DiscoverViewModel.State,
    snackbarHostState: SnackbarHostState,
    onDeviceSelected: (SourceDevice) -> Unit,
    onNavigateBack: () -> Unit,
) {

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
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .horizontalCutoutPadding()
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.devices.isEmpty() -> {
                    val context = LocalContext.current
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
                            text = stringResource(R.string.discover_all_devices_managed_msg),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    log(TAG) { "Failed to open Bluetooth settings: $e" }
                                    try {
                                        val fallback = Intent(Settings.ACTION_SETTINGS)
                                        context.startActivity(fallback)
                                    } catch (e2: Exception) {
                                        log(TAG) { "Failed to open general settings: $e2" }
                                        Toast.makeText(
                                            context,
                                            R.string.general_error_no_compatible_app_found_msg,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.discover_pair_new_device_action))
                        }
                    }
                }

                else -> {
                    val navBarPadding = navigationBarBottomPadding()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp + navBarPadding)
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

private val TAG = logTag("Discover", "Screen")

@Preview
@Composable
private fun DiscoverScreenPreview() {
    PreviewWrapper {
        DiscoverScreen(
            state = DiscoverViewModel.State(
                devices = listOf(
                    MockDevice(),
                    MockDevice(),
                    MockDevice(),
                )
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onDeviceSelected = {},
            onNavigateBack = {}
        )
    }
}