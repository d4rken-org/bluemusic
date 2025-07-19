package eu.darken.bluemusic.devices.ui.manage

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.common.compose.ColoredTitleText
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.ui.waitForState
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.ui.manage.rows.Android10AppLaunchHintCard
import eu.darken.bluemusic.devices.ui.manage.rows.BatteryOptimizationHintCard
import eu.darken.bluemusic.devices.ui.manage.rows.EmptyDevicesCard
import eu.darken.bluemusic.devices.ui.manage.rows.NotificationPermissionHintCard
import eu.darken.bluemusic.devices.ui.manage.rows.device.ManagedDeviceItem

@Composable
fun DevicesScreenHost(vm: DevicesViewModel = hiltViewModel()) {

    val state by waitForState(vm.state)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        log(vm.tag, INFO) { "Permission granted: $isGranted" }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is DevicesEvent.RequestPermission -> {
                    permissionLauncher.launch(event.permission)
                }
            }
        }
    }

    state?.let { state ->
        DevicesScreen(
            state = state,
            onAddDevice = { vm.navTo(Nav.Main.DiscoverDevices) },
            onDeviceConfig = { vm.navTo(Nav.Main.DeviceConfig(it)) },
            onDeviceAction = { vm.action(it) },
            onNavigateToSettings = { vm.navTo(Nav.Main.SettingsIndex) },
            onNavigateToUpgrade = { vm.navTo(Nav.Main.Upgrade) },
            onRequestBluetoothPermission = {
                vm.action(DevicesAction.RequestBluetoothPermission)
            },
        )
    }
}

@Composable
fun DevicesScreen(
    state: DevicesViewModel.State,
    onAddDevice: () -> Unit,
    onDeviceConfig: (addr: DeviceAddr) -> Unit,
    onDeviceAction: (DevicesAction) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
) {
    Scaffold(
        topBar = {
            ManagedDevicesTopBar(
                isProVersion = state.isProVersion,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToUpgrade = onNavigateToUpgrade
            )
        },
        floatingActionButton = {
            if (state.hasBluetoothPermission && state.isBluetoothEnabled && state.devicesWithApps.isNotEmpty()) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Critical permission/state cards - these block normal functionality
            if (!state.hasBluetoothPermission) {
                item {
                    BluetoothPermissionCard(
                        onRequestPermission = onRequestBluetoothPermission
                    )
                }
            }

            if (state.hasBluetoothPermission && !state.isBluetoothEnabled) {
                item {
                    BluetoothDisabledCard()
                }
            }

            if (state.showBatteryOptimizationHint && state.batteryOptimizationIntent != null) {
                item {
                    BatteryOptimizationHintCard(
                        intent = state.batteryOptimizationIntent,
                        onDismiss = { onDeviceAction(DevicesAction.DismissBatteryOptimizationHint) }
                    )
                }
            }

            if (state.showAndroid10AppLaunchHint && state.android10AppLaunchIntent != null) {
                item {
                    Android10AppLaunchHintCard(
                        intent = state.android10AppLaunchIntent,
                        onDismiss = { onDeviceAction(DevicesAction.DismissAndroid10AppLaunchHint) }
                    )
                }
            }

            if (state.showNotificationPermissionHint) {
                item {
                    NotificationPermissionHintCard(
                        onRequestPermission = { onDeviceAction(DevicesAction.RequestNotificationPermission) },
                        onDismiss = { onDeviceAction(DevicesAction.DismissNotificationPermissionHint) }
                    )
                }
            }

            if (state.hasBluetoothPermission && state.isBluetoothEnabled) {
                if (state.devicesWithApps.isEmpty() && !state.isLoading) {
                    item {
                        EmptyDevicesCard(onAddDevice = onAddDevice)
                    }
                } else {
                    items(
                        items = state.devicesWithApps,
                        key = { it.device.address }
                    ) { deviceWithApps ->
                        ManagedDeviceItem(
                            device = deviceWithApps.device,
                            launchApps = deviceWithApps.launchApps,
                            onDeviceAction = onDeviceAction,
                            onNavigateToConfig = { onDeviceConfig(deviceWithApps.device.address) },
                            isOnlyDevice = state.devicesWithApps.size == 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagedDevicesTopBar(
    isProVersion: Boolean,
    onNavigateToSettings: () -> Unit,
    onNavigateToUpgrade: () -> Unit
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
            if (!isProVersion) {
                IconButton(onClick = onNavigateToUpgrade) {
                    Icon(
                        imageVector = Icons.TwoTone.Stars,
                        contentDescription = stringResource(R.string.general_upgrade_action)
                    )
                }
            }
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


@Preview2
@Composable
private fun DevicesScreenPreview() {
    val devices = listOf(
        MockDevice().toManagedDevice(isActive = true),
        MockDevice().toManagedDevice(),
    )

    PreviewWrapper {
        DevicesScreen(
            state = DevicesViewModel.State(
                devicesWithApps = devices.map { DevicesViewModel.DeviceWithApps(it, emptyList()) },
                isBluetoothEnabled = true,
                isProVersion = false,
            ),
            onAddDevice = {},
            onDeviceConfig = {},
            onDeviceAction = {},
            onNavigateToSettings = {},
            onNavigateToUpgrade = {},
            onRequestBluetoothPermission = {},
        )
    }
}

@Preview2
@Composable
private fun DevicesScreenEmptyPreview() {
    PreviewWrapper {
        DevicesScreen(
            state = DevicesViewModel.State(
                devicesWithApps = emptyList(),
                isBluetoothEnabled = true,
                isProVersion = true,
            ),
            onAddDevice = {},
            onDeviceConfig = {},
            onDeviceAction = {},
            onNavigateToSettings = {},
            onNavigateToUpgrade = {},
            onRequestBluetoothPermission = {},
        )
    }
}

@Preview2
@Composable
private fun ManagedDevicesScreenPermissionPreview() {
    PreviewWrapper {
        DevicesScreen(
            state = DevicesViewModel.State(
                devicesWithApps = emptyList(),
                isBluetoothEnabled = false,
                isProVersion = true,
            ),
            onAddDevice = {},
            onDeviceConfig = {},
            onDeviceAction = {},
            onNavigateToSettings = {},
            onNavigateToUpgrade = {},
            onRequestBluetoothPermission = {},
        )
    }
}
