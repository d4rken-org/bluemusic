package eu.darken.bluemusic.main.ui.widget

import android.content.Context
import androidx.annotation.ColorInt
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.BluetoothRepo
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.ManagedDevice

object WidgetRenderStateMapper {

    fun map(
        context: Context,
        btState: BluetoothRepo.State,
        devices: List<ManagedDevice>,
        theme: WidgetTheme,
        isPro: Boolean,
    ): WidgetRenderState {
        val bgColor = resolvedBgColor(context, theme)
        val textColor = resolvedTextColor(context, theme)
        val iconColor = resolvedIconColor(context, theme)

        if (!btState.isEnabled) {
            return WidgetRenderState.BluetoothOff(
                theme = theme,
                resolvedBgColor = bgColor,
                resolvedTextColor = textColor,
                resolvedIconColor = iconColor,
            )
        }

        val activeDevice = devices
            .filter { it.type != SourceDevice.Type.PHONE_SPEAKER }
            .firstOrNull { it.isActive }

        if (activeDevice == null) {
            return WidgetRenderState.NoDevice(
                theme = theme,
                resolvedBgColor = bgColor,
                resolvedTextColor = textColor,
                resolvedIconColor = iconColor,
            )
        }

        if (!isPro) {
            return WidgetRenderState.UpgradeRequired(
                theme = theme,
                resolvedBgColor = bgColor,
                resolvedTextColor = textColor,
                resolvedIconColor = iconColor,
                deviceLabel = activeDevice.label,
            )
        }

        val accentColor = resolvedAccentColor(context, theme)
        val accentContentColor = WidgetTheme.bestContrastForeground(accentColor)
        val secondaryTextColor = resolvedSecondaryTextColor(bgColor, textColor)

        return WidgetRenderState.Active(
            theme = theme,
            resolvedBgColor = bgColor,
            resolvedTextColor = textColor,
            resolvedIconColor = iconColor,
            resolvedAccentColor = accentColor,
            resolvedAccentContentColor = accentContentColor,
            resolvedSecondaryTextColor = secondaryTextColor,
            deviceLabel = activeDevice.label,
            deviceAddress = activeDevice.address,
            isLocked = activeDevice.volumeLock,
        )
    }

    @ColorInt
    fun resolvedBgColor(context: Context, theme: WidgetTheme): Int {
        val baseColor = theme.backgroundColor ?: resolveThemeColor(context, android.R.attr.colorBackground)
        return WidgetTheme.applyAlpha(baseColor, theme.backgroundAlpha)
    }

    @ColorInt
    fun resolvedTextColor(context: Context, theme: WidgetTheme): Int {
        return theme.foregroundColor ?: resolveThemeColor(context, android.R.attr.textColorPrimary)
    }

    @ColorInt
    fun resolvedIconColor(context: Context, theme: WidgetTheme): Int {
        return theme.foregroundColor ?: resolvedTextColor(context, theme)
    }

    @ColorInt
    fun resolvedAccentColor(context: Context, theme: WidgetTheme): Int {
        return if (theme.backgroundColor != null && theme.foregroundColor != null) {
            ColorUtils.blendARGB(theme.backgroundColor, theme.foregroundColor, 0.32f)
        } else {
            resolveThemeColor(context, android.R.attr.colorAccent)
        }
    }

    @ColorInt
    fun resolvedSecondaryTextColor(@ColorInt bgColor: Int, @ColorInt textColor: Int): Int {
        return ColorUtils.blendARGB(bgColor, textColor, 0.55f)
    }

    @ColorInt
    private fun resolveThemeColor(context: Context, attr: Int): Int {
        val themedContext = ContextThemeWrapper(
            context,
            com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight,
        )
        val typedArray = themedContext.theme.obtainStyledAttributes(intArrayOf(attr))
        val color = typedArray.getColor(0, android.graphics.Color.BLACK)
        typedArray.recycle()
        return color
    }
}
