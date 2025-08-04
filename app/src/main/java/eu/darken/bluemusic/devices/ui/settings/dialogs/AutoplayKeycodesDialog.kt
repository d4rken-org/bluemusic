package eu.darken.bluemusic.devices.ui.settings.dialogs

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.PreviewWrapper

data class KeycodeOption(
    val keycode: Int,
    val name: String,
    val description: String
)

@Composable
fun AutoplayKeycodesDialog(
    currentKeycodes: List<Int>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val availableKeycodes = remember {
        listOf(
            KeycodeOption(
                KeyEvent.KEYCODE_MEDIA_PLAY,
                "Play",
                "Sends play command only"
            ),
            KeycodeOption(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                "Play/Pause",
                "Toggles between play and pause"
            ),
            KeycodeOption(
                KeyEvent.KEYCODE_MEDIA_NEXT,
                "Next",
                "Skip to next track"
            ),
            KeycodeOption(
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                "Previous",
                "Go to previous track"
            ),
            KeycodeOption(
                KeyEvent.KEYCODE_MEDIA_STOP,
                "Stop",
                "Stop playback"
            ),
            KeycodeOption(
                KeyEvent.KEYCODE_MEDIA_REWIND,
                "Rewind",
                "Rewind current track"
            ),
            KeycodeOption(
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                "Fast Forward",
                "Fast forward current track"
            )
        )
    }

    var selectedKeycodes by remember { mutableStateOf(currentKeycodes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.devices_device_config_autoplay_keycodes_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Available keycodes section
                Text(
                    text = stringResource(R.string.devices_device_config_autoplay_keycodes_available),
                    fontWeight = FontWeight.Medium
                )
                LazyColumn(
                    modifier = Modifier.weight(1f, false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableKeycodes) { keycodeOption ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedKeycodes.contains(keycodeOption.keycode),
                                onCheckedChange = { checked ->
                                    selectedKeycodes = if (checked) {
                                        selectedKeycodes + keycodeOption.keycode
                                    } else {
                                        selectedKeycodes - keycodeOption.keycode
                                    }
                                }
                            )
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = keycodeOption.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = keycodeOption.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (selectedKeycodes.isNotEmpty()) {
                    HorizontalDivider()

                    // Selected keycodes order section
                    Text(
                        text = stringResource(R.string.devices_device_config_autoplay_keycodes_order),
                        fontWeight = FontWeight.Medium
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f, false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(selectedKeycodes) { keycode ->
                            val keycodeOption = availableKeycodes.find { it.keycode == keycode }
                            if (keycodeOption != null) {
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
                                            text = keycodeOption.name,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    val index = selectedKeycodes.indexOf(keycode)
                                                    if (index > 0) {
                                                        selectedKeycodes = selectedKeycodes.toMutableList().apply {
                                                            removeAt(index)
                                                            add(index - 1, keycode)
                                                        }
                                                    }
                                                },
                                                enabled = selectedKeycodes.indexOf(keycode) > 0
                                            ) {
                                                Icon(
                                                    Icons.Default.ArrowUpward,
                                                    contentDescription = "Move up"
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    val index = selectedKeycodes.indexOf(keycode)
                                                    if (index < selectedKeycodes.size - 1) {
                                                        selectedKeycodes = selectedKeycodes.toMutableList().apply {
                                                            removeAt(index)
                                                            add(index + 1, keycode)
                                                        }
                                                    }
                                                },
                                                enabled = selectedKeycodes.indexOf(keycode) < selectedKeycodes.size - 1
                                            ) {
                                                Icon(
                                                    Icons.Default.ArrowDownward,
                                                    contentDescription = "Move down"
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    selectedKeycodes = selectedKeycodes - keycode
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Remove"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedKeycodes)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.action_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Preview
@Composable
private fun AutoplayKeycodesDialogPreview() {
    PreviewWrapper {
        AutoplayKeycodesDialog(
            currentKeycodes = listOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_NEXT),
            onConfirm = {},
            onDismiss = {}
        )
    }
}