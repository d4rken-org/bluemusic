package eu.darken.bluemusic.main.ui.settings.support.contact

import android.content.ActivityNotFoundException
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.Email
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.BlueMusicLinks
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import eu.darken.bluemusic.common.debug.recorder.ui.RecorderConsentDialog
import eu.darken.bluemusic.common.error.ErrorEventHandler

private sealed interface ContactDialog {
    data object SentConfirm : ContactDialog
    data object Consent : ContactDialog
    data object ShortRecordingWarning : ContactDialog
    data class DeleteSession(val sessionId: String) : ContactDialog
}

@Composable
fun ContactSupportScreenHost(vm: ContactSupportViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasSentEmail by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ContactDialog?>(null) }

    LifecycleResumeEffect(hasSentEmail) {
        vm.refreshLogSessions()
        if (hasSentEmail) {
            dialog = ContactDialog.SentConfirm
            hasSentEmail = false
        }
        onPauseOrDispose {}
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is ContactSupportViewModel.Event.OpenEmail -> {
                    try {
                        hasSentEmail = true
                        context.startActivity(event.intent)
                    } catch (_: ActivityNotFoundException) {
                        hasSentEmail = false
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.contact_no_email_app_msg)
                        )
                    }
                }

                is ContactSupportViewModel.Event.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }

                ContactSupportViewModel.Event.ShowConsentDialog -> {
                    dialog = ContactDialog.Consent
                }

                ContactSupportViewModel.Event.ShowShortRecordingWarning -> {
                    dialog = ContactDialog.ShortRecordingWarning
                }
            }
        }
    }

    when (val d = dialog) {
        is ContactDialog.SentConfirm -> {
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.support_debuglog_sent_title)) },
                text = { Text(stringResource(R.string.support_debuglog_sent_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        vm.confirmSent()
                    }) {
                        Text(stringResource(R.string.general_ok_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialog = null }) {
                        Text(stringResource(R.string.general_cancel_action))
                    }
                },
            )
        }

        is ContactDialog.Consent -> {
            RecorderConsentDialog(
                onStartRecord = {
                    vm.doStartRecording()
                },
                onOpenPrivacyPolicy = { vm.openUrl(BlueMusicLinks.PRIVACY_POLICY) },
                onDismiss = { dialog = null },
            )
        }

        is ContactDialog.ShortRecordingWarning -> {
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
                        vm.forceStopRecording()
                    }) {
                        Text(stringResource(R.string.settings_support_debuglog_short_recording_stop))
                    }
                },
            )
        }

        is ContactDialog.DeleteSession -> {
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(stringResource(R.string.support_debuglog_session_delete_title)) },
                text = { Text(stringResource(R.string.support_debuglog_session_delete_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        vm.deleteLogSession(d.sessionId)
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

    val state by vm.state.collectAsStateWithLifecycle(null)
    state?.let { currentState ->
        ContactSupportScreen(
            state = currentState,
            snackbarHostState = snackbarHostState,
            onNavigateUp = { vm.navUp() },
            onCategorySelected = { vm.selectCategory(it) },
            onDescriptionChanged = { vm.updateDescription(it) },
            onExpectedChanged = { vm.updateExpectedBehavior(it) },
            onSelectSession = { vm.selectLogSession(it) },
            onDeleteSession = { id -> dialog = ContactDialog.DeleteSession(id) },
            onStartRecording = { vm.startRecording() },
            onStopRecording = { vm.stopRecording() },
            onSend = { vm.send() },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactSupportScreen(
    state: ContactSupportViewModel.State,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit,
    onCategorySelected: (ContactCategory) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onExpectedChanged: (String) -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSend: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contact_screen_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .horizontalCutoutPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Category
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(
                        icon = {
                            Icon(
                                Icons.TwoTone.Description,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        title = stringResource(R.string.contact_category_section_label),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ContactCategory.entries.forEach { category ->
                            FilterChip(
                                selected = state.category == category,
                                onClick = { onCategorySelected(category) },
                                label = { Text(stringResource(category.labelRes)) },
                            )
                        }
                    }
                }
            }

            // Debug log (bug only)
            if (state.isBug) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(
                            icon = {
                                Icon(
                                    Icons.TwoTone.BugReport,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            title = stringResource(R.string.contact_debug_log_label),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.contact_debug_log_picker_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (state.sessions.isEmpty() && !state.isRecording) {
                            Text(
                                text = stringResource(R.string.contact_debug_log_picker_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            state.sessions.forEach { session ->
                                val isSelected = state.selectedSessionId == session.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSelectSession(session.id) },
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 4.dp),
                                    ) {
                                        Text(
                                            text = session.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        val sizeText = Formatter.formatShortFileSize(context, session.diskSize)
                                        val agoText = DateUtils.getRelativeTimeSpanString(
                                            session.createdAt.toEpochMilli(),
                                            System.currentTimeMillis(),
                                            DateUtils.SECOND_IN_MILLIS,
                                            DateUtils.FORMAT_ABBREV_RELATIVE,
                                        )
                                        Text(
                                            text = "$sizeText \u00B7 $agoText",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { onDeleteSession(session.id) }) {
                                        Icon(
                                            Icons.TwoTone.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            if (state.isRecording) {
                                FilledTonalButton(onClick = onStopRecording) {
                                    Icon(Icons.TwoTone.Cancel, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.debug_log_stop_action))
                                }
                            } else {
                                FilledTonalButton(onClick = onStartRecording) {
                                    Icon(Icons.TwoTone.BugReport, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.debug_log_record_action))
                                }
                            }
                        }
                    }
                }
            }

            // Description
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = onDescriptionChanged,
                        label = { Text(stringResource(R.string.contact_description_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    WordCountIndicator(
                        wordCount = state.descriptionWordCount,
                        minimum = 20,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(state.category.hintRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Expected behavior (bug only)
            if (state.isBug) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = state.expectedBehavior,
                            onValueChange = onExpectedChanged,
                            label = { Text(stringResource(R.string.contact_expected_behavior_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        WordCountIndicator(
                            wordCount = state.expectedWordCount,
                            minimum = 10,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.contact_expected_behavior_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Personal note
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        Icons.TwoTone.Info,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.contact_welcome_msg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Send button
            androidx.compose.material3.Button(
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSend,
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.TwoTone.Email, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.contact_send_action))
            }

            // Footer
            Text(
                text = stringResource(R.string.contact_footer_msg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    icon: @Composable () -> Unit,
    title: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun WordCountIndicator(
    wordCount: Int,
    minimum: Int,
) {
    val color = when {
        wordCount == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        wordCount < minimum -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = pluralStringResource(R.plurals.contact_word_count, wordCount, wordCount, minimum),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Preview2
@Composable
private fun ContactSupportScreenPreview() {
    PreviewWrapper {
        ContactSupportScreen(
            state = ContactSupportViewModel.State(
                category = ContactCategory.BUG,
                description = "The app crashes when I connect my headphones and try to adjust the volume",
            ),
            onNavigateUp = {},
            onCategorySelected = {},
            onDescriptionChanged = {},
            onExpectedChanged = {},
            onSelectSession = {},
            onDeleteSession = {},
            onStartRecording = {},
            onStopRecording = {},
            onSend = {},
        )
    }
}

@Preview2
@Composable
private fun ContactSupportScreenEmptyPreview() {
    PreviewWrapper {
        ContactSupportScreen(
            state = ContactSupportViewModel.State(),
            onNavigateUp = {},
            onCategorySelected = {},
            onDescriptionChanged = {},
            onExpectedChanged = {},
            onSelectSession = {},
            onDeleteSession = {},
            onStartRecording = {},
            onStopRecording = {},
            onSend = {},
        )
    }
}
