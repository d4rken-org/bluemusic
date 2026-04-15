package eu.darken.bluemusic.main.ui.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.ColoredTitleText
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetConfigurationScreen(
    state: WidgetConfigurationViewModel.State,
    onSelectPreset: (WidgetTheme.Preset) -> Unit,
    onEnterCustomMode: (resolvedBg: Int, resolvedFg: Int) -> Unit,
    onSetBackgroundColor: (Int) -> Unit,
    onSetForegroundColor: (Int) -> Unit,
    onSetBackgroundAlpha: (Int) -> Unit,
    onSetShowDeviceLabel: (Boolean) -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val resolvedBg = remember(state.theme) { WidgetRenderStateMapper.resolvedBgColor(context, state.theme) }
    val resolvedFg = remember(state.theme) { WidgetRenderStateMapper.resolvedTextColor(context, state.theme) }
    val resolvedIcon = remember(state.theme) { WidgetRenderStateMapper.resolvedIconColor(context, state.theme) }
    val opacityPercent = remember(state.theme.backgroundAlpha) {
        (state.theme.backgroundAlpha / 255f * 100f).roundToInt()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        bottomBar = {
            WidgetConfigurationBottomBar(
                onReset = onReset,
                onCancel = onCancel,
                onConfirm = onConfirm,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(it)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.widget_config_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.isPro) {
                    ColoredTitleText(
                        fullTitle = stringResource(R.string.app_name_upgraded),
                        postfix = stringResource(R.string.app_name_upgrade_postfix),
                        style = MaterialTheme.typography.titleMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.app_name_short),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            WidgetPreview(
                theme = state.theme,
                resolvedBg = resolvedBg,
                resolvedFg = resolvedFg,
                resolvedIcon = resolvedIcon,
            )

            ConfigurationSection(title = stringResource(R.string.widget_config_theme_label)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WidgetTheme.Preset.entries.forEach { preset ->
                        val isSelected = state.activePreset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectPreset(preset) },
                            label = { Text(presetLabel(preset)) },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        Icons.TwoTone.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
                            } else null,
                        )
                    }

                    FilterChip(
                        selected = state.isCustomMode,
                        onClick = { onEnterCustomMode(resolvedBg, resolvedFg) },
                        label = { Text(stringResource(R.string.widget_config_theme_custom_label)) },
                        leadingIcon = if (state.isCustomMode) {
                            {
                                Icon(
                                    Icons.TwoTone.Palette,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else null,
                    )
                }

                AnimatedVisibility(visible = state.isCustomMode) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.theme.backgroundColor?.let { bg ->
                            ColorInput(
                                label = stringResource(R.string.widget_config_bg_color_label),
                                color = bg,
                                onColorChanged = onSetBackgroundColor,
                            )
                        }
                        state.theme.foregroundColor?.let { fg ->
                            ColorInput(
                                label = stringResource(R.string.widget_config_fg_color_label),
                                color = fg,
                                onColorChanged = onSetForegroundColor,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.widget_config_bg_alpha_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "$opacityPercent%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Slider(
                    value = state.theme.backgroundAlpha / 255f,
                    onValueChange = { onSetBackgroundAlpha((it * 255).toInt()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ConfigurationSection {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetShowDeviceLabel(!state.theme.showDeviceLabel) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.widget_config_show_label_toggle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.widget_config_show_label_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.theme.showDeviceLabel,
                        onCheckedChange = onSetShowDeviceLabel,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WidgetPreview(
    theme: WidgetTheme,
    resolvedBg: Int,
    resolvedFg: Int,
    resolvedIcon: Int,
) {
    val context = LocalContext.current
    val accentColor = remember(theme) { WidgetRenderStateMapper.resolvedAccentColor(context, theme) }
    val accentContentColor = remember(accentColor) { WidgetTheme.bestContrastForeground(accentColor) }
    val secondaryFg = remember(resolvedBg, resolvedFg) {
        WidgetRenderStateMapper.resolvedSecondaryTextColor(resolvedBg, resolvedFg)
    }
    val sampleDeviceLabel = stringResource(R.string.widget_preview_device_label)
    val hasTransparency = theme.backgroundColor != null && theme.backgroundAlpha < 255

    val lockedState = remember(theme, resolvedBg, resolvedFg, resolvedIcon, accentColor, secondaryFg, sampleDeviceLabel) {
        WidgetRenderState.Active(
            theme = theme,
            resolvedBgColor = resolvedBg,
            resolvedTextColor = resolvedFg,
            resolvedIconColor = resolvedIcon,
            resolvedAccentColor = accentColor,
            resolvedAccentContentColor = accentContentColor,
            resolvedSecondaryTextColor = secondaryFg,
            deviceLabel = sampleDeviceLabel,
            deviceAddress = "preview",
            isLocked = true,
        )
    }
    val unlockedState = remember(theme, resolvedBg, resolvedFg, resolvedIcon, accentColor, secondaryFg, sampleDeviceLabel) {
        WidgetRenderState.Active(
            theme = theme,
            resolvedBgColor = resolvedBg,
            resolvedTextColor = resolvedFg,
            resolvedIconColor = resolvedIcon,
            resolvedAccentColor = accentColor,
            resolvedAccentContentColor = accentContentColor,
            resolvedSecondaryTextColor = secondaryFg,
            deviceLabel = sampleDeviceLabel,
            deviceAddress = "preview",
            isLocked = false,
            lockIcon = R.drawable.ic_volume_lock_unlocked,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.widget_config_preview_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                if (hasTransparency) {
                    CheckerboardBackground {
                        WidgetPreviewVariants(
                            lockedState = lockedState,
                            unlockedState = unlockedState,
                        )
                    }
                } else {
                    WidgetPreviewVariants(
                        lockedState = lockedState,
                        unlockedState = unlockedState,
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewVariants(
    lockedState: WidgetRenderState.Active,
    unlockedState: WidgetRenderState.Active,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ComposeWidgetPreview(state = lockedState)
        ComposeWidgetPreview(state = unlockedState)
    }
}

@Composable
private fun ConfigurationSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            content()
        }
    }
}

@Composable
private fun WidgetConfigurationBottomBar(
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.general_reset_action))
            }
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.general_cancel_action))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.general_ok_action))
            }
        }
    }
}

