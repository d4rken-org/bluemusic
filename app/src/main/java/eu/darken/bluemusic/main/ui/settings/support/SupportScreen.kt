package eu.darken.bluemusic.main.ui.settings.support

import android.text.format.Formatter
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.BlueMusicLinks
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import eu.darken.bluemusic.common.compose.icons.Discord
import eu.darken.bluemusic.common.compose.navigationBarBottomPadding
import eu.darken.bluemusic.common.debug.recorder.core.DebugLogStore
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm.snackbarEvent) {
        vm.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    state?.let { vmState ->
        SupportScreen(
            state = vmState,
            snackbarHostState = snackbarHostState,
            onNavigateUp = { vm.navUp() },
            onDebugLog = { vm.debugLog() },
            onOpenUrl = { url -> vm.openUrl(url) },
            onContactDeveloper = { vm.contactDeveloper() },
            onDeleteStoredLogs = { vm.deleteStoredLogs() },
        )
    }
}

@Composable
fun SupportScreen(
    state: SupportScreenViewModel.State,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit,
    onDebugLog: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onContactDeveloper: () -> Unit,
    onDeleteStoredLogs: () -> Unit,
) {
    var showConsentDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

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

    if (showDeleteDialog) {
        val sessionCount = state.storedStats?.sessionCount ?: 0
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_support_stored_logs_delete_title)) },
            text = { Text(stringResource(R.string.settings_support_stored_logs_delete_msg, sessionCount)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteStoredLogs()
                }) {
                    Text(stringResource(R.string.general_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.general_cancel_action))
                }
            }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            item {
                SettingsPreferenceItem(
                    icon = Icons.Filled.Email,
                    title = stringResource(R.string.settings_support_contact_label),
                    subtitle = stringResource(R.string.settings_support_contact_desc),
                    onClick = onContactDeveloper,
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
                            onDebugLog()
                        } else {
                            showConsentDialog = true
                        }
                    }
                )
                SettingsDivider()
            }

            item {
                val context = LocalContext.current
                val storedStats = state.storedStats
                val subtitle = when {
                    storedStats == null -> ""
                    storedStats.sessionCount == 0 -> stringResource(R.string.settings_support_stored_logs_empty)
                    else -> stringResource(
                        R.string.settings_support_stored_logs_desc,
                        storedStats.sessionCount,
                        Formatter.formatShortFileSize(context, storedStats.totalSize),
                    )
                }
                SettingsPreferenceItem(
                    icon = Icons.Filled.Delete,
                    title = stringResource(R.string.settings_support_stored_logs_label),
                    subtitle = subtitle,
                    onClick = {
                        if (!state.isRecording && (storedStats?.sessionCount ?: 0) > 0) {
                            showDeleteDialog = true
                        }
                    }
                )
                SettingsDivider()
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
                storedStats = DebugLogStore.Stats(sessionCount = 3, totalSize = 1024 * 1024 * 5L),
            ),
            onNavigateUp = {},
            onDebugLog = {},
            onOpenUrl = {},
            onContactDeveloper = {},
            onDeleteStoredLogs = {},
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
                storedStats = DebugLogStore.Stats(sessionCount = 0, totalSize = 0),
            ),
            onNavigateUp = {},
            onDebugLog = {},
            onOpenUrl = {},
            onContactDeveloper = {},
            onDeleteStoredLogs = {},
        )
    }
}
