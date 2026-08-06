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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.ca.CaString
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

    // Keyed on the throwable, not the LocalizedError: the latter is rebuilt (with fresh action
    // lambdas, so never equal) on every recomposition, which would wipe the message immediately.
    var actionError by remember(throwable) { mutableStateOf<CaString?>(null) }

    // errorMessage is per-dispatch, NOT read from localizedError: this function serves both the fix
    // and the info button, and fixActionErrorMessage describes only the fix action's failure. Each
    // call site passes its own copy (or none), so no button can ever surface another one's message.
    fun dispatchAndDismiss(action: (Activity) -> Unit, errorMessage: CaString? = null) {
        // Error actions are arbitrary third-party code (intent launches, navigation): a throw here
        // would crash the UI thread from inside a click handler, and skipping onDismiss() would
        // leave the dialog latched on the current error with no way out.
        try {
            activity?.let(action)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Error action failed: ${e.asLog()}" }
            // A dispatch that ships its own failure copy keeps the dialog open and shows it inline
            // (no length cap, unlike a Toast). Never latched: the dismiss button stays available.
            errorMessage?.let {
                actionError = it
                return
            }
        }
        onDismiss()
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
                actionError?.let {
                    SelectionContainer {
                        Text(
                            text = it.get(context),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                localizedError.infoAction?.let { action ->
                    // No errorMessage: the info action has no failure copy of its own, and it must
                    // never borrow the fix action's.
                    TextButton(onClick = { dispatchAndDismiss(action = action) }) {
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
                    TextButton(
                        onClick = {
                            dispatchAndDismiss(
                                action = action,
                                errorMessage = localizedError.fixActionErrorMessage,
                            )
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
