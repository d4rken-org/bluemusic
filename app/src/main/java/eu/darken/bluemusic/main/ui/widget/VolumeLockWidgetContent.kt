package eu.darken.bluemusic.main.ui.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import eu.darken.bluemusic.R
import eu.darken.bluemusic.main.ui.MainActivity

@Composable
fun VolumeLockWidgetContent(
    state: WidgetRenderState,
    context: android.content.Context,
) {
    val layoutMode = currentLayoutMode()
    val openApp = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    )

    when (state) {
        is WidgetRenderState.Active -> ActiveContent(state, context, layoutMode)
        is WidgetRenderState.NoDevice -> MessageContent(
            bgColor = state.resolvedBgColor,
            textColor = state.resolvedTextColor,
            iconColor = state.resolvedIconColor,
            text = context.getString(R.string.widget_no_device_label),
            iconRes = R.drawable.ic_volume_lock_unlocked,
            clickModifier = GlanceModifier.clickable(openApp),
            layoutMode = layoutMode,
        )

        is WidgetRenderState.BluetoothOff -> MessageContent(
            bgColor = state.resolvedBgColor,
            textColor = state.resolvedTextColor,
            iconColor = state.resolvedIconColor,
            text = context.getString(R.string.widget_bluetooth_off_label),
            iconRes = R.drawable.ic_volume_lock_unlocked,
            clickModifier = GlanceModifier.clickable(openApp),
            layoutMode = layoutMode,
        )

        is WidgetRenderState.UpgradeRequired -> UpgradeContent(state, openApp, context, layoutMode)
        is WidgetRenderState.Error -> MessageContent(
            bgColor = state.resolvedBgColor,
            textColor = state.resolvedTextColor,
            iconColor = state.resolvedIconColor,
            text = state.message,
            iconRes = R.drawable.ic_volume_lock_unlocked,
            clickModifier = GlanceModifier.clickable(openApp),
            layoutMode = layoutMode,
        )
    }
}

@Composable
private fun ActiveContent(
    state: WidgetRenderState.Active,
    context: android.content.Context,
    layoutMode: WidgetLayoutMode,
) {
    val toggleAction = actionRunCallback<VolumeLockToggleAction>(
        parameters = actionParametersOf(
            VolumeLockToggleAction.PARAM_DEVICE_ADDRESS to state.deviceAddress
        )
    )

    when (layoutMode) {
        WidgetLayoutMode.ICON_ONLY -> WidgetRoot(
            bgColor = state.resolvedBgColor,
            clickModifier = GlanceModifier.clickable(toggleAction),
            compact = true,
        ) {
            ActiveCompactBody(
                state = state,
                text = null,
                textAlign = TextAlign.Center,
                compact = true,
            )
        }

        WidgetLayoutMode.COMPACT -> WidgetRoot(
            bgColor = state.resolvedBgColor,
            clickModifier = GlanceModifier.clickable(toggleAction),
            compact = true,
        ) {
            ActiveCompactBody(
                state = state,
                text = state.deviceLabel.takeIf { state.theme.showDeviceLabel },
                textAlign = TextAlign.Center,
                compact = true,
            )
        }

        WidgetLayoutMode.EXPANDED -> ActiveExpandedContent(
            state = state,
            context = context,
            clickModifier = GlanceModifier.clickable(toggleAction),
        )
    }
}

