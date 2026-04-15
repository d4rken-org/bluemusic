package eu.darken.bluemusic.main.ui.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper

@Composable
fun ComposeWidgetPreview(
    state: WidgetRenderState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is WidgetRenderState.Active -> ActivePreview(state, modifier)
        is WidgetRenderState.NoDevice -> MessagePreview(
            bgColor = state.resolvedBgColor,
            textColor = state.resolvedTextColor,
            iconColor = state.resolvedIconColor,
            text = stringResource(R.string.widget_no_device_label),
            modifier = modifier,
        )

        is WidgetRenderState.BluetoothOff -> MessagePreview(
            bgColor = state.resolvedBgColor,
            textColor = state.resolvedTextColor,
            iconColor = state.resolvedIconColor,
            text = stringResource(R.string.widget_bluetooth_off_label),
            modifier = modifier,
        )

        is WidgetRenderState.UpgradeRequired -> UpgradePreview(state, modifier)
        is WidgetRenderState.Error -> MessagePreview(
            bgColor = state.resolvedBgColor,
            textColor = state.resolvedTextColor,
            iconColor = state.resolvedIconColor,
            text = state.message,
            modifier = modifier,
        )
    }
}

@Composable
fun CheckerboardBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val lightColor = Color(0xFFE7EAF0)
    val darkColor = Color(0xFFD5DAE2)

    Box(
        modifier = modifier
            .clipToBounds()
            .drawBehind {
                val cellSize = 8.dp.toPx()
                val cols = (size.width / cellSize).toInt() + 1
                val rows = (size.height / cellSize).toInt() + 1
                drawRect(lightColor)
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        if ((row + col) % 2 == 0) {
                            drawRect(
                                color = darkColor,
                                topLeft = Offset(col * cellSize, row * cellSize),
                                size = Size(cellSize, cellSize),
                            )
                        }
                    }
                }
            },
    ) {
        content()
    }
}

@Composable
private fun ActivePreview(
    state: WidgetRenderState.Active,
    modifier: Modifier = Modifier,
) {
    WidgetPreviewRoot(
        bgColor = state.resolvedBgColor,
        modifier = modifier,
    ) {
        PreviewIconContainer(
            iconRes = state.lockIcon,
            tint = if (state.isLocked) state.resolvedAccentContentColor else state.resolvedIconColor,
            containerColor = if (state.isLocked) {
                state.resolvedAccentColor
            } else {
                subtlePreviewContainerColor(state.resolvedBgColor, state.resolvedTextColor)
            },
        )

        Spacer(modifier = Modifier.width(WidgetLayoutTokens.ContentSpacing))

        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            if (state.theme.showDeviceLabel) {
                Text(
                    text = state.deviceLabel,
                    color = Color(state.resolvedTextColor),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.devices_device_config_volume_lock_label),
                    color = Color(state.resolvedSecondaryTextColor),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = stringResource(R.string.devices_device_config_volume_lock_label),
                    color = Color(state.resolvedTextColor),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UpgradePreview(
    state: WidgetRenderState.UpgradeRequired,
    modifier: Modifier = Modifier,
) {
    WidgetPreviewRoot(
        bgColor = state.resolvedBgColor,
        modifier = modifier,
    ) {
        PreviewIconContainer(
            iconRes = R.drawable.ic_volume_lock_locked,
            tint = state.resolvedIconColor,
            containerColor = subtlePreviewContainerColor(state.resolvedBgColor, state.resolvedTextColor),
        )

        Spacer(modifier = Modifier.width(WidgetLayoutTokens.ContentSpacing))

        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            if (state.theme.showDeviceLabel) {
                Text(
                    text = state.deviceLabel,
                    color = Color(state.resolvedTextColor),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(R.string.upgrade_feature_requires_pro),
                color = Color(state.resolvedTextColor),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MessagePreview(
    bgColor: Int,
    textColor: Int,
    iconColor: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    WidgetPreviewRoot(
        bgColor = bgColor,
        modifier = modifier,
    ) {
        PreviewIconContainer(
            iconRes = R.drawable.ic_volume_lock_unlocked,
            tint = iconColor,
            containerColor = subtlePreviewContainerColor(bgColor, textColor),
        )

        Spacer(modifier = Modifier.width(WidgetLayoutTokens.ContentSpacing))

        Text(
            text = text,
            color = Color(textColor),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WidgetPreviewRoot(
    bgColor: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(WidgetLayoutTokens.RootCorner),
        color = Color(bgColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = WidgetLayoutTokens.HorizontalPadding,
                    vertical = WidgetLayoutTokens.VerticalPadding,
                ),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun PreviewIconContainer(
    iconRes: Int,
    tint: Int,
    containerColor: Int? = null,
) {
    Box(
        modifier = Modifier
            .size(WidgetLayoutTokens.IconContainerSize)
            .then(
                if (containerColor != null) {
                    Modifier
                        .clip(RoundedCornerShape(WidgetLayoutTokens.IconContainerCorner))
                        .background(Color(containerColor))
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(WidgetLayoutTokens.IconSize),
            colorFilter = ColorFilter.tint(Color(tint)),
        )
    }
}

@Preview2
@Composable
private fun ActiveWidgetPreview() = PreviewWrapper {
    ComposeWidgetPreview(
        state = WidgetRenderState.Active(
            theme = WidgetTheme.DEFAULT,
            resolvedBgColor = 0xFF101319.toInt(),
            resolvedTextColor = 0xFFFFFFFF.toInt(),
            resolvedIconColor = 0xFFFFFFFF.toInt(),
            resolvedAccentColor = 0xFF2A3440.toInt(),
            resolvedAccentContentColor = 0xFFFFFFFF.toInt(),
            resolvedSecondaryTextColor = 0xFFA8B1BC.toInt(),
            deviceLabel = "My Headphones",
            deviceAddress = "preview",
            isLocked = true,
        ),
        modifier = Modifier.padding(16.dp),
    )
}

private fun subtlePreviewContainerColor(bgColor: Int, foregroundColor: Int): Int =
    androidx.core.graphics.ColorUtils.blendARGB(bgColor, foregroundColor, 0.08f)

@Preview2
@Composable
private fun TransparentWidgetPreview() = PreviewWrapper {
    CheckerboardBackground(modifier = Modifier.padding(16.dp)) {
        ComposeWidgetPreview(
            state = WidgetRenderState.NoDevice(
                theme = WidgetTheme(
                    backgroundColor = 0xFF1565C0.toInt(),
                    foregroundColor = 0xFFFFFFFF.toInt(),
                    backgroundAlpha = 180,
                ),
                resolvedBgColor = 0xB41565C0.toInt(),
                resolvedTextColor = 0xFFFFFFFF.toInt(),
                resolvedIconColor = 0xFFFFFFFF.toInt(),
            ),
        )
    }
}
