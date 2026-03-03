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
import androidx.compose.material.icons.filled.Folder
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@Composable
fun SupportScreenHost(vm: SupportScreenViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    LifecycleResumeEffect(vm) {
        vm.onResume()
        onPauseOrDispose { }
    }

    val state by vm.state.collectAsStateWithLifecycle(null)
    var showShortRecordingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                is SupportScreenViewModel.SupportEvent.ShowShortRecordingWarning -> {
                    showShortRecordingDialog = true
                }
            }
        }
    }

    state?.let { vmState ->
        SupportScreen(
            state = vmState,
            showShortRecordingDialog = showShortRecordingDialog,
            onNavigateUp = { vm.navUp() },
            onStartDebugLog = { vm.startDebugLog() },
            onStopDebugLog = { vm.stopDebugLog() },
            onOpenUrl = { url -> vm.openUrl(url) },
            onConfirmStopDebugLog = {
                showShortRecordingDialog = false
                vm.confirmStopDebugLog()
            },
            onDismissShortRecordingDialog = {
                showShortRecordingDialog = false
            },
            onDeleteAllDebugLogs = { vm.deleteAllDebugLogs() },
        )
    }
}

@Composable
fun SupportScreen(
    state: SupportScreenViewModel.State,
    showShortRecordingDialog: Boolean = false,
    onNavigateUp: () -> Unit,
    onStartDebugLog: () -> Unit,
    onStopDebugLog: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onConfirmStopDebugLog: () -> Unit = {},
    onDismissShortRecordingDialog: () -> Unit = {},
    onDeleteAllDebugLogs: () -> Unit = {},
) {
    var showConsentDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showConsentDialog) {
        RecorderConsentDialog(
            onDismissRequest = { showConsentDialog = false },
            onConfirm = {
                showConsentDialog = false
                onStartDebugLog()
            },
            onOpenPrivacyPolicy = {
                onOpenUrl(BlueMusicLinks.PRIVACY_POLICY)
            }
        )
    }

    if (showShortRecordingDialog) {
        AlertDialog(
            onDismissRequest = onDismissShortRecordingDialog,
            title = { Text(stringResource(R.string.settings_support_debuglog_short_recording_title)) },
            text = { Text(stringResource(R.string.settings_support_debuglog_short_recording_msg)) },
            dismissButton = {
                TextButton(onClick = onConfirmStopDebugLog) {
                    Text(stringResource(R.string.settings_support_debuglog_short_recording_stop))
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissShortRecordingDialog) {
                    Text(stringResource(R.string.settings_support_debuglog_short_recording_continue))
                }
            },
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.settings_support_debuglog_folder_delete_title)) },
            text = { Text(stringResource(R.string.settings_support_debuglog_folder_delete_msg)) },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.general_cancel_action))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    onDeleteAllDebugLogs()
                }) {
                    Text(stringResource(R.string.general_delete_action))
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
                            onStopDebugLog()
                        } else {
                            showConsentDialog = true
                        }
                    }
                )
            }

            if (state.folderSessionCount > 0) {
                item {
                    SettingsPreferenceItem(
                        icon = Icons.Filled.Folder,
                        title = stringResource(R.string.settings_support_debuglog_folder_label),
                        subtitle = pluralStringResource(
                            R.plurals.settings_support_debuglog_folder_desc,
                            state.folderSessionCount,
                            state.folderSessionCount,
                            state.folderTotalSize ?: "",
                        ),
                        enabled = !state.isRecording,
                        onClick = { showDeleteConfirmDialog = true },
                    )
                }
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
                logPath = File("/tmp/debug.log"),
                folderSessionCount = 3,
                folderTotalSize = "4.2 MB",
            ),
            onNavigateUp = {},
            onStartDebugLog = {},
            onStopDebugLog = {},
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
                logPath = null,
                folderSessionCount = 1,
                folderTotalSize = "512 KB",
            ),
            onNavigateUp = {},
            onStartDebugLog = {},
            onStopDebugLog = {},
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
                logPath = File("/tmp/debug.log"),
            ),
            showShortRecordingDialog = true,
            onNavigateUp = {},
            onStartDebugLog = {},
            onStopDebugLog = {},
            onOpenUrl = {},
        )
    }
}

@Preview2
@Composable
private fun SupportScreenEmptyFolderPreview() {
    PreviewWrapper {
        SupportScreen(
            state = SupportScreenViewModel.State(
                isRecording = false,
                logPath = null,
                folderSessionCount = 0,
                folderTotalSize = null,
            ),
            onNavigateUp = {},
            onStartDebugLog = {},
            onStopDebugLog = {},
            onOpenUrl = {},
        )
    }
}