@Composable
private fun ActiveExpandedContent(
    state: WidgetRenderState.Active,
    context: android.content.Context,
    clickModifier: GlanceModifier,
) {
    val textColor = fixedColor(state.resolvedTextColor)
    val secondaryTextColor = fixedColor(state.resolvedSecondaryTextColor)

    WidgetRoot(state.resolvedBgColor, clickModifier) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LockStatusIcon(
                iconRes = state.lockIcon,
                backgroundColor = if (state.isLocked) {
                    state.resolvedAccentColor
                } else {
                    subtleContainerColor(state.resolvedBgColor, state.resolvedTextColor)
                },
                iconColor = if (state.isLocked) {
                    state.resolvedAccentContentColor
                } else {
                    state.resolvedIconColor
                },
            )

            Spacer(modifier = GlanceModifier.width(WidgetLayoutTokens.ContentSpacing))

            Column(modifier = GlanceModifier.defaultWeight()) {
                if (state.theme.showDeviceLabel) {
                    Text(
                        text = state.deviceLabel,
                        style = TextStyle(
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = context.getString(R.string.devices_device_config_volume_lock_label),
                        style = TextStyle(
                            color = secondaryTextColor,
                            fontSize = 12.sp,
                        ),
                        maxLines = 1,
                    )
                } else {
                    Text(
                        text = context.getString(R.string.devices_device_config_volume_lock_label),
                        style = TextStyle(
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpgradeContent(
    state: WidgetRenderState.UpgradeRequired,
    openApp: androidx.glance.action.Action,
    context: android.content.Context,
    layoutMode: WidgetLayoutMode,
) {
    when (layoutMode) {
        WidgetLayoutMode.EXPANDED -> UpgradeExpandedContent(state, openApp, context)
        WidgetLayoutMode.COMPACT, WidgetLayoutMode.ICON_ONLY -> MessageCompactContent(
            bgColor = state.resolvedBgColor,
            textColor = state.resolvedTextColor,
            iconColor = state.resolvedIconColor,
            text = state.deviceLabel.takeIf { layoutMode == WidgetLayoutMode.COMPACT && state.theme.showDeviceLabel }
                ?: context.getString(R.string.upgrade_feature_requires_pro).takeIf { layoutMode == WidgetLayoutMode.COMPACT },
            iconRes = R.drawable.ic_volume_lock_locked,
            clickModifier = GlanceModifier.clickable(openApp),
        )
    }
}

@Composable
private fun UpgradeExpandedContent(
    state: WidgetRenderState.UpgradeRequired,
    openApp: androidx.glance.action.Action,
    context: android.content.Context,
) {
    val textColor = fixedColor(state.resolvedTextColor)

    WidgetRoot(state.resolvedBgColor, GlanceModifier.clickable(openApp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LockStatusIcon(
                iconRes = R.drawable.ic_volume_lock_locked,
                backgroundColor = subtleContainerColor(state.resolvedBgColor, state.resolvedTextColor),
                iconColor = state.resolvedIconColor,
            )

            Spacer(modifier = GlanceModifier.width(WidgetLayoutTokens.ContentSpacing))

            Column(modifier = GlanceModifier.defaultWeight()) {
                if (state.theme.showDeviceLabel) {
                    Text(
                        text = state.deviceLabel,
                        style = TextStyle(
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                }
                Text(
                    text = context.getString(R.string.upgrade_feature_requires_pro),
                    style = TextStyle(
                        color = textColor,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MessageContent(
    bgColor: Int,
    textColor: Int,
    iconColor: Int,
    text: String,
    iconRes: Int,
    clickModifier: GlanceModifier,
    layoutMode: WidgetLayoutMode,
) {
    when (layoutMode) {
        WidgetLayoutMode.EXPANDED -> MessageExpandedContent(
            bgColor = bgColor,
            textColor = textColor,
            iconColor = iconColor,
            text = text,
            iconRes = iconRes,
            clickModifier = clickModifier,
        )

        WidgetLayoutMode.COMPACT, WidgetLayoutMode.ICON_ONLY -> MessageCompactContent(
            bgColor = bgColor,
            textColor = textColor,
            iconColor = iconColor,
            text = text.takeIf { layoutMode == WidgetLayoutMode.COMPACT },
            iconRes = iconRes,
            clickModifier = clickModifier,
        )
    }
}

@Composable
private fun MessageExpandedContent(
    bgColor: Int,
    textColor: Int,
    iconColor: Int,
    text: String,
    iconRes: Int,
    clickModifier: GlanceModifier,
) {
    WidgetRoot(bgColor, clickModifier) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LockStatusIcon(
                iconRes = iconRes,
                backgroundColor = subtleContainerColor(bgColor, textColor),
                iconColor = iconColor,
            )

            Spacer(modifier = GlanceModifier.width(WidgetLayoutTokens.ContentSpacing))

            Text(
                text = text,
                style = TextStyle(
                    color = fixedColor(textColor),
                    fontSize = 13.sp,
                ),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun MessageCompactContent(
    bgColor: Int,
    textColor: Int,
    iconColor: Int,
    text: String?,
    iconRes: Int,
    clickModifier: GlanceModifier,
) {
    WidgetRoot(bgColor = bgColor, clickModifier = clickModifier, compact = true) {
        LockStatusIcon(
            iconRes = iconRes,
            backgroundColor = subtleContainerColor(bgColor, textColor),
            iconColor = iconColor,
            compact = true,
        )

        text?.let {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = it,
                style = TextStyle(
                    color = fixedColor(textColor),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActiveCompactBody(
    state: WidgetRenderState.Active,
    text: String?,
    textAlign: TextAlign,
    compact: Boolean,
) {
    LockStatusIcon(
        iconRes = state.lockIcon,
        backgroundColor = if (state.isLocked) {
            state.resolvedAccentColor
        } else {
            subtleContainerColor(state.resolvedBgColor, state.resolvedTextColor)
        },
        iconColor = if (state.isLocked) {
            state.resolvedAccentContentColor
        } else {
            state.resolvedIconColor
        },
        compact = compact,
    )

    text?.let {
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = it,
            style = TextStyle(
                color = fixedColor(state.resolvedTextColor),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = textAlign,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun LockStatusIcon(
    iconRes: Int,
    backgroundColor: Int,
    iconColor: Int,
    compact: Boolean = false,
) {
    Box(
        modifier = GlanceModifier
            .size(if (compact) WidgetLayoutTokens.CompactIconContainerSize else WidgetLayoutTokens.IconContainerSize)
            .cornerRadius(WidgetLayoutTokens.IconContainerCorner)
            .background(fixedColor(backgroundColor)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(if (compact) WidgetLayoutTokens.CompactIconSize else WidgetLayoutTokens.IconSize),
            colorFilter = ColorFilter.tint(fixedColor(iconColor)),
        )
    }
}

@Composable
private fun WidgetRoot(
    bgColor: Int,
    clickModifier: GlanceModifier,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = clickModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(WidgetLayoutTokens.RootCorner)
                .background(fixedColor(bgColor))
                .padding(
                    horizontal = if (compact) WidgetLayoutTokens.CompactHorizontalPadding else WidgetLayoutTokens.HorizontalPadding,
                    vertical = if (compact) WidgetLayoutTokens.CompactVerticalPadding else WidgetLayoutTokens.VerticalPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun currentLayoutMode(): WidgetLayoutMode {
    val size = LocalSize.current
    return when {
        size.width <= WidgetLayoutTokens.IconOnlyMaxWidth -> WidgetLayoutMode.ICON_ONLY
        size.width <= WidgetLayoutTokens.CompactMaxWidth -> WidgetLayoutMode.COMPACT
        else -> WidgetLayoutMode.EXPANDED
    }
}

private fun subtleContainerColor(bgColor: Int, foregroundColor: Int): Int =
    ColorUtils.blendARGB(bgColor, foregroundColor, 0.08f)

private enum class WidgetLayoutMode {
    ICON_ONLY,
    COMPACT,
    EXPANDED,
}

private fun fixedColor(argb: Int): ColorProvider = ColorProvider(Color(argb))
