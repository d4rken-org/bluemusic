package eu.darken.bluemusic.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.annotation.Keep
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag

class WidgetProvider : GlanceAppWidgetReceiver() {

    @Keep
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetProviderEntryPoint {
        fun widgetManager(): WidgetManager
    }

    override val glanceAppWidget: GlanceAppWidget = VolumeLockGlanceWidget()

    private fun widgetManager(context: Context): WidgetManager =
        EntryPointAccessors.fromApplication(context, WidgetProviderEntryPoint::class.java).widgetManager()

    override fun onEnabled(context: Context) {
        log(TAG) { "onEnabled()" }
        widgetManager(context).syncWidgetPresence()
        super.onEnabled(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        log(TAG) { "onUpdate(appWidgetIds=${appWidgetIds.toList()})" }
        widgetManager(context).syncWidgetPresence()
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        log(TAG) { "onDeleted(appWidgetIds=${appWidgetIds.toList()})" }
        super.onDeleted(context, appWidgetIds)
        widgetManager(context).syncWidgetPresence()
    }

    override fun onDisabled(context: Context) {
        log(TAG) { "onDisabled()" }
        super.onDisabled(context)
        widgetManager(context).syncWidgetPresence()
    }

    companion object {
        val TAG = logTag("Widget", "Provider")
    }
}
