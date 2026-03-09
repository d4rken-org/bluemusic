package eu.darken.bluemusic.main.ui.settings.support

import android.content.Intent
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.FiberManualRecord
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.BlueMusicLinks
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import eu.darken.bluemusic.common.compose.icons.Discord
import eu.darken.bluemusic.common.compose.navigationBarBottomPadding
import eu.darken.bluemusic.common.debug.recorder.core.DebugSession
import eu.darken.bluemusic.common.debug.recorder.ui.RecorderActivity
import eu.darken.bluemusic.common.debug.recorder.ui.RecorderConsentDialog
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.settings.SettingsCategoryHeader
import eu.darken.bluemusic.common.settings.SettingsDivider
import eu.darken.bluemusic.common.settings.SettingsPreferenceItem

private sealed interface SupportDialog {
    data object Consent : SupportDialog
    data object ShortRecordingWarning : SupportDialog
    data class DeleteSession(val id: String) : SupportDialog
    data object ClearAll : SupportDialog
}

@Composable
fun SupportScreenHost(vm: SupportScreenViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    LifecycleResumeEffect(Unit) {
        vm.refreshSessions()
        onPauseOrDispose {}
    }

    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle(null)

    var dialog by remember { mutableStateOf<SupportDialog?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                SupportScreenViewModel.Event.ShowConsentDialog -> {
                    dialog = SupportDialog.Consent
                }

                SupportScreenViewModel.Event.ShowShortRecordingWarning -> {
                    dialog = SupportDialog.ShortRecordingWarning
                }

                is SupportScreenViewModel.Event.OpenRecorderActivity -> {
                    val intent = RecorderActivity.getLaunchIntent(context, event.sessionId, event.legacyPath).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    when (val d = dialog) {
        is SupportDialog.Consent -> {
            RecorderConsentDialog(
                onStartRecord = {
                    vm.startDebugLog()
                },
                onOpenPrivacyPolicy = {
                    vm.openUrl(BlueMusicLinks.PRIVACY_POLICY)
                },
                onDismiss = { dialog = null },
            )
        }

        is SupportDialog.ShortRecordingWarning -> {
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.settings_support_debuglog_short_recording_title)) },
                text = { Text(stringResource(R.string.settings_support_debuglog_short_recording_msg)) },
                confirmButton = {
                    TextButton(onClick = { dialog = null }) {
                        Text(stringResource(R.string.settings_support_debuglog_short_recording_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        dialog = null
                        vm.forceStopDebugLog()
                    }) {
                        Text(stringResource(R.string.settings_support_debuglog_short_recording_stop))
                    }
                },
            )
        }

        is SupportDialog.DeleteSession -> {
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.support_debuglog_session_delete_title)) },
                text = { Text(stringResource(R.string.support_debuglog_session_delete_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        vm.deleteSession(d.id)
                    }) {
                        Text(stringResource(R.string.general_delete_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = null }) {
                        Text(stringResource(R.string.general_cancel_action))
                    }
                },
            )
        }

        is SupportDialog.ClearAll -> {
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.support_debuglog_clear_title)) },
                text = { Text(stringResource(R.string.support_debuglog_clear_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        vm.clearDebugLogs()
                    }) {
                        Text(stringResource(R.string.general_delete_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = null }) {
                        Text(stringResource(R.string.general_cancel_action))
                    }
                },
            )
        }

        null -> {}
    }

    state?.let { vmState ->
        SupportScreen(
            state = vmState,
            onNavigateUp = { vm.navUp() },
            onOpenUrl = { vm.openUrl(it) },
            onContactDeveloper = { vm.contactDeveloper() },
            onDebugLogToggle = { vm.onDebugLogToggle() },
            onOpenSession = { vm.openSession(it) },
            onDeleteSession = { dialog = SupportDialog.DeleteSession(it) },
            onStopRecording = { vm.onDebugLogToggle() },
            onClearLogs = { dialog = SupportDialog.ClearAll },
        )
    }
}

