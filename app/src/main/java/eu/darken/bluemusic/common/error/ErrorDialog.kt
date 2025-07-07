package eu.darken.bluemusic.common.error

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper

@Composable
fun ErrorDialog(throwable: Throwable, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val localizedError = throwable.localized(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = localizedError.label.get(context),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                SelectionContainer {
                    Text(
                        text = localizedError.description.get(context),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                localizedError.infoAction?.let { action ->
                    TextButton(onClick = { activity?.let { action.invoke(it) } }) {
                        Text(
                            localizedError.infoActionLabel?.get(context)
                                ?: stringResource(R.string.general_show_details_action)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                localizedError.fixAction?.let { action ->
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.general_cancel_action))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            activity?.let { action.invoke(it) }
                            onDismiss()
                        }
                    ) {
                        Text(
                            localizedError.fixActionLabel?.get(context)
                                ?: stringResource(android.R.string.ok)
                        )
                    }
                }
                    ?: TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.ok))
                    }
            }
        }
    )
}

@Preview2
@Composable
fun ErrorDialogPreview() {
    PreviewWrapper {
        ErrorDialog(
            throwable = RuntimeException("Sample error message for preview"),
            onDismiss = {}
        )
    }
}
