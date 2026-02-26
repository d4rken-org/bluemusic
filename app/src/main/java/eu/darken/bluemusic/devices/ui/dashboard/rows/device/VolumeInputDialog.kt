package eu.darken.bluemusic.devices.ui.dashboard.rows.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper

@Composable
fun VolumeInputDialog(
    streamLabel: String,
    currentPercentage: Int,
    minValue: Int = 0,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialText = currentPercentage.toString()
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = initialText, selection = TextRange(0, initialText.length)))
    }

    val parsed = textFieldValue.text.trim().toIntOrNull()
    val isValid = parsed != null && parsed in minValue..100

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun confirm() {
        val value = parsed ?: return
        if (!isValid) return
        onConfirm(value / 100f)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(streamLabel) },
        text = {
            Column {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    label = { Text(stringResource(R.string.devices_volume_input_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    isError = textFieldValue.text.isNotEmpty() && !isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                if (textFieldValue.text.isNotEmpty() && !isValid) {
                    Text(
                        text = stringResource(R.string.devices_volume_input_error, minValue, 100),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = isValid) {
                Text(stringResource(R.string.action_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Preview2
@Composable
private fun VolumeInputDialogPreview() {
    PreviewWrapper {
        VolumeInputDialog(
            streamLabel = "Music",
            currentPercentage = 75,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview2
@Composable
private fun VolumeInputDialogRingtonePreview() {
    PreviewWrapper {
        VolumeInputDialog(
            streamLabel = "Ringtone",
            currentPercentage = 50,
            minValue = 1,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
