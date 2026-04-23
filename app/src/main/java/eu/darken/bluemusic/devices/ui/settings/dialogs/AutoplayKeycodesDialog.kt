package eu.darken.bluemusic.devices.ui.settings.dialogs

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.devices.ui.AutoplayKeycodeOption
import eu.darken.bluemusic.devices.ui.AutoplayKeycodes

private const val MAX_AUTOPLAY_KEYCODES = 10

@Composable
fun AutoplayKeycodesDialog(
    currentKeycodes: List<Int>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedKeycodes by remember { mutableStateOf(currentKeycodes) }
    var showPicker by remember { mutableStateOf(false) }
    val atCap = selectedKeycodes.size >= MAX_AUTOPLAY_KEYCODES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.devices_device_config_autoplay_keycodes_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.devices_device_config_autoplay_keycodes_order),
                    fontWeight = FontWeight.Medium
                )

                if (selectedKeycodes.isEmpty()) {
                    Text(
                        text = stringResource(R.string.devices_device_config_autoplay_keycodes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(selectedKeycodes) { index, keycode ->
                            SelectedKeycodeRow(
                                position = index + 1,
                                option = AutoplayKeycodes.resolve(keycode),
                                canMoveUp = index > 0,
                                canMoveDown = index < selectedKeycodes.size - 1,
                                onMoveUp = {
                                    selectedKeycodes = selectedKeycodes.toMutableList().apply {
                                        removeAt(index)
                                        add(index - 1, keycode)
                                    }
                                },
                                onMoveDown = {
                                    selectedKeycodes = selectedKeycodes.toMutableList().apply {
                                        removeAt(index)
                                        add(index + 1, keycode)
                                    }
                                },
                                onRemove = {
                                    selectedKeycodes = selectedKeycodes.toMutableList().apply {
                                        removeAt(index)
                                    }
                                }
                            )
                        }
                    }
                }

                TextButton(
                    onClick = { showPicker = true },
                    enabled = !atCap,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.devices_device_config_autoplay_keycodes_add_action))
                }
                if (atCap) {
                    Text(
                        text = stringResource(R.string.devices_device_config_autoplay_keycodes_max_reached, MAX_AUTOPLAY_KEYCODES),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(selectedKeycodes)
                onDismiss()
            }) {
                Text(stringResource(R.string.action_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    if (showPicker) {
        AutoplayKeycodePickerDialog(
            onPick = { keycode ->
                if (selectedKeycodes.size < MAX_AUTOPLAY_KEYCODES) {
                    selectedKeycodes = selectedKeycodes + keycode
                }
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun SelectedKeycodeRow(
    position: Int,
    option: AutoplayKeycodeOption,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$position.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = option.label(),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = stringResource(R.string.cd_autoplay_keycode_move_up)
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = stringResource(R.string.cd_autoplay_keycode_move_down)
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_autoplay_keycode_remove)
                )
            }
        }
    }
}

@Composable
private fun AutoplayKeycodePickerDialog(
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.devices_device_config_autoplay_keycodes_add_action)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(AutoplayKeycodes.knownCodes) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(option.keycode) }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label(),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = option.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Preview2
@Composable
private fun AutoplayKeycodesDialogPreview() {
    PreviewWrapper {
        AutoplayKeycodesDialog(
            currentKeycodes = listOf(
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_NEXT,
            ),
            onConfirm = {},
            onDismiss = {}
        )
    }
}

@Preview2
@Composable
private fun AutoplayKeycodesDialogEmptyPreview() {
    PreviewWrapper {
        AutoplayKeycodesDialog(
            currentKeycodes = emptyList(),
            onConfirm = {},
            onDismiss = {}
        )
    }
}

@Preview2
@Composable
private fun AutoplayKeycodePickerDialogPreview() {
    PreviewWrapper {
        AutoplayKeycodePickerDialog(
            onPick = {},
            onDismiss = {}
        )
    }
}
