package eu.darken.bluemusic.common.debug.recorder.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.theming.BlueMusicTheme
import eu.darken.bluemusic.common.theming.themeState
import eu.darken.bluemusic.common.ui.Activity2
import eu.darken.bluemusic.main.core.GeneralSettings
import javax.inject.Inject

@AndroidEntryPoint
class RecorderActivity : Activity2() {
    private val vm: RecorderViewModel by viewModels()

    @Inject lateinit var generalSettings: GeneralSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (intent.getStringExtra(RECORD_PATH) == null) {
            finish()
            return
        }

        setContent {
            val themeState by generalSettings.themeState.collectAsState(null)
            themeState?.let { theme ->
                BlueMusicTheme(state = theme) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        ErrorEventHandler(vm)
                        RecorderScreenHost(
                            viewModel = vm,
                            onCancelClick = { finish() }
                        )
                    }
                }
            }
        }
    }

    companion object {
        internal val TAG = logTag("Debug", "Log", "RecorderActivity")
        const val RECORD_PATH = "logPath"

        fun getLaunchIntent(context: Context, path: String): Intent {
            val intent = Intent(context, RecorderActivity::class.java)
            intent.putExtra(RECORD_PATH, path)
            return intent
        }
    }
}
