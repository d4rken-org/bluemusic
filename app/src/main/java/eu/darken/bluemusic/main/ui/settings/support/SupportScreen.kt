package eu.darken.bluemusic.main.ui.settings.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.BlueMusicLinks
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import eu.darken.bluemusic.common.compose.icons.Discord
import eu.darken.bluemusic.common.compose.navigationBarBottomPadding
import eu.darken.bluemusic.common.debug.recorder.ui.RecorderConsentDialog
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.settings.SettingsCategoryHeader
import eu.darken.bluemusic.common.settings.SettingsDivider
import eu.darken.bluemusic.common.settings.SettingsPreferenceItem
import eu.darken.bluemusic.common.ui.waitForState
import java.io.File

@Composable
fun SupportScreenHost(vm: SupportScreenViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val state by waitForState(vm.state)
    var showShortRecordingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(vm.shortRecordingWarningEvent) {
        vm.shortRecordingWarningEvent.collect { showShortRecordingDialog = true }
    }

    // Auto-dismiss if recording stops externally
    state?.let { if (!it.isRecording) showShortRecordingDialog = false }

    state?.let { vmState ->
        SupportScreen(
            state = vmState,
            showShortRecordingDialog = showShortRecordingDialog,
            onNavigateUp = { vm.navUp() },
            onDebugLog = { vm.debugLog() },
            onOpenUrl = { url -> vm.openUrl(url) },
            onForceStopDebugLog = {
                showShortRecordingDialog = false
                vm.forceStopDebugLog()
            },
            onCancelStopWarning = {
                showShortRecordingDialog = false
                vm.cancelStopWarning()
            },
        )
    }
}

@Composable
fun SupportScreen(
    state: SupportScreenViewModel.State,
    showShortRecordingDialog: Boolean = false,
    onNavigateUp: () -> Unit,
    onDebugLog: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onForceStopDebugLog: () -> Unit = {},
    onCancelStopWarning: () -> Unit = {},
) {
    var showConsentDialog by remember { mutableStateOf(false) }

    if (showConsentDialog) {
        RecorderConsentDialog(
            onDismissRequest = { showConsentDialog = false },
            onConfirm = {
                showConsentDialog = false
                onDebugLog()
            },
            onOpenPrivacyPolicy = {
                onOpenUrl(BlueMusicLinks.PRIVACY_POLICY)
            }
        )
    }

    if (showShortRecordingDialog) {
        AlertDialog(
            onDismissRequest = onCancelStopWarning,
            title = { Text(stringResource(R.string.settings_support_debuglog_short_recording_title)) },
            text = { Text(stringResource(R.string.settings_support_debuglog_short_recording_msg)) },
            confirmButton = {
                TextButton(onClick = onCancelStopWarning) {
                    Text(stringResource(R.string.general_cancel_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onForceStopDebugLog) {
                    Text(stringResource(R.string.settings_support_debuglog_short_recording_stop))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_support_label)) },
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
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { paddingValues ->
        val navBarPadding = navigationBarBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .horizontalCutoutPadding(),
            contentPadding = PaddingValues(bottom = navBarPadding),
            verticalArrangement = Arrangement.Top
        ) {
            item {
                SettingsPreferenceItem(
                    icon = Icons.Filled.Book,
                    title = stringResource(R.string.settings_support_wiki_label),
                    subtitle = stringResource(R.string.settings_support_wiki_description),
                    onClick = { onOpenUrl(BlueMusicLinks.WIKI) }
                )
                SettingsDivider()
            }

            item {
                SettingsPreferenceItem(
                    icon = Icons.Filled.BugReport,
                    title = stringResource(R.string.settings_support_issue_tracker_label),
                    subtitle = stringResource(R.string.settings_support_issue_tracker_description),
                    onClick = { onOpenUrl(BlueMusicLinks.ISSUES) }
                )
                SettingsDivider()
            }

            item {
                SettingsPreferenceItem(
                    icon = Icons.Filled.Discord,
                    title = stringResource(R.string.settings_support_discord_label),
                    subtitle = stringResource(R.string.settings_support_discord_description),
                    onClick = { onOpenUrl("https://discord.gg/PZR7M8p") }
                )
                SettingsDivider()
            }

            item { SettingsCategoryHeader(stringResource(R.string.settings_category_other_label)) }

            item {
                SettingsPreferenceItem(
                    icon = if (state.isRecording) Icons.Filled.Notifications else Icons.Filled.Description,
                    title = stringResource(R.string.settings_support_debuglog_label),
                    subtitle = if (state.isRecording) {
                        stringResource(R.string.settings_support_debuglog_recording_desc) +
                                (state.logPath?.let { "\n" + stringResource(R.string.settings_support_debuglog_path, it.path) } ?: "")
                    } else {
                        stringResource(R.string.settings_support_debuglog_desc)
                    },
                    onClick = {
                        if (state.isRecording) {
                            // If already recording, stop immediately
                            onDebugLog()
                        } else {
                            // If not recording, show consent dialog first
                            showConsentDialog = true
                        }
                    }
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SupportScreenPreview() {
    PreviewWrapper {
        SupportScreen(
            state = SupportScreenViewModel.State(
                isRecording = true,
                logPath = File("/tmp/debug.log")
            ),
            onNavigateUp = {},
            onDebugLog = {},
            onOpenUrl = {},
        )
    }
}

@Preview2
@Composable
private fun SupportScreenNotRecordingPreview() {
    PreviewWrapper {
        SupportScreen(
            state = SupportScreenViewModel.State(
                isRecording = false,
                logPath = null
            ),
            onNavigateUp = {},
            onDebugLog = {},
            onOpenUrl = {},
        )
    }
}

@Preview2
@Composable
private fun SupportScreenShortRecordingDialogPreview() {
    PreviewWrapper {
        SupportScreen(
            state = SupportScreenViewModel.State(
                isRecording = true,
                logPath = File("/tmp/debug.log")
            ),
            showShortRecordingDialog = true,
            onNavigateUp = {},
            onDebugLog = {},
            onOpenUrl = {},
        )
    }
}
