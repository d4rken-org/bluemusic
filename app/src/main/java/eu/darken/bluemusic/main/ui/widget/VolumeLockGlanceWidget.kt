package eu.darken.bluemusic.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.annotation.Keep
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class VolumeLockGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    @Keep
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun deviceRepo(): DeviceRepo
        fun upgradeRepo(): UpgradeRepo
        fun bluetoothRepo(): BluetoothRepo
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep: WidgetEntryPoint
        val appWidgetId: Int
        val initialIsPro: Boolean

        try {
            ep = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
            appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
            log(TAG, VERBOSE) { "provideGlance(appWidgetId=$appWidgetId)" }
            initialIsPro = ep.upgradeRepo().upgradeInfo.first().isUpgraded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "provideGlance setup failed: ${e.asLog()}" }
            val theme = WidgetTheme.DEFAULT
            provideContent {
                VolumeLockWidgetContent(
                    state = WidgetRenderState.Error(
                        resolvedBgColor = WidgetRenderStateMapper.resolvedBgColor(context, theme),
                        resolvedTextColor = WidgetRenderStateMapper.resolvedTextColor(context, theme),
                        resolvedIconColor = WidgetRenderStateMapper.resolvedIconColor(context, theme),
                        message = context.getString(R.string.widget_error_label),
                    ),
                    context = context,
                )
            }
            return
        }

        provideContent {
            val devices by ep.deviceRepo().devices.collectAsState(initial = emptyList())
            val btState by ep.bluetoothRepo().state.collectAsState(
                initial = BluetoothRepo.State(isEnabled = true, hasPermission = true, devices = emptySet())
            )
            val upgradeInfo by ep.upgradeRepo().upgradeInfo.collectAsState(initial = null)

            val isPro = upgradeInfo?.isUpgraded ?: initialIsPro

            val theme = try {
                WidgetTheme.fromBundle(
                    AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
                )
            } catch (e: Exception) {
                WidgetTheme.DEFAULT
            }

            val state = WidgetRenderStateMapper.map(
                context = context,
                btState = btState,
                devices = devices,
                theme = theme,
                isPro = isPro,
            )

            VolumeLockWidgetContent(state = state, context = context)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val theme = WidgetTheme.DEFAULT
        val bgColor = WidgetRenderStateMapper.resolvedBgColor(context, theme)
        val textColor = WidgetRenderStateMapper.resolvedTextColor(context, theme)
        val iconColor = WidgetRenderStateMapper.resolvedIconColor(context, theme)
        val accentColor = WidgetRenderStateMapper.resolvedAccentColor(context, theme)
        val accentContentColor = WidgetTheme.bestContrastForeground(accentColor)
        val secondaryTextColor = WidgetRenderStateMapper.resolvedSecondaryTextColor(bgColor, textColor)

        provideContent {
            VolumeLockWidgetContent(
                state = WidgetRenderState.Active(
                    theme = theme,
                    resolvedBgColor = bgColor,
                    resolvedTextColor = textColor,
                    resolvedIconColor = iconColor,
                    resolvedAccentColor = accentColor,
                    resolvedAccentContentColor = accentContentColor,
                    resolvedSecondaryTextColor = secondaryTextColor,
                    deviceLabel = "My Headphones",
                    deviceAddress = "preview",
                    isLocked = true,
                ),
                context = context,
            )
        }
    }

    companion object {
        val TAG = logTag("Widget", "Glance")
    }
}
