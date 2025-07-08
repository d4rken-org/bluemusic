package eu.darken.bluemusic.main.ui.settings.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import eu.darken.bluemusic.common.BlueMusicLinks
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.icons.Discord
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

    state?.let { vmState ->
        SupportScreen(
            state = vmState,
            onNavigateUp = { vm.navUp() },
            onDebugLog = { vm.debugLog() },
            onOpenUrl = { url -> vm.openUrl(url) },
        )
    }
}

@Composable
fun SupportScreen(
    state: SupportScreenViewModel.State,
    onNavigateUp: () -> Unit,
    onDebugLog: () -> Unit,
    onOpenUrl: (String) -> Unit,
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
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
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
