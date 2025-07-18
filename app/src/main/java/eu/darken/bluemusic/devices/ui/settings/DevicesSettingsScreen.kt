package eu.darken.bluemusic.devices.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Start
import androidx.compose.material.icons.twotone.Approval
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.settings.SettingsDivider
import eu.darken.bluemusic.common.settings.SettingsPreferenceItem
import eu.darken.bluemusic.common.settings.SettingsSwitchItem
import eu.darken.bluemusic.common.ui.waitForState
import eu.darken.bluemusic.devices.ui.settings.dialogs.AutoplayKeycodesDialog
import eu.darken.bluemusic.main.ui.settings.general.GeneralSettingsScreen
import eu.darken.bluemusic.main.ui.settings.general.GeneralSettingsViewModel

@Composable
fun DevicesSettingsScreenHost(vm: DevicesSettingsViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val state by waitForState(vm.state)

    state?.let { state ->
        DevicesSettingsScreen(
            state = state,
            onNavigateUp = { vm.navUp() },
            onUpgradeButler = { vm.upgradeButler() },
            onToggleEnabled = { vm.onToggleEnabled(it) },
            onToggleVisibleVolumeAdjustments = { vm.onToggleVisibleVolumeAdjustments(it) },
            onToggleRestoreOnBoot = { vm.onToggleRestoreOnBoot(it) },
            onAutoplayKeycodesClicked = { vm.onAutoplayKeycodesClicked() },
            onAutoplayKeycodesChanged = { vm.onAutoplayKeycodesChanged(it) },
        )
    }
}

@Composable
fun DevicesSettingsScreen(
    state: DevicesSettingsViewModel.State,
    onNavigateUp: () -> Unit,
    onUpgradeButler: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleVisibleVolumeAdjustments: (Boolean) -> Unit,
    onToggleRestoreOnBoot: (Boolean) -> Unit,
    onAutoplayKeycodesClicked: () -> Unit,
    onAutoplayKeycodesChanged: (List<Int>) -> Unit,
) {

    val snackbarHostState = remember { SnackbarHostState() }
    var showAutoplayKeycodesDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_settings_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                stringResource(R.string.general_back_action)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.Top
        ) {
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Approval,
                    title = stringResource(R.string.label_app_enabled),
                    subtitle = stringResource(R.string.description_app_enabled),
                    checked = state.isEnabled,
                    onCheckedChange = { onToggleEnabled(it) }
                )
                SettingsDivider()
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Adjust,
                    title = stringResource(R.string.label_visible_volume_adjustments),
                    subtitle = stringResource(R.string.description_visible_volume_adjustments),
                    checked = state.visibleAdjustments,
                    onCheckedChange = { onToggleVisibleVolumeAdjustments(it) }
                )
                SettingsDivider()
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Start,
                    title = stringResource(R.string.label_boot_restore),
                    subtitle = stringResource(R.string.description_boot_restore),
                    checked = state.restoreOnBoot,
                    onCheckedChange = { onToggleRestoreOnBoot(it) }
                )
                SettingsDivider()
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.Default.PlayCircleOutline,
                    title = stringResource(R.string.label_autoplay_keytype),
                    subtitle = if (state.autoplayKeycodes.isEmpty()) {
                        stringResource(R.string.desc_autoplay_keytype)
                    } else {
                        val keycodeNames = state.autoplayKeycodes.mapNotNull { keycode ->
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
                        }.joinToString(", ")
                        keycodeNames.ifEmpty { stringResource(R.string.desc_autoplay_keytype) }
                    },
                    onClick = {
                        showAutoplayKeycodesDialog = true
                        onAutoplayKeycodesClicked()
                    }
                )
                SettingsDivider()
            }
        }
    }

    if (showAutoplayKeycodesDialog) {
        AutoplayKeycodesDialog(
            currentKeycodes = state.autoplayKeycodes,
            onConfirm = { keycodes ->
                onAutoplayKeycodesChanged(keycodes)
                showAutoplayKeycodesDialog = false
            },
            onDismiss = {
                showAutoplayKeycodesDialog = false
            }
        )
    }
}

@Preview2
@Composable
private fun GeneralSettingsScreenPreview() {
    PreviewWrapper {
        GeneralSettingsScreen(
            state = GeneralSettingsViewModel.State(filePreviews = true),
            onNavigateUp = {},
            onLanguageSwitcher = {},
            onThemeModeSelected = {},
            onThemeStyleSelected = {},
            onUpgradeButler = {},
        )
    }
}