package eu.darken.bluemusic.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class WidgetConfigurationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepo,
    @ApplicationContext private val context: Context,
) : eu.darken.bluemusic.common.ui.ViewModel2(dispatcherProvider, logTag("Widget", "ConfigurationVM")) {

    override var launchErrorHandler: CoroutineExceptionHandler? = null

    private val appWidgetManager by lazy { AppWidgetManager.getInstance(context) }

    val widgetId: Int
        get() = savedStateHandle.get<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

    init {
        log(tag) { "ViewModel init(widgetId=$widgetId)" }
    }

    private val initialTheme: WidgetTheme = run {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return@run WidgetTheme.DEFAULT
        WidgetTheme.fromBundle(appWidgetManager.getAppWidgetOptions(widgetId))
    }

    private val forceCustomMode = MutableStateFlow(WidgetTheme.matchPreset(initialTheme) == null)
    private val currentTheme = MutableStateFlow(initialTheme)

    val state = combine(
        currentTheme,
        forceCustomMode,
        upgradeRepo.upgradeInfo,
    ) { theme, forceCustom, upgradeInfo ->
        val activePreset = if (forceCustom) null else WidgetTheme.matchPreset(theme)
        State(
            isPro = upgradeInfo.isUpgraded,
            theme = theme,
            activePreset = activePreset,
            isCustomMode = activePreset == null,
        )
    }.asStateFlow()

    data class State(
        val isPro: Boolean = false,
        val theme: WidgetTheme = WidgetTheme.DEFAULT,
        val activePreset: WidgetTheme.Preset? = WidgetTheme.Preset.MATERIAL_YOU,
        val isCustomMode: Boolean = false,
    )

    fun selectPreset(preset: WidgetTheme.Preset) {
        log(tag, INFO) { "selectPreset(preset=$preset)" }
        forceCustomMode.value = false
        currentTheme.value = WidgetTheme(
            backgroundColor = preset.presetBg,
            foregroundColor = preset.presetFg,
            backgroundAlpha = currentTheme.value.backgroundAlpha,
            showDeviceLabel = currentTheme.value.showDeviceLabel,
        )
    }

    fun enterCustomMode(resolvedBg: Int, resolvedFg: Int) {
        log(tag, INFO) { "enterCustomMode()" }
        forceCustomMode.value = true
        val theme = currentTheme.value
        currentTheme.value = theme.copy(
            backgroundColor = theme.backgroundColor ?: (resolvedBg or 0xFF000000.toInt()),
            foregroundColor = theme.foregroundColor ?: (resolvedFg or 0xFF000000.toInt()),
        )
    }

    fun setBackgroundColor(color: Int) {
        currentTheme.value = currentTheme.value.copy(backgroundColor = color or 0xFF000000.toInt())
    }

    fun setForegroundColor(color: Int) {
        currentTheme.value = currentTheme.value.copy(foregroundColor = color or 0xFF000000.toInt())
    }

    fun setBackgroundAlpha(alpha: Int) {
        currentTheme.value = currentTheme.value.copy(backgroundAlpha = alpha.coerceIn(0, 255))
    }

    fun setShowDeviceLabel(show: Boolean) {
        currentTheme.value = currentTheme.value.copy(showDeviceLabel = show)
    }

    fun resetToDefaults() {
        log(tag, INFO) { "resetToDefaults()" }
        forceCustomMode.value = false
        currentTheme.value = WidgetTheme.DEFAULT
    }

    fun confirmSelection() {
        val theme = currentTheme.value
        log(tag, INFO) { "confirmSelection: saving theme=$theme" }
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        theme.toBundle(options)
        appWidgetManager.updateAppWidgetOptions(widgetId, options)
    }

    companion object {
        private val TAG = logTag("Widget", "ConfigurationVM")
    }
}
