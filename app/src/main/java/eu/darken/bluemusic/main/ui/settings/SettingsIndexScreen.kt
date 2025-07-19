package eu.darken.bluemusic.main.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.twotone.Devices
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.PrivacyTip
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.BlueMusicLinks
import eu.darken.bluemusic.common.compose.ColoredTitleText
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.common.navigation.NavigationDestination
import eu.darken.bluemusic.common.settings.SettingsBaseItem
import eu.darken.bluemusic.common.settings.SettingsCategoryHeader
import eu.darken.bluemusic.common.settings.SettingsDivider
import eu.darken.bluemusic.common.ui.waitForState

@Composable
fun SettingsIndexScreenHost(vm: SettingsViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val state by waitForState(vm.state)

    state?.let { state ->
        SettingsIndexScreen(
            state = state,
            onNavigateUp = { vm.navUp() },
            onNavigateTo = { vm.navTo(it) },
            onOpenUrl = { vm.openUrl(it) },
        )
    }
}

@Composable
fun SettingsIndexScreen(
    state: SettingsViewModel.State,
    onNavigateUp: () -> Unit,
    onNavigateTo: (NavigationDestination) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.settings_label))
                        if (state.isUpgraded) {
                            ColoredTitleText(
                                fullTitle = stringResource(R.string.app_name_upgraded),
                                postfix = stringResource(R.string.app_name_upgrade_postfix),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.general_back_action)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.Top
        ) {
            item {
                SettingsBaseItem(
                    icon = Icons.TwoTone.Settings,
                    title = stringResource(R.string.general_settings_label),
                    subtitle = stringResource(R.string.general_settings_desc),
                    onClick = { onNavigateTo(Nav.Settings.General) },
                )
                SettingsDivider()
            }

            item {
                SettingsBaseItem(
                    icon = Icons.TwoTone.Devices,
                    title = stringResource(R.string.devices_settings_label),
                    subtitle = stringResource(R.string.devices_settings_desc),
                    onClick = { onNavigateTo(Nav.Settings.Devices) },
                )
                SettingsDivider()
            }

            item { SettingsCategoryHeader(stringResource(R.string.settings_category_other_label)) }

            if (!state.isUpgraded) {
                item {
                    SettingsBaseItem(
                        icon = Icons.TwoTone.Stars,
                        title = stringResource(R.string.upgrade_prompt_title),
                        subtitle = stringResource(R.string.upgrade_prompt_body),
                        onClick = { onNavigateTo(Nav.Main.Upgrade) },
                    )
                    SettingsDivider()
                }
            }

            item {
                SettingsBaseItem(
                    icon = Icons.TwoTone.Info,
                    title = stringResource(R.string.settings_support_label),
                    subtitle = stringResource(R.string.settings_support_description),
                    onClick = { onNavigateTo(Nav.Settings.Support) },
                )
                SettingsDivider()
            }

            item {
                SettingsBaseItem(
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    title = stringResource(R.string.changelog_label),
                    subtitle = state.versionText,
                    onClick = { onOpenUrl(BlueMusicLinks.CHANGELOG) },
                )
                SettingsDivider()
            }

            item {
                SettingsBaseItem(
                    icon = Icons.TwoTone.Favorite,
                    title = stringResource(R.string.settings_acknowledgements_label),
                    subtitle = stringResource(R.string.settings_acknowledgements_description),
                    onClick = { onNavigateTo(Nav.Settings.Acks) },
                )
                SettingsDivider()
            }

            item {
                SettingsBaseItem(
                    icon = Icons.TwoTone.PrivacyTip,
                    title = stringResource(R.string.settings_privacy_policy_label),
                    subtitle = stringResource(R.string.settings_privacy_policy_desc),
                    onClick = { onOpenUrl(BlueMusicLinks.PRIVACY_POLICY) },
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SettingsIndexScreenPreview() {
    PreviewWrapper {
        SettingsIndexScreen(
            state = SettingsViewModel.State(
                isUpgraded = true // TODO: Preview with upgrade status
            ),
            onNavigateUp = {},
            onNavigateTo = {},
            onOpenUrl = {},
        )
    }
}
