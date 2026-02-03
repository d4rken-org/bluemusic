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
import eu.darken.bluemusic.common.compose.Preview2
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.monitor.core.alert.AlertType

@Composable
fun ConnectionAlertDialog(
    currentType: AlertType,
    onConfirm: (AlertType) -> Unit,
    onDismiss: () -> Unit
) {
    val alertTypes = listOf(
        AlertType.NONE to stringResource(R.string.connection_alert_type_none),
        AlertType.SOUND to stringResource(R.string.connection_alert_type_sound),
        AlertType.VIBRATION to stringResource(R.string.connection_alert_type_vibration),
        AlertType.BOTH to stringResource(R.string.connection_alert_type_both)
    )

    var selectedType by remember { mutableStateOf(currentType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connection_alert_dialog_title)) },
        text = {
            LazyColumn {
                items(alertTypes) { (type, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type }
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
            TextButton(onClick = { onConfirm(selectedType) }) {
                Text(stringResource(R.string.general_ok_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.general_cancel_action))
            }
        }
    )
}

@Preview2
@Composable
private fun ConnectionAlertDialogPreview() {
    PreviewWrapper {
        ConnectionAlertDialog(
            currentType = AlertType.SOUND,
            onConfirm = {},
            onDismiss = {}
        )
    }
}
