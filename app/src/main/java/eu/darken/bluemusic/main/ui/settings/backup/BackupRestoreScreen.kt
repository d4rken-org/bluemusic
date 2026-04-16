package eu.darken.bluemusic.main.ui.settings.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Backup
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import eu.darken.bluemusic.common.compose.navigationBarBottomPadding
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.main.backup.core.AppBackup

@Composable
fun BackupRestoreScreenHost(vm: BackupRestoreViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            log(vm.tag, INFO) { "Backup URI selected: $it" }
            vm.onBackupUriSelected(it)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            log(vm.tag, INFO) { "Restore URI selected: $it" }
            vm.onRestoreUriSelected(it)
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is BackupRestoreEvent.OpenBackupPicker -> backupLauncher.launch(event.suggestedName)
                is BackupRestoreEvent.OpenRestorePicker -> restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            }
        }
    }

    val state by vm.state.collectAsStateWithLifecycle()

    state?.let { s ->
        BackupRestoreScreen(
            state = s,
            onNavigateUp = { vm.navUp() },
            onBackupClicked = { vm.onBackupClicked() },
            onRestoreClicked = { vm.onRestoreClicked() },
            onToggleSkipExisting = { vm.onToggleSkipExisting(it) },
            onConfirmRestore = { vm.onConfirmRestore() },
            onCancelRestore = { vm.onCancelRestore() },
        )
    }
}

@Composable
fun BackupRestoreScreen(
    state: BackupRestoreViewModel.State,
    onNavigateUp: () -> Unit,
    onBackupClicked: () -> Unit,
    onRestoreClicked: () -> Unit,
    onToggleSkipExisting: (Boolean) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.general_back_action)
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { paddingValues ->
        val navBarPadding = navigationBarBottomPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalCutoutPadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = navBarPadding + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { BackupSection(isWorking = state.isWorking, onBackupClicked = onBackupClicked) }

                item {
                    RestoreSection(
                        isWorking = state.isWorking,
                        skipExisting = state.skipExisting,
                        hasPendingPreview = state.pendingRestore != null,
                        onRestoreClicked = onRestoreClicked,
                        onToggleSkipExisting = onToggleSkipExisting,
                    )
                }

                state.pendingRestore?.let { preview ->
                    item {
                        RestorePreviewSection(
                            preview = preview,
                            isWorking = state.isWorking,
                            onConfirmRestore = onConfirmRestore,
                            onCancelRestore = onCancelRestore,
                        )
                    }
                }

                state.lastResult?.let { result ->
                    item { ResultSection(result = result) }
                }
            }
        }
    }
}

@Composable
private fun BackupSection(
    isWorking: Boolean,
    onBackupClicked: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = stringResource(R.string.backup_restore_backup_action),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.backup_restore_backup_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onBackupClicked,
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_restore_backup_action))
            }
        }
    }
}

@Composable
private fun RestoreSection(
    isWorking: Boolean,
    skipExisting: Boolean,
    hasPendingPreview: Boolean,
    onRestoreClicked: () -> Unit,
    onToggleSkipExisting: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = stringResource(R.string.backup_restore_restore_action),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.backup_restore_restore_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.backup_restore_skip_existing_label),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.backup_restore_skip_existing_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = skipExisting,
                    onCheckedChange = onToggleSkipExisting,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRestoreClicked,
                enabled = !isWorking && !hasPendingPreview,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_restore_restore_action))
            }
        }
    }
}

@Composable
private fun RestorePreviewSection(
    preview: BackupRestoreViewModel.RestorePreview,
    isWorking: Boolean,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.backup_restore_preview_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = preview.fileName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "v${preview.backup.appVersion} — ${preview.backup.createdAt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.backup_restore_preview_devices, preview.deviceCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (preview.existingDeviceCount > 0) {
                Text(
                    text = stringResource(
                        R.string.backup_restore_preview_overlap,
                        preview.existingDeviceCount,
                        preview.deviceCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }

            if (preview.versionMismatch || preview.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (preview.versionMismatch) {
                WarningCard(
                    text = stringResource(
                        R.string.backup_restore_version_warning,
                        preview.backup.appVersion,
                        eu.darken.bluemusic.common.BuildConfigWrap.VERSION_NAME,
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            preview.warnings.forEach { warning ->
                WarningCard(text = warning)
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onCancelRestore,
                    modifier = Modifier.weight(1f),
                    enabled = !isWorking,
                ) {
                    Text(stringResource(R.string.general_cancel_action))
                }
                Button(
                    onClick = onConfirmRestore,
                    modifier = Modifier.weight(1f),
                    enabled = !isWorking,
                ) {
                    Text(stringResource(R.string.backup_restore_confirm_action))
                }
            }
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.TwoTone.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun ResultSection(result: BackupRestoreViewModel.OperationResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = result.fileName,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val summary = when (result.type) {
                BackupRestoreViewModel.OperationType.BACKUP -> stringResource(
                    R.string.backup_restore_success_backup,
                    result.deviceCount,
                )

                BackupRestoreViewModel.OperationType.RESTORE -> stringResource(
                    R.string.backup_restore_success_restore,
                    result.deviceCount,
                    result.skippedCount,
                )
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Preview2
@Composable
private fun BackupRestoreScreenIdlePreview() {
    PreviewWrapper {
        BackupRestoreScreen(
            state = BackupRestoreViewModel.State(),
            onNavigateUp = {},
            onBackupClicked = {},
            onRestoreClicked = {},
            onToggleSkipExisting = {},
            onConfirmRestore = {},
            onCancelRestore = {},
        )
    }
}

@Preview2
@Composable
private fun BackupRestoreScreenPreviewPreview() {
    PreviewWrapper {
        BackupRestoreScreen(
            state = BackupRestoreViewModel.State(
                pendingRestore = BackupRestoreViewModel.RestorePreview(
                    fileUri = Uri.EMPTY,
                    fileName = "bluemusic-backup-2026-04-16.zip",
                    backup = AppBackup(
                        formatVersion = 1,
                        appVersion = "3.2.0",
                        appVersionCode = 32000L,
                        createdAt = "2026-04-15T10:30:00Z",
                    ),
                    versionMismatch = true,
                    deviceCount = 5,
                    existingDeviceCount = 2,
                    warnings = listOf("Unknown DnD mode 'future_mode' for device AA:BB:CC:DD:EE:FF, will be ignored"),
                ),
            ),
            onNavigateUp = {},
            onBackupClicked = {},
            onRestoreClicked = {},
            onToggleSkipExisting = {},
            onConfirmRestore = {},
            onCancelRestore = {},
        )
    }
}

@Preview2
@Composable
private fun BackupRestoreScreenResultPreview() {
    PreviewWrapper {
        BackupRestoreScreen(
            state = BackupRestoreViewModel.State(
                lastResult = BackupRestoreViewModel.OperationResult(
                    type = BackupRestoreViewModel.OperationType.RESTORE,
                    deviceCount = 5,
                    skippedCount = 2,
                    fileName = "bluemusic-backup-2026-04-16.zip",
                ),
            ),
            onNavigateUp = {},
            onBackupClicked = {},
            onRestoreClicked = {},
            onToggleSkipExisting = {},
            onConfirmRestore = {},
            onCancelRestore = {},
        )
    }
}
