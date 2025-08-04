package eu.darken.bluemusic.devices.ui.config.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.PreviewWrapper
import java.time.Duration

@Composable
fun TimingDialog(
    title: String,
    message: String,
    currentValue: Duration,
    onConfirm: (Duration) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(currentValue.toMillis().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.TwoTone.Refresh, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                Spacer(modifier = Modifier.Companion.height(16.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Companion.Number),
                    singleLine = true,
                    modifier = Modifier.Companion.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    value.toLongOrNull()?.let {
                        onConfirm(Duration.ofMillis(it))
                        onDismiss()
                    }
                }
            ) {
                Text(stringResource(R.string.action_set))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onReset()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.action_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

@Preview
@Composable
private fun TimingDialogPreview() {
    PreviewWrapper {
        TimingDialog(
            title = "Reaction Delay",
            message = "Time to wait before applying volume changes after connection",
            currentValue = Duration.ofMillis(4000),
            onConfirm = {},
            onReset = {},
            onDismiss = {}
        )
    }
}
