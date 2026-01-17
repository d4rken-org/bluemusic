package eu.darken.bluemusic.main.ui.settings.general

import androidx.compose.foundation.layout.Arrangement
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.settings.EnumSelectorDialog
import eu.darken.bluemusic.common.settings.SettingsCategoryHeader
import eu.darken.bluemusic.common.settings.SettingsDivider
import eu.darken.bluemusic.common.settings.SettingsPreferenceItem
import eu.darken.bluemusic.common.theming.ThemeMode
import eu.darken.bluemusic.common.theming.ThemeStyle
import eu.darken.bluemusic.common.ui.waitForState
import kotlinx.coroutines.launch

@Composable
fun GeneralSettingsScreenHost(vm: GeneralSettingsViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val state by waitForState(vm.state)

    state?.let { vmState ->
        GeneralSettingsScreen(
            state = vmState,
            onNavigateUp = { vm.navUp() },
            onLanguageSwitcher = { vm.showLanguagePicker() },
            onThemeModeSelected = { vm.updateThemeMode(it) },
            onThemeStyleSelected = { vm.updateThemeStyle(it) },
            onUpgrade = { vm.upgrade() },
        )
    }
}

@Composable
fun GeneralSettingsScreen(
    state: GeneralSettingsViewModel.State,
    onNavigateUp: () -> Unit,
    onLanguageSwitcher: (() -> Unit)?,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onThemeStyleSelected: (ThemeStyle) -> Unit,
    onUpgrade: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showThemeStyleDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.general_settings_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                stringResource(R.string.general_back_action)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .horizontalCutoutPadding(),
            verticalArrangement = Arrangement.Top
        ) {
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_ui_label))
            }

            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Palette,
                    title = stringResource(R.string.ui_theme_mode_setting_label),
                    subtitle = stringResource(R.string.ui_theme_mode_setting_explanation),
                    value = state.themeState.mode.label.get(context),
                    onClick = {
                        if (state.isUpgraded) {
                            showThemeModeDialog = true
                        } else {
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.upgrade_feature_requires_pro),
                                    actionLabel = context.getString(R.string.upgrade_prompt_upgrade_action)
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    onUpgrade()
                                }
                            }
                        }
                    }
                )
                SettingsDivider()
            }

            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Palette,
                    title = stringResource(R.string.ui_theme_style_setting_label),
                    subtitle = stringResource(R.string.ui_theme_style_setting_explanation),
                    value = state.themeState.style.label.get(context),
                    onClick = {
                        if (state.isUpgraded) {
                            showThemeStyleDialog = true
                        } else {
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.upgrade_feature_requires_pro),
                                    actionLabel = context.getString(R.string.upgrade_prompt_upgrade_action)
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    onUpgrade()
                                }
                            }
                        }
                    }
                )
                SettingsDivider()
            }

            onLanguageSwitcher?.let { action ->
                item {
                    SettingsPreferenceItem(
                        icon = Icons.TwoTone.Translate,
                        title = stringResource(R.string.ui_language_override_label),
                        subtitle = stringResource(R.string.ui_language_override_desc),
                        onClick = { action.invoke() }
                    )
                    SettingsDivider()
                }
            }
        }
    }

    if (showThemeModeDialog) {
        EnumSelectorDialog(
            title = stringResource(R.string.ui_theme_mode_setting_label),
            options = ThemeMode.entries,
            selectedOption = state.themeState.mode,
            onOptionSelected = { mode ->
                onThemeModeSelected(mode)
                showThemeModeDialog = false
            },
            onDismiss = { showThemeModeDialog = false }
        )
    }

    if (showThemeStyleDialog) {
        EnumSelectorDialog(
            title = stringResource(R.string.ui_theme_style_setting_label),
            options = ThemeStyle.entries,
            selectedOption = state.themeState.style,
            onOptionSelected = { style ->
                onThemeStyleSelected(style)
                showThemeStyleDialog = false
            },
            onDismiss = { showThemeStyleDialog = false }
        )
    }
}

@Preview2
@Composable
private fun GeneralSettingsScreenPreview() {
    PreviewWrapper {
        GeneralSettingsScreen(
            state = GeneralSettingsViewModel.State(filePreviews = true),
            onNavigateUp = {},
            onLanguageSwitcher = {},
            onThemeModeSelected = {},
            onThemeStyleSelected = {},
            onUpgrade = {},
        )
    }
}