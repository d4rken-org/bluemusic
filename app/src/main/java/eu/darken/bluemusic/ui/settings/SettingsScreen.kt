package eu.darken.bluemusic.ui.settings

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.ui.theme.BlueMusicTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    var showAutoplayDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.abc_action_bar_up_description)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.label_about)
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
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Premium Features Section
            item {
                SectionHeader(title = stringResource(R.string.label_premium_features))
            }
            
            item {
                SwitchPreference(
                    title = stringResource(R.string.label_visible_volume_adjustments),
                    description = if (state.isProVersion) {
                        stringResource(R.string.description_visible_volume_adjustments)
                    } else {
                        stringResource(R.string.description_visible_volume_adjustments) + "\n[${stringResource(R.string.label_premium_version_required)}]"
                    },
                    isChecked = state.visibleAdjustments,
                    leadingIcon = if (!state.isProVersion) Icons.Default.Star else null,
                    onCheckedChange = { onEvent(SettingsEvent.OnVisibleAdjustmentsToggled) }
                )
            }
            
            item {
                SwitchPreference(
                    title = stringResource(R.string.label_speaker_autosave),
                    description = if (state.isProVersion) {
                        stringResource(R.string.description_speaker_autosave)
                    } else {
                        stringResource(R.string.description_speaker_autosave) + "\n[${stringResource(R.string.label_premium_version_required)}]"
                    },
                    isChecked = state.speakerAutosave,
                    leadingIcon = if (!state.isProVersion) Icons.Default.Star else null,
                    onCheckedChange = { onEvent(SettingsEvent.OnSpeakerAutosaveToggled) }
                )
            }
            
            // Autoplay Section
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(title = stringResource(R.string.label_autoplay))
            }
            
            item {
                ClickablePreference(
                    title = stringResource(R.string.label_autoplay_keycode),
                    description = when (state.autoplayKeycode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> stringResource(R.string.label_keyevent_playpause)
                        KeyEvent.KEYCODE_MEDIA_PLAY -> stringResource(R.string.label_keyevent_play)
                        KeyEvent.KEYCODE_MEDIA_NEXT -> stringResource(R.string.label_keyevent_next)
                        else -> stringResource(R.string.label_keyevent_playpause)
                    },
                    onClick = { showAutoplayDialog = true }
                )
            }
            
            // Developer Options Section
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                SectionHeader(title = stringResource(R.string.label_developer))
            }
            
            item {
                SwitchPreference(
                    title = stringResource(R.string.label_bugreporting),
                    description = if (state.bugreportingEnabled) ":)" else ":(",
                    isChecked = state.bugreportingEnabled,
                    onCheckedChange = { onEvent(SettingsEvent.OnBugreportingToggled(it)) }
                )
            }
            
            // Advanced Settings
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
            
            item {
                ClickablePreference(
                    title = stringResource(R.string.label_advanced),
                    description = stringResource(R.string.description_advanced_settings),
                    onClick = onNavigateToAdvanced
                )
            }
        }
    }
    
    // Dialogs
    if (showAutoplayDialog) {
        AutoplayKeycodeDialog(
            currentKeycode = state.autoplayKeycode,
            onKeycodeSelected = { keycode ->
                onEvent(SettingsEvent.OnAutoplayKeycodeSelected(keycode))
                showAutoplayDialog = false
            },
            onDismiss = { showAutoplayDialog = false }
        )
    }
    
    if (state.showUpgradeDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(SettingsEvent.OnDismissDialog) },
            icon = { Icon(Icons.Default.Star, contentDescription = null) },
            title = { Text(stringResource(R.string.label_premium_version)) },
            text = { Text(stringResource(R.string.description_premium_required_this_extra_option)) },
            confirmButton = {
                TextButton(onClick = { /* Handled by ScreenHost */ }) {
                    Text(stringResource(R.string.action_upgrade))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(SettingsEvent.OnDismissDialog) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SwitchPreference(
    title: String,
    description: String,
    isChecked: Boolean,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = modifier
    )
}

@Composable
private fun ClickablePreference(
    title: String,
    description: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        modifier = modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun AutoplayKeycodeDialog(
    currentKeycode: Int,
    onKeycodeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val keycodes = listOf(
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to stringResource(R.string.label_keyevent_playpause),
        KeyEvent.KEYCODE_MEDIA_PLAY to stringResource(R.string.label_keyevent_play),
        KeyEvent.KEYCODE_MEDIA_NEXT to stringResource(R.string.label_keyevent_next)
    )
    
    var selectedKeycode by remember { mutableStateOf(currentKeycode) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_autoplay_keycode)) },
        text = {
            Column {
                keycodes.forEach { (keycode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedKeycode = keycode }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedKeycode == keycode,
                            onClick = { selectedKeycode = keycode }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onKeycodeSelected(selectedKeycode)
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    BlueMusicTheme {
        SettingsScreen(
            state = SettingsState(
                isProVersion = true,
                bugreportingEnabled = true,
                visibleAdjustments = true,
                speakerAutosave = false,
                autoplayKeycode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            ),
            onEvent = {},
            onNavigateBack = {},
            onNavigateToAdvanced = {},
            onNavigateToAbout = {}
        )
    }
}