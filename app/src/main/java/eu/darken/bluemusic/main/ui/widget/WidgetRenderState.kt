package eu.darken.bluemusic.main.ui.widget

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import eu.darken.bluemusic.R
import eu.darken.bluemusic.devices.core.DeviceAddr

sealed class WidgetRenderState {
    abstract val resolvedBgColor: Int
    abstract val resolvedTextColor: Int
    abstract val resolvedIconColor: Int

    data class Active(
        val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
        @ColorInt val resolvedAccentColor: Int,
        @ColorInt val resolvedAccentContentColor: Int,
        @ColorInt val resolvedSecondaryTextColor: Int,
        val deviceLabel: String,
        val deviceAddress: DeviceAddr,
        val isLocked: Boolean,
        @DrawableRes val lockIcon: Int = if (isLocked) R.drawable.ic_volume_lock_locked else R.drawable.ic_volume_lock_unlocked,
    ) : WidgetRenderState()

    data class NoDevice(
        val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
    ) : WidgetRenderState()

    data class BluetoothOff(
        val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
    ) : WidgetRenderState()

    data class UpgradeRequired(
        val theme: WidgetTheme,
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
        val deviceLabel: String,
    ) : WidgetRenderState()

    data class Error(
        @ColorInt override val resolvedBgColor: Int,
        @ColorInt override val resolvedTextColor: Int,
        @ColorInt override val resolvedIconColor: Int,
        val message: String,
    ) : WidgetRenderState()
}
