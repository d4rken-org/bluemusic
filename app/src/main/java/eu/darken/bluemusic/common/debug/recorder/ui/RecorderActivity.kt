package eu.darken.bluemusic.common.debug.recorder.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.theming.BlueMusicTheme
import eu.darken.bluemusic.common.theming.themeState
import eu.darken.bluemusic.common.ui.Activity2
import eu.darken.bluemusic.main.core.GeneralSettings
import javax.inject.Inject

private sealed interface RecorderDialog {
    data object SentConfirm : RecorderDialog
    data object DeleteConfirm : RecorderDialog
}

@AndroidEntryPoint
class RecorderActivity : Activity2() {

    private val vm: RecorderViewModel by viewModels()

    @Inject lateinit var generalSettings: GeneralSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (intent.getStringExtra(RECORD_SESSION_ID) == null && intent.getStringExtra(RECORD_PATH) == null) {
            finish()
            return
        }

        setContent {
            val themeState by generalSettings.themeState.collectAsStateWithLifecycle(initialValue = null)
            themeState?.let { theme ->
                BlueMusicTheme(state = theme) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        var hasShared by remember { mutableStateOf(false) }
                        var dialog by remember { mutableStateOf<RecorderDialog?>(null) }

                        LaunchedEffect(Unit) {
                            vm.events.collect { event ->
                                when (event) {
                                    is RecorderViewModel.Event.ShareIntent -> {
                                        hasShared = true
                                        startActivity(event.intent)
                                    }

                                    is RecorderViewModel.Event.Finish -> finish()
                                }
                            }
                        }

                        LifecycleResumeEffect(hasShared) {
                            if (hasShared) {
                                dialog = RecorderDialog.SentConfirm
                                hasShared = false
                            }
                            onPauseOrDispose {}
                        }

                        when (dialog) {
                            is RecorderDialog.SentConfirm -> {
                                AlertDialog(
                                    onDismissRequest = { dialog = null },
                                    title = { Text(stringResource(R.string.support_debuglog_sent_title)) },
                                    text = { Text(stringResource(R.string.support_debuglog_sent_message)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            dialog = null
                                            vm.discard()
                                        }) {
                                            Text(stringResource(R.string.general_ok_action))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { dialog = null }) {
                                            Text(stringResource(R.string.general_cancel_action))
                                        }
                                    },
                                )
                            }

                            is RecorderDialog.DeleteConfirm -> {
                                AlertDialog(
                                    onDismissRequest = { dialog = null },
                                    title = { Text(stringResource(R.string.support_debuglog_session_delete_title)) },
                                    text = { Text(stringResource(R.string.support_debuglog_session_delete_message)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            dialog = null
                                            vm.discard()
                                        }) {
                                            Text(stringResource(R.string.general_delete_action))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { dialog = null }) {
                                            Text(stringResource(R.string.general_cancel_action))
                                        }
                                    },
                                )
                            }

                            null -> {}
                        }

                        val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
                        state?.let {
                            RecorderScreen(
                                state = it,
                                onShare = { vm.share() },
                                onKeep = { vm.keep() },
                                onDiscard = { dialog = RecorderDialog.DeleteConfirm },
                                onPrivacyPolicy = { vm.goPrivacyPolicy() },
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "RecorderActivity")
        const val RECORD_SESSION_ID = "sessionId"
        const val RECORD_PATH = "logPath"

        fun getLaunchIntent(context: Context, sessionId: String, legacyPath: String? = null): Intent {
            val intent = Intent(context, RecorderActivity::class.java)
            intent.putExtra(RECORD_SESSION_ID, sessionId)
            if (legacyPath != null) intent.putExtra(RECORD_PATH, legacyPath)
            return intent
        }
    }
}