@Composable
private fun ColorInput(
    label: String,
    color: Int,
    onColorChanged: (Int) -> Unit,
) {
    val hexString = String.format("%06X", 0xFFFFFF and color)
    var textFieldValue by remember(hexString) {
        mutableStateOf(TextFieldValue(hexString, TextRange(hexString.length)))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(color or 0xFF000000.toInt()))
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                val filtered = newValue.text.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.take(6)
                textFieldValue = newValue.copy(text = filtered)
                if (filtered.length == 6) {
                    try {
                        onColorChanged(filtered.toLong(16).toInt())
                    } catch (_: NumberFormatException) {
                    }
                }
            },
            label = { Text(label) },
            prefix = { Text("#") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun presetLabel(preset: WidgetTheme.Preset): String = when (preset) {
    WidgetTheme.Preset.MATERIAL_YOU -> stringResource(R.string.ui_theme_style_materialyou_label)
    WidgetTheme.Preset.CLASSIC_DARK -> stringResource(R.string.ui_theme_mode_dark_label)
    WidgetTheme.Preset.CLASSIC_LIGHT -> stringResource(R.string.ui_theme_mode_light_label)
    WidgetTheme.Preset.BLUE -> stringResource(R.string.ui_theme_color_blue_label)
    WidgetTheme.Preset.GREEN -> stringResource(R.string.widget_config_theme_green_label)
    WidgetTheme.Preset.RED -> stringResource(R.string.widget_config_theme_red_label)
}

@Preview2
@Composable
private fun ConfigScreenPresetPreview() = PreviewWrapper {
    WidgetConfigurationScreen(
        state = WidgetConfigurationViewModel.State(
            isPro = true,
            theme = WidgetTheme.DEFAULT,
            activePreset = WidgetTheme.Preset.MATERIAL_YOU,
            isCustomMode = false,
        ),
        onSelectPreset = {},
        onEnterCustomMode = { _, _ -> },
        onSetBackgroundColor = {},
        onSetForegroundColor = {},
        onSetBackgroundAlpha = {},
        onSetShowDeviceLabel = {},
        onReset = {},
        onConfirm = {},
        onCancel = {},
    )
}

@Preview2
@Composable
private fun ConfigScreenCustomPreview() = PreviewWrapper {
    WidgetConfigurationScreen(
        state = WidgetConfigurationViewModel.State(
            isPro = true,
            theme = WidgetTheme(
                backgroundColor = 0xFF1565C0.toInt(),
                foregroundColor = 0xFFFFFFFF.toInt(),
                backgroundAlpha = 200,
            ),
            activePreset = null,
            isCustomMode = true,
        ),
        onSelectPreset = {},
        onEnterCustomMode = { _, _ -> },
        onSetBackgroundColor = {},
        onSetForegroundColor = {},
        onSetBackgroundAlpha = {},
        onSetShowDeviceLabel = {},
        onReset = {},
        onConfirm = {},
        onCancel = {},
    )
}
