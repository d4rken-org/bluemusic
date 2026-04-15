package eu.darken.bluemusic.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.theming.BlueMusicTheme
import eu.darken.bluemusic.common.theming.themeState
import eu.darken.bluemusic.common.ui.Activity2
import eu.darken.bluemusic.main.core.GeneralSettings
import eu.darken.bluemusic.common.theming.ThemeState
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WidgetConfigurationActivity : Activity2() {

    private val vm: WidgetConfigurationViewModel by viewModels()

    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var widgetManager: WidgetManager
    @ApplicationContext @Inject lateinit var appContext: Context

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setResult(RESULT_CANCELED)

        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )

        log(TAG) { "onCreate(widgetId=$widgetId)" }

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            log(TAG) { "Invalid widget ID, finishing" }
            finish()
            return
        }

        setContent {
            val themeState by generalSettings.themeState.collectAsStateWithLifecycle(
                initialValue = ThemeState()
            )

            BlueMusicTheme(state = themeState) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val state by vm.state.collectAsStateWithLifecycle(initialValue = null)
                    state?.let { currentState ->
                        WidgetConfigurationScreen(
                            state = currentState,
                            onSelectPreset = { preset -> vm.selectPreset(preset) },
                            onEnterCustomMode = { bg, fg -> vm.enterCustomMode(bg, fg) },
                            onSetBackgroundColor = { color -> vm.setBackgroundColor(color) },
                            onSetForegroundColor = { color -> vm.setForegroundColor(color) },
                            onSetBackgroundAlpha = { alpha -> vm.setBackgroundAlpha(alpha) },
                            onSetShowDeviceLabel = { show -> vm.setShowDeviceLabel(show) },
                            onReset = { vm.resetToDefaults() },
                            onConfirm = { confirmSelection() },
                            onCancel = { finish() },
                        )
                    }
                }
            }
        }
    }

    private fun confirmSelection() {
        vm.confirmSelection()
        widgetManager.syncWidgetPresence()

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_OK, resultValue)

        lifecycleScope.launch {
            try {
                val manager = GlanceAppWidgetManager(appContext)
                val glanceId = manager.getGlanceIdBy(widgetId)
                VolumeLockGlanceWidget().update(appContext, glanceId)
            } finally {
                finish()
            }
        }
    }

    companion object {
        private val TAG = logTag("Widget", "ConfigurationActivity")
    }
}
