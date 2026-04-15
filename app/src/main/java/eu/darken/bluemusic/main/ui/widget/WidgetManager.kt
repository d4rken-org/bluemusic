package eu.darken.bluemusic.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val appWidgetManager by lazy { AppWidgetManager.getInstance(context) }
    private val widgetProvider by lazy { ComponentName(context, WidgetProvider::class.java) }
    private val _hasWidgets = MutableStateFlow(false)
    val hasWidgets: StateFlow<Boolean> = _hasWidgets.asStateFlow()

    fun syncWidgetPresence(): Boolean {
        val isPresent = appWidgetManager.getAppWidgetIds(widgetProvider).isNotEmpty()
        if (_hasWidgets.value != isPresent) {
            log(TAG) { "syncWidgetPresence(): hasWidgets=$isPresent" }
        } else {
            log(TAG, VERBOSE) { "syncWidgetPresence(): unchanged=$isPresent" }
        }
        _hasWidgets.value = isPresent
        return isPresent
    }

    suspend fun refreshWidgets() {
        if (!syncWidgetPresence()) {
            log(TAG, VERBOSE) { "refreshWidgets(): no widgets present" }
            return
        }
        log(TAG, VERBOSE) { "refreshWidgets()" }
        VolumeLockGlanceWidget().updateAll(context)
    }

    companion object {
        val TAG = logTag("Widget", "Manager")
    }
}
