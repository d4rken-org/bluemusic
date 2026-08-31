package eu.darken.bluemusic.devices.ui.config.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.VolumeUp
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
import kotlin.math.roundToInt

@Composable
fun VolumeLimitDialog(
    title: String,
    currentMin: Float?,
    currentMax: Float?,
    onConfirm: (Float?, Float?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var minValue by remember { mutableStateOf(currentMin.toPercentInput()) }
    var maxValue by remember { mutableStateOf(currentMax.toPercentInput()) }

    val minPercent = minValue.toPercentOrNull()
    val maxPercent = maxValue.toPercentOrNull()
    val isValid = minPercent.isAcceptable(minValue) &&
            maxPercent.isAcceptable(maxValue) &&
            (minPercent == null || maxPercent == null || minPercent <= maxPercent)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.TwoTone.VolumeUp, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column {
                Text(stringResource(R.string.devices_device_config_volume_limit_dialog_desc))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = minValue,
                    onValueChange = { minValue = it },
                    label = { Text(stringResource(R.string.devices_device_config_volume_limit_dialog_min_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !minPercent.isAcceptable(minValue),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxValue,
                    onValueChange = { maxValue = it },
                    label = { Text(stringResource(R.string.devices_device_config_volume_limit_dialog_max_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !maxPercent.isAcceptable(maxValue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onConfirm(minPercent?.let { it / 100f }, maxPercent?.let { it / 100f })
                    onDismiss()
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

private fun Float?.toPercentInput(): String = this?.let { (it * 100).roundToInt().toString() } ?: ""

private fun String.toPercentOrNull(): Int? = trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

/** Blank means "no bound"; anything else has to be a percentage. */
private fun Int?.isAcceptable(input: String): Boolean =
    if (input.isBlank()) true else this != null && this in 0..100

@Preview
@Composable
private fun VolumeLimitDialogPreview() {
    PreviewWrapper {
        VolumeLimitDialog(
            title = "Limit for Music",
            currentMin = 0.1f,
            currentMax = 0.5f,
            onConfirm = { _, _ -> },
            onReset = {},
            onDismiss = {}
        )
    }
}
