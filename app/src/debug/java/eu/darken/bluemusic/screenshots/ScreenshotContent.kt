package eu.darken.bluemusic.screenshots

import android.view.KeyEvent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.common.apps.AppInfo
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.devices.ui.appselection.AppSelectionScreen
import eu.darken.bluemusic.devices.ui.appselection.AppSelectionViewModel
import eu.darken.bluemusic.devices.ui.config.DeviceConfigScreen
import eu.darken.bluemusic.devices.ui.config.DeviceConfigViewModel
import eu.darken.bluemusic.devices.ui.dashboard.DashboardViewModel
import eu.darken.bluemusic.devices.ui.dashboard.DevicesScreen
import eu.darken.bluemusic.devices.ui.settings.dialogs.AutoplayKeycodesDialog
import eu.darken.bluemusic.main.ui.settings.SettingsIndexScreen
import eu.darken.bluemusic.main.ui.settings.SettingsViewModel

internal const val DS = "spec:width=1080px,height=2400px,dpi=428"

private val mockDevice1 = MockDevice(
    label = "Sony WH-1000XM5",
    address = "AA:BB:CC:DD:EE:01",
)

private val mockDevice2 = MockDevice(
    label = "JBL Flip 6",
    address = "AA:BB:CC:DD:EE:02",
)

private val mockApps = listOf(
    AppInfo(packageName = "com.spotify.music", label = "Spotify", icon = null),
    AppInfo(packageName = "com.google.android.apps.youtube.music", label = "YouTube Music", icon = null),
    AppInfo(packageName = "com.bambuna.podcastaddict", label = "Podcast Addict", icon = null),
    AppInfo(packageName = "com.audible.application", label = "Audible", icon = null),
    AppInfo(packageName = "com.soundcloud.android", label = "SoundCloud", icon = null),
    AppInfo(packageName = "com.apple.android.music", label = "Apple Music", icon = null),
    AppInfo(packageName = "deezer.android.app", label = "Deezer", icon = null),
    AppInfo(packageName = "com.amazon.mp3", label = "Amazon Music", icon = null),
    AppInfo(packageName = "com.aspiro.tidal", label = "Tidal", icon = null),
    AppInfo(packageName = "com.pandora.android", label = "Pandora", icon = null),
)

@Composable
internal fun DashboardContent() {
    PreviewWrapper {
        DevicesScreen(
            state = DashboardViewModel.State(
                devicesWithApps = listOf(
                    DashboardViewModel.DeviceWithApps(
                        device = mockDevice1.toManagedDevice(isConnected = true),
                        launchApps = emptyList(),
                    ),
                    DashboardViewModel.DeviceWithApps(
                        device = mockDevice2.toManagedDevice(),
                        launchApps = emptyList(),
                    ),
                ),
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

@Composable
internal fun DeviceConfigTopContent() {
    PreviewWrapper {
        DeviceConfigScreen(
            state = DeviceConfigViewModel.State(
                device = mockDevice1.toManagedDevice(isConnected = true),
                isProVersion = true,
                isLoading = false,
            ),
            onAction = {},
            onNavigateBack = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Composable
internal fun DeviceConfigReactionContent() {
    PreviewWrapper {
        DeviceConfigScreen(
            state = DeviceConfigViewModel.State(
                device = mockDevice1.toManagedDevice(isConnected = true),
                isProVersion = true,
                isLoading = false,
            ),
            onAction = {},
            onNavigateBack = {},
            snackbarHostState = remember { SnackbarHostState() },
            listState = rememberLazyListState(initialFirstVisibleItemIndex = 3),
        )
    }
}

@Composable
internal fun DeviceConfigTimingContent() {
    PreviewWrapper {
        DeviceConfigScreen(
            state = DeviceConfigViewModel.State(
                device = mockDevice1.toManagedDevice(isConnected = true),
                isProVersion = true,
                isLoading = false,
            ),
            onAction = {},
            onNavigateBack = {},
            snackbarHostState = remember { SnackbarHostState() },
            listState = rememberLazyListState(initialFirstVisibleItemIndex = 4),
        )
    }
}

@Composable
internal fun AppSelectionContent() {
    PreviewWrapper {
        AppSelectionScreen(
            state = AppSelectionViewModel.State(
                deviceName = "Sony WH-1000XM5",
                apps = mockApps,
                filteredApps = mockApps,
                selectedPackages = setOf("com.spotify.music", "com.google.android.apps.youtube.music"),
                searchQuery = "",
                isLoading = false,
            ),
            onSearchQueryChanged = {},
            onAppToggled = {},
            onClearSelection = {},
            onNavigateBack = {},
        )
    }
}

@Composable
internal fun AutoplayContent() {
    PreviewWrapper {
        AutoplayKeycodesDialog(
            currentKeycodes = listOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_NEXT),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Composable
internal fun SettingsContent() {
    PreviewWrapper {
        SettingsIndexScreen(
            state = SettingsViewModel.State(
                isUpgraded = true,
                versionText = "3.1.4",
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
            onNavigateTo = {},
            onOpenUrl = {},
            onCopyVersion = {},
        )
    }
}

@Preview(name = "Dashboard", device = DS)
@Composable
private fun DashboardPreview() = DashboardContent()

@Preview(name = "DeviceConfigTop", device = DS)
@Composable
private fun DeviceConfigTopPreview() = DeviceConfigTopContent()

@Preview(name = "DeviceConfigReaction", device = DS)
@Composable
private fun DeviceConfigReactionPreview() = DeviceConfigReactionContent()

@Preview(name = "DeviceConfigTiming", device = DS)
@Composable
private fun DeviceConfigTimingPreview() = DeviceConfigTimingContent()

@Preview(name = "AppSelection", device = DS)
@Composable
private fun AppSelectionPreview() = AppSelectionContent()

@Preview(name = "Autoplay", device = DS)
@Composable
private fun AutoplayPreview() = AutoplayContent()

@Preview(name = "Settings", device = DS)
@Composable
private fun SettingsPreview() = SettingsContent()
