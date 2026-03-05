package eu.darken.bluemusic.main.ui.settings.support

import android.text.format.Formatter
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.BlueMusicLinks
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import eu.darken.bluemusic.common.compose.icons.Discord
import eu.darken.bluemusic.common.compose.navigationBarBottomPadding
import eu.darken.bluemusic.common.debug.recorder.core.DebugSessionManager
import eu.darken.bluemusic.common.debug.recorder.core.DebugSessionManager.DebugSession
import eu.darken.bluemusic.common.debug.recorder.ui.RecorderActivity
import eu.darken.bluemusic.common.debug.recorder.ui.RecorderConsentDialog
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.settings.SettingsCategoryHeader
import eu.darken.bluemusic.common.settings.SettingsDivider
import eu.darken.bluemusic.common.settings.SettingsPreferenceItem
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SupportScreenHost(vm: SupportScreenViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val state by vm.state.collectAsStateWithLifecycle(null)
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showShortRecordingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                is SupportScreenViewModel.SupportEvent.ShowShortRecordingWarning -> {
                    showShortRecordingDialog = true
                }

                is SupportScreenViewModel.SupportEvent.OpenSession -> {
                    val intent = RecorderActivity.getLaunchIntent(context, event.path)
                    context.startActivity(intent)
                }
            }
        }
    }

    LaunchedEffect(vm.snackbarEvent) {
        vm.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    state?.let { vmState ->
        SupportScreen(
            state = vmState,
            showShortRecordingDialog = showShortRecordingDialog,
            snackbarHostState = snackbarHostState,
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
            onContactDeveloper = { vm.contactDeveloper() },
            onOpenSession = { vm.openSession(it) },
            onDeleteSession = { vm.deleteSession(it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    state: SupportScreenViewModel.State,
    showShortRecordingDialog: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit,
    onStartDebugLog: () -> Unit,
    onStopDebugLog: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onConfirmStopDebugLog: () -> Unit = {},
    onDismissShortRecordingDialog: () -> Unit = {},
    onContactDeveloper: () -> Unit = {},
    onOpenSession: (DebugSession.Ready) -> Unit = {},
    onDeleteSession: (DebugSession) -> Unit = {},
) {
    var showConsentDialog by remember { mutableStateOf(false) }
    var showSessionsSheet by rememberSaveable { mutableStateOf(false) }

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

    if (showSessionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSessionsSheet = false },
            sheetState = sheetState,
        ) {
            DebugSessionsSheetContent(
                sessions = state.sessions,
                onStopRecording = onStopDebugLog,
                onOpenSession = onOpenSession,
                onDeleteSession = onDeleteSession,
            )
        }
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
                        stringResource(R.string.settings_support_debuglog_recording_desc)
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
                    icon = Icons.Filled.FolderOpen,
                    title = stringResource(R.string.settings_support_stored_logs_label),
                    subtitle = subtitle,
                    onClick = { showSessionsSheet = true }
                )
            }
        }
    }
}

@Composable
private fun DebugSessionsSheetContent(
    sessions: List<DebugSession>,
    onStopRecording: () -> Unit,
    onOpenSession: (DebugSession.Ready) -> Unit,
    onDeleteSession: (DebugSession) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.settings_support_stored_logs_label),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        if (sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_support_stored_logs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
        } else {
            sessions.forEach { session ->
                when (session) {
                    is DebugSession.Recording -> RecordingSessionRow(
                        session = session,
                        onStop = onStopRecording,
                    )

                    is DebugSession.Compressing -> CompressingSessionRow(session = session)

                    is DebugSession.Ready -> ReadySessionRow(
                        session = session,
                        onClick = { onOpenSession(session) },
                        onDelete = { onDeleteSession(session) },
                    )

                    is DebugSession.Failed -> FailedSessionRow(
                        session = session,
                        onDelete = { onDeleteSession(session) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun RecordingSessionRow(
    session: DebugSession.Recording,
    onStop: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recording_alpha",
    )
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.debug_log_recording_progress),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = dateFormat.format(Date(session.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onStop) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = stringResource(R.string.debug_log_stop_action),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CompressingSessionRow(session: DebugSession.Compressing) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.debug_log_compressing_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = dateFormat.format(Date(session.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadySessionRow(
    session: DebugSession.Ready,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.FolderZip,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateFormat.format(Date(session.timestamp)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${session.fileCount} files (${Formatter.formatShortFileSize(context, session.zipSize)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.general_delete_action),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FailedSessionRow(
    session: DebugSession.Failed,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateFormat.format(Date(session.timestamp)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = session.error.message ?: "Compression failed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.general_delete_action),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                sessions = listOf(
                    DebugSession.Recording(
                        id = "session_active",
                        dir = File("/tmp/session_active"),
                        timestamp = System.currentTimeMillis(),
                    ),
                    DebugSession.Ready(
                        id = "session1",
                        dir = File("/tmp/session1"),
                        zipFile = File("/tmp/session1.zip"),
                        timestamp = System.currentTimeMillis() - 3600_000,
                        fileCount = 3,
                        zipSize = 1024 * 512L,
                    ),
                ),
                storedStats = DebugSessionManager.Stats(sessionCount = 1, totalSize = 1024 * 512L),
            ),
            onNavigateUp = {},
            onStartDebugLog = {},
            onStopDebugLog = {},
            onOpenUrl = {},
            onContactDeveloper = {},
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
                storedStats = DebugSessionManager.Stats(sessionCount = 0, totalSize = 0),
            ),
            onNavigateUp = {},
            onStartDebugLog = {},
            onStopDebugLog = {},
            onOpenUrl = {},
            onContactDeveloper = {},
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
            ),
            showShortRecordingDialog = true,
            onNavigateUp = {},
            onStartDebugLog = {},
            onStopDebugLog = {},
            onOpenUrl = {},
        )
    }
}
