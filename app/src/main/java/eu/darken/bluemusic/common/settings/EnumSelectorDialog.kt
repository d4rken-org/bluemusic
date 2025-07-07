package eu.darken.bluemusic.common.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R

@Composable
fun <T> EnumSelectorDialog(
    title: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) where T : Enum<T>, T : EnumPreference<T> {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOptionSelected(option) }
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selectedOption,
                            onClick = { onOptionSelected(option) }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = option.label.get(context),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.general_cancel_action))
            }
        }
    )
}
