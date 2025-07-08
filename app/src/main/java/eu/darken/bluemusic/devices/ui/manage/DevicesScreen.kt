package eu.darken.bluemusic.devices.ui.manage

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Devices
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.ColoredTitleText
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.ui.waitForState
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.ManagedDevice

@Composable
fun ManagedDevicesScreenHost(vm: DevicesViewModel = hiltViewModel()) {

    val state by waitForState(vm.state)

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        log(vm.tag, INFO) { "Bluetooth permission granted: $isGranted" }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is DevicesEvent.RequestPermission -> {
                    bluetoothPermissionLauncher.launch(event.permission)
                }
            }
        }
    }

    state?.let { state ->
        ManagedDevicesScreen(
            state = state,
            onAddDevice = { vm.navTo(Nav.Main.DiscoverDevices) },
            onDeviceConfig = { vm.navTo(Nav.Main.DeviceConfig(it)) },
            onDeviceAction = { vm.action(it) },
            onNavigateToSettings = { vm.navTo(Nav.Main.SettingsIndex) },
            onRequestBluetoothPermission = {
                vm.action(DevicesAction.RequestBluetoothPermission)
            },
        )
    }
}

@Composable
fun ManagedDevicesScreen(
    state: DevicesViewModel.State,
    onAddDevice: () -> Unit,
    onDeviceConfig: (addr: DeviceAddr) -> Unit,
    onDeviceAction: (DevicesAction) -> Unit,
    onNavigateToSettings: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
) {
    Scaffold(
        topBar = {
            ManagedDevicesTopBar(
                isProVersion = state.isProVersion,
                onNavigateToSettings = onNavigateToSettings
            )
        },
        floatingActionButton = {
            if (state.hasBluetoothPermission && state.isBluetoothEnabled) {
                FloatingActionButton(
                    onClick = { onAddDevice() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Add,
                        contentDescription = stringResource(R.string.label_add_device)
                    )
                }
            }
        }
    ) { paddingValues ->
        when {
            !state.hasBluetoothPermission -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        BluetoothPermissionCard(
                            onRequestPermission = onRequestBluetoothPermission
                        )
                    }
                }
            }

            !state.isBluetoothEnabled -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        BluetoothDisabledCard()
                    }
                }
            }

            state.devices.isEmpty() && !state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyDevicesMessage()
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
//                    // Battery optimization hint
//                    if (state.showBatteryOptimizationHint && state.batteryOptimizationIntent != null) {
//                        item {
//                            BatteryOptimizationHintCard(
//                                intent = state.batteryOptimizationIntent,
//                                onDismiss = { onEvent(ManagedDevicesEvent.OnBatterySavingDismissed) }
//                            )
//                        }
//                    }
//
//                    // Android 10 app launch hint
//                    if (state.showAndroid10AppLaunchHint && state.android10AppLaunchIntent != null) {
//                        item {
//                            Android10AppLaunchHintCard(
//                                intent = state.android10AppLaunchIntent,
//                                onDismiss = { onEvent(ManagedDevicesEvent.OnAppLaunchHintDismissed) }
//                            )
//                        }
//                    }
//
//                    // Notification permission hint
//                    if (state.showNotificationPermissionHint) {
//                        item {
//                            NotificationPermissionHintCard(
//                                onRequestPermission = {
//                                    // Permission request handled by ScreenHost
//                                },
//                                onDismiss = { onEvent(ManagedDevicesEvent.OnNotificationPermissionsDismissed) }
//                            )
//                        }
//                    }

                    items(
                        items = state.devices,
                        key = { it.address }
                    ) { device ->
                        ManagedDeviceItem(
                            device = device,
                            onDeviceAction = onDeviceAction,
                            onNavigateToConfig = { onDeviceConfig(device.address) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagedDevicesTopBar(
    isProVersion: Boolean,
    onNavigateToSettings: () -> Unit
) {
    TopAppBar(
        title = {
            if (isProVersion) {
                ColoredTitleText(
                    fullTitle = stringResource(R.string.app_name_upgraded),
                    postfix = stringResource(R.string.app_name_upgrade_postfix),
                )
            } else {
                Text(text = stringResource(R.string.app_name))
            }
        },
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.TwoTone.Settings,
                    contentDescription = stringResource(R.string.settings_label)
                )
            }
        }
    )
}

@Composable
private fun BluetoothDisabledCard() {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.BluetoothDisabled,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.title_bluetooth_is_off),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.description_bluetooth_is_disabled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    context.startActivity(intent)
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.action_enable_bluetooth))
            }
        }
    }
}

@Composable
private fun BluetoothPermissionCard(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.TwoTone.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.title_bluetooth_permission_required),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.description_bluetooth_permission_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRequestPermission,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.action_grant_permission))
            }
        }
    }
}

@Composable
private fun BatteryOptimizationHintCard(
    intent: Intent,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.battery_optimization_hint_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.battery_optimization_hint_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_dismiss))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { context.startActivity(intent) }
                ) {
                    Text(stringResource(R.string.settings_label))
                }
            }
        }
    }
}

@Composable
private fun Android10AppLaunchHintCard(
    intent: Intent,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.android10_applaunch_hint_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.android10_applaunch_hint_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_dismiss))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { context.startActivity(intent) }
                ) {
                    Text(stringResource(R.string.settings_label))
                }
            }
        }
    }
}

@Composable
private fun NotificationPermissionHintCard(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.notification_permission_hint_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.notification_permission_hint_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_dismiss))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.action_set))
                }
            }
        }
    }
}

@Composable
private fun EmptyDevicesMessage() {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.TwoTone.Devices,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.managed_devices_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview2
@Composable
private fun ManagedDevicesScreenPreview() {
    val devices = listOf(
        ManagedDevice(
            address = "00:11:22:33:44:55",
            lastConnected = System.currentTimeMillis()
        ),
        ManagedDevice(
            address = "AA:BB:CC:DD:EE:FF",
            lastConnected = System.currentTimeMillis() - 86400000
        )
    )

    PreviewWrapper {
        ManagedDevicesScreen(
            state = DevicesViewModel.State(
                devices = devices,
                isBluetoothEnabled = true,
                isProVersion = false,
            ),
            onAddDevice = {},
            onDeviceConfig = {},
            onDeviceAction = {},
            onNavigateToSettings = {},
            onRequestBluetoothPermission = {},
        )
    }
}

@Preview2
@Composable
private fun ManagedDevicesScreenEmptyPreview() {
    PreviewWrapper {
        ManagedDevicesScreen(
            state = DevicesViewModel.State(
                devices = emptyList(),
                isBluetoothEnabled = true,
                isProVersion = true,
            ),
            onAddDevice = {},
            onDeviceConfig = {},
            onDeviceAction = {},
            onNavigateToSettings = {},
            onRequestBluetoothPermission = {},
        )
    }
}

@Preview2
@Composable
private fun ManagedDevicesScreenPermissionPreview() {
    PreviewWrapper {
        ManagedDevicesScreen(
            state = DevicesViewModel.State(
                devices = emptyList(),
                isBluetoothEnabled = false,
                isProVersion = true,
            ),
            onAddDevice = {},
            onDeviceConfig = {},
            onDeviceAction = {},
            onNavigateToSettings = {},
            onRequestBluetoothPermission = {},
        )
    }
}
