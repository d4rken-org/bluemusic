package eu.darken.bluemusic.devices.ui.manage

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.devices.core.ManagedDevice

@Composable
fun ManagedDevicesScreen(
    state: ManagedDevicesState,
    onEvent: (ManagedDevicesEvent) -> Unit,
    onNavigateToConfig: (deviceAddress: String) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            ManagedDevicesTopBar(
                isProVersion = state.isProVersion,
                onNavigateToSettings = onNavigateToSettings
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    onEvent(ManagedDevicesEvent.OnAddDeviceClicked)
                    onNavigateToDiscover()
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.label_add_device)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Bluetooth state
            if (!state.isBluetoothEnabled) {
                item {
                    BluetoothDisabledCard()
                }
            }
            
            // Battery optimization hint
            if (state.showBatteryOptimizationHint && state.batteryOptimizationIntent != null) {
                item {
                    BatteryOptimizationHintCard(
                        intent = state.batteryOptimizationIntent,
                        onDismiss = { onEvent(ManagedDevicesEvent.OnBatterySavingDismissed) }
                    )
                }
            }
            
            // Android 10 app launch hint
            if (state.showAndroid10AppLaunchHint && state.android10AppLaunchIntent != null) {
                item {
                    Android10AppLaunchHintCard(
                        intent = state.android10AppLaunchIntent,
                        onDismiss = { onEvent(ManagedDevicesEvent.OnAppLaunchHintDismissed) }
                    )
                }
            }
            
            // Notification permission hint
            if (state.showNotificationPermissionHint) {
                item {
                    NotificationPermissionHintCard(
                        onRequestPermission = {
                            // Permission request handled by ScreenHost
                        },
                        onDismiss = { onEvent(ManagedDevicesEvent.OnNotificationPermissionsDismissed) }
                    )
                }
            }
            
            // Device list
            if (state.devices.isEmpty() && !state.isLoading) {
                item {
                    EmptyDevicesMessage()
                }
            } else {
                items(
                    items = state.devices,
                    key = { it.address }
                ) { device ->
                    ManagedDeviceItem(
                        device = device,
                        onEvent = onEvent,
                        onNavigateToConfig = { onNavigateToConfig(device.address) }
                    )
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
            Text(stringResource(R.string.app_name))
        },
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.label_settings)
                )
            }
        }
    )
}

@Composable
private fun BluetoothDisabledCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.description_bluetooth_is_disabled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
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
                    Text(stringResource(R.string.label_settings))
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
                    Text(stringResource(R.string.label_settings))
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
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
}

@Preview
@Composable
private fun ManagedDevicesScreenPreview() {
    PreviewWrapper {
        ManagedDevicesScreen(
            state = ManagedDevicesState(
                devices = listOf(
                    ManagedDevice(
                        address = "00:11:22:33:44:55",
                        lastConnected = System.currentTimeMillis()
                    ),
                    ManagedDevice(
                        address = "AA:BB:CC:DD:EE:FF",
                        lastConnected = System.currentTimeMillis() - 86400000
                    )
                ),
                isBluetoothEnabled = true,
                isProVersion = false,
                showBatteryOptimizationHint = true,
                batteryOptimizationIntent = Intent()
            ),
            onEvent = {},
            onNavigateToConfig = {},
            onNavigateToDiscover = {},
            onNavigateToSettings = {}
        )
    }
}