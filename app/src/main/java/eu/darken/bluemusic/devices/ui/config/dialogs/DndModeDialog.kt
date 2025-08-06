package eu.darken.bluemusic.devices.ui.config.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.monitor.core.audio.DndMode

@Composable
fun DndModeDialog(
    currentMode: DndMode?,
    onConfirm: (DndMode?) -> Unit,
    onDismiss: () -> Unit
) {
    val dndModes = listOf(
        null to stringResource(R.string.dnd_mode_dont_change),
        DndMode.OFF to stringResource(R.string.dnd_mode_off),
        DndMode.PRIORITY_ONLY to stringResource(R.string.dnd_mode_priority_only),
        DndMode.ALARMS_ONLY to stringResource(R.string.dnd_mode_alarms_only),
        DndMode.TOTAL_SILENCE to stringResource(R.string.dnd_mode_total_silence)
    )

    var selectedMode by remember { mutableStateOf(currentMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dnd_mode_dialog_title)) },
        text = {
            LazyColumn {
                items(dndModes) { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = mode }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode }
                        )
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedMode) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.general_cancel_action))
            }
        }
    )
}

@Preview
@Composable
private fun DndModeDialogPreview() {
    PreviewWrapper {
        DndModeDialog(
            currentMode = DndMode.PRIORITY_ONLY,
            onConfirm = {},
            onDismiss = {}
        )
    }
}