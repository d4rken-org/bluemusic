package eu.darken.bluemusic.main.ui.settings.support.contact

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import eu.darken.bluemusic.common.compose.navigationBarBottomPadding
import eu.darken.bluemusic.common.debug.recorder.core.DebugLogStore
import eu.darken.bluemusic.common.error.ErrorEventHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContactSupportScreenHost(vm: ContactSupportViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState(null)
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm.sendEvent) {
        vm.sendEvent.collect { intent ->
            context.startActivity(intent)
        }
    }

    LaunchedEffect(vm.snackbarEvent) {
        vm.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    state?.let { currentState ->
        ContactSupportScreen(
            state = currentState,
            snackbarHostState = snackbarHostState,
            onNavigateUp = { vm.navUp() },
            onCategorySelected = { vm.selectCategory(it) },
            onDescriptionChanged = { vm.updateDescription(it) },
            onLogSessionSelected = { vm.selectLogSession(it) },
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
    onLogSessionSelected: (DebugLogStore.LogSession?) -> Unit,
    onSend: () -> Unit,
) {
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onSend,
                modifier = Modifier.navigationBarsPadding(),
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                    )
                },
                text = { Text(stringResource(R.string.contact_send_action)) },
                containerColor = if (state.canSend) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (state.canSend) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.statusBars,
    ) { paddingValues ->
        val navBarPadding = navigationBarBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .horizontalCutoutPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 80.dp + navBarPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ContactCategory.entries.forEach { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { onCategorySelected(category) },
                            label = { Text(stringResource(category.labelRes)) },
                        )
                    }
                }
            }

            item {
                Column {
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = onDescriptionChanged,
                        label = { Text(stringResource(R.string.contact_description_label)) },
                        placeholder = {
                            val hintRes = state.category?.hintRes
                            if (hintRes != null) Text(stringResource(hintRes))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 10,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    WordCountIndicator(
                        wordCount = state.descriptionWordCount,
                        minimum = 20,
                    )
                }
            }

            if (state.category == ContactCategory.BUG) {
                item {
                    LogSessionPicker(
                        sessions = state.logSessions,
                        selectedSession = state.selectedLogSession,
                        onSessionSelected = onLogSessionSelected,
                    )
                }
            }

            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.contact_welcome_msg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.contact_footer_msg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WordCountIndicator(
    wordCount: Int,
    minimum: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        val color = when {
            wordCount == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
            wordCount < minimum -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }
        Text(
            text = stringResource(R.string.contact_word_count, wordCount, minimum),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun LogSessionPicker(
    sessions: List<DebugLogStore.LogSession>,
    selectedSession: DebugLogStore.LogSession?,
    onSessionSelected: (DebugLogStore.LogSession?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Column {
        Text(
            text = stringResource(R.string.contact_debug_log_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.contact_debug_log_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = selectedSession?.let {
                        val date = dateFormat.format(Date(it.timestamp))
                        val size = Formatter.formatShortFileSize(context, it.totalSize)
                        "$date ($size)"
                    } ?: stringResource(R.string.contact_debug_log_select_hint),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    sessions.forEach { session ->
                        val date = dateFormat.format(Date(session.timestamp))
                        val size = Formatter.formatShortFileSize(context, session.totalSize)
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(date)
                                    Text(
                                        text = "${session.fileCount} files ($size)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                onSessionSelected(session)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
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
                logSessions = listOf(
                    DebugLogStore.LogSession(
                        dir = File("/tmp/session1"),
                        zipFile = File("/tmp/session1.zip"),
                        timestamp = System.currentTimeMillis(),
                        fileCount = 3,
                        totalSize = 1024 * 512,
                    )
                ),
            ),
            onNavigateUp = {},
            onCategorySelected = {},
            onDescriptionChanged = {},
            onLogSessionSelected = {},
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
            onLogSessionSelected = {},
            onSend = {},
        )
    }
}
