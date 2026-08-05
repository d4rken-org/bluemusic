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
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag

@Composable
fun ErrorDialog(throwable: Throwable, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val localizedError = throwable.localized(context)

    fun dispatchAndDismiss(action: (Activity) -> Unit) {
        // Error actions are arbitrary third-party code (intent launches, navigation): a throw here
        // would crash the UI thread from inside a click handler, and skipping onDismiss() would
        // leave the dialog latched on the current error with no way out.
        try {
            activity?.let(action)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Error action failed: ${e.asLog()}" }
        } finally {
            onDismiss()
        }
    }

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
                    TextButton(onClick = { dispatchAndDismiss(action) }) {
                        Text(
                            localizedError.infoActionLabel?.get(context)
                                ?: stringResource(R.string.general_show_details_action)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                localizedError.fixAction?.let { action ->
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.general_dismiss_action))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { dispatchAndDismiss(action) }) {
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

private val TAG = logTag("Error", "Dialog")

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