@Composable
fun SupportScreen(
    state: SupportScreenViewModel.State,
    onNavigateUp: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onContactDeveloper: () -> Unit,
    onDebugLogToggle: () -> Unit,
    onOpenSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStopRecording: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showSessionsSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_support_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.general_back_action),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { paddingValues ->
        val navBarPadding = navigationBarBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .horizontalCutoutPadding(),
            contentPadding = PaddingValues(bottom = navBarPadding),
            verticalArrangement = Arrangement.Top,
        ) {
            item {
                SettingsPreferenceItem(
                    icon = Icons.Filled.Book,
                    title = stringResource(R.string.settings_support_wiki_label),
                    subtitle = stringResource(R.string.settings_support_wiki_description),
                    onClick = { onOpenUrl(BlueMusicLinks.WIKI) },
                )
                SettingsDivider()
            }

            item {
                SettingsPreferenceItem(
                    icon = Icons.Filled.BugReport,
                    title = stringResource(R.string.settings_support_issue_tracker_label),
                    subtitle = stringResource(R.string.settings_support_issue_tracker_description),
                    onClick = { onOpenUrl(BlueMusicLinks.ISSUES) },
                )
                SettingsDivider()
            }

            item {
                SettingsPreferenceItem(
                    icon = Icons.Filled.Discord,
                    title = stringResource(R.string.settings_support_discord_label),
                    subtitle = stringResource(R.string.settings_support_discord_description),
                    onClick = { onOpenUrl("https://discord.gg/PZR7M8p") },
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
                    icon = if (state.isRecording) Icons.TwoTone.Cancel else Icons.Filled.BugReport,
                    title = if (state.isRecording) {
                        stringResource(R.string.debug_log_stop_action)
                    } else {
                        stringResource(R.string.debug_log_record_action)
                    },
                    subtitle = if (state.isRecording) {
                        state.currentLogPath?.path
                    } else {
                        stringResource(R.string.settings_support_debuglog_desc)
                    },
                    onClick = onDebugLogToggle,
                )
                SettingsDivider()
            }

            if (state.sessions.isNotEmpty()) {
                item {
                    val nonRecordingSessions = state.logSessionCount
                    val logSizeFormatted = Formatter.formatShortFileSize(context, state.logFolderSize)
                    SettingsPreferenceItem(
                        icon = Icons.Filled.BugReport,
                        title = stringResource(R.string.settings_support_stored_logs_label),
                        subtitle = if (nonRecordingSessions > 0) {
                            stringResource(
                                R.string.settings_support_stored_logs_desc,
                                nonRecordingSessions,
                                logSizeFormatted,
                            )
                        } else {
                            stringResource(R.string.settings_support_stored_logs_empty)
                        },
                        onClick = { showSessionsSheet = true },
                    )
                }
            }
        }
    }

    if (showSessionsSheet) {
        DebugSessionsBottomSheet(
            sessions = state.sessions,
            onDismiss = { showSessionsSheet = false },
            onOpenSession = onOpenSession,
            onDeleteSession = onDeleteSession,
            onStopRecording = onStopRecording,
            onClearAll = {
                showSessionsSheet = false
                onClearLogs()
            },
        )
    }
}

@Composable
private fun DebugSessionsBottomSheet(
    sessions: List<DebugSession>,
    onDismiss: () -> Unit,
    onOpenSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStopRecording: () -> Unit,
    onClearAll: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_support_stored_logs_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (sessions.any { it !is DebugSession.Recording }) {
                    IconButton(onClick = onClearAll) {
                        Icon(
                            imageVector = Icons.TwoTone.Delete,
                            contentDescription = stringResource(R.string.general_clear_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_support_stored_logs_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            } else {
                sessions.forEach { session ->
                    SessionRow(
                        session = session,
                        context = context,
                        onOpen = { onOpenSession(session.id) },
                        onDelete = { onDeleteSession(session.id) },
                        onStop = onStopRecording,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: DebugSession,
    context: android.content.Context,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (session is DebugSession.Ready) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (session) {
            is DebugSession.Recording -> Icon(
                imageVector = Icons.TwoTone.FiberManualRecord,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error,
            )

            is DebugSession.Compressing -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )

            is DebugSession.Ready -> Icon(
                imageVector = Icons.TwoTone.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            is DebugSession.Failed -> Icon(
                imageVector = Icons.TwoTone.Warning,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val agoText = DateUtils.getRelativeTimeSpanString(
                session.createdAt.toEpochMilli(),
                System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            )
            Text(
                text = when (session) {
                    is DebugSession.Recording -> stringResource(R.string.support_debuglog_session_recording)
                    is DebugSession.Compressing -> stringResource(R.string.support_debuglog_session_compressing)
                    is DebugSession.Ready -> "${Formatter.formatShortFileSize(context, session.diskSize)} \u00B7 $agoText"
                    is DebugSession.Failed -> when (session.reason) {
                        DebugSession.Failed.Reason.EMPTY_LOG -> stringResource(R.string.support_debuglog_failed_empty_log)
                        DebugSession.Failed.Reason.MISSING_LOG -> stringResource(R.string.support_debuglog_failed_missing_log)
                        DebugSession.Failed.Reason.CORRUPT_ZIP -> stringResource(R.string.support_debuglog_failed_corrupt_zip)
                        DebugSession.Failed.Reason.ZIP_FAILED -> stringResource(R.string.support_debuglog_failed_zip_failed)
                    } + " \u00B7 $agoText"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (session) {
            is DebugSession.Recording -> {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.TwoTone.Cancel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is DebugSession.Compressing -> {}

            is DebugSession.Ready, is DebugSession.Failed -> {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.TwoTone.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            state = SupportScreenViewModel.State(),
            onNavigateUp = {},
            onOpenUrl = {},
            onContactDeveloper = {},
            onDebugLogToggle = {},
            onOpenSession = {},
            onDeleteSession = {},
            onStopRecording = {},
            onClearLogs = {},
        )
    }
}
