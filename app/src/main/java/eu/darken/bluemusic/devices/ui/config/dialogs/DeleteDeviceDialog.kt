package eu.darken.bluemusic.devices.ui.config.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.PreviewWrapper

@Composable
fun DeleteDeviceDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.TwoTone.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.devices_device_config_remove_device_label)) },
        text = { Text(stringResource(R.string.devices_device_config_remove_device_confirmation, deviceName)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(
                    text = stringResource(R.string.devices_device_config_remove_device_action),
                    color = MaterialTheme.colorScheme.error
                )
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
private fun DeleteDeviceDialogPreview() {
    PreviewWrapper {
        DeleteDeviceDialog(
            deviceName = "My Bluetooth Device",
            onConfirm = {},
            onDismiss = {}
        )
    }
}