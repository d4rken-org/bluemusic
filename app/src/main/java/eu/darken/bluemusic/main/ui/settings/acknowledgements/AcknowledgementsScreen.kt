package eu.darken.bluemusic.main.ui.settings.acknowledgements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.settings.SettingsBaseItem
import eu.darken.bluemusic.common.settings.SettingsCategoryHeader
import eu.darken.bluemusic.common.settings.SettingsDivider
import eu.darken.bluemusic.common.ui.waitForState

@Composable
fun AcknowledgementsScreen(
    state: AcknowledgementsScreenViewModel.State,
    onNavigateUp: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_acknowledgements_label)) },
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
                SettingsCategoryHeader(stringResource(R.string.settings_acks_translation_header))
            }

            item {
                SettingsBaseItem(
                    icon = Icons.TwoTone.Translate,
                    title = stringResource(R.string.settings_acks_translate_title),
                    subtitle = stringResource(R.string.settings_acks_translate_desc),
                    onClick = { onOpenUrl("http://crowdin.com/project/bluemusic") }
                )
                SettingsDivider()
            }

            item {
                SettingsBaseItem(
                    icon = Icons.TwoTone.Favorite,
                    title = stringResource(R.string.settings_acks_translators_title),
                    subtitle = stringResource(R.string.settings_acks_translators_people),
                    onClick = { onOpenUrl("http://crowdin.com/project/bluemusic") }
                )
            }

            item { SettingsCategoryHeader(stringResource(R.string.settings_acks_thanks_header)) }

            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_acks_crowdin_title),
                    subtitle = stringResource(R.string.settings_acks_crowdin_desc),
                    onClick = { onOpenUrl("http://crowdin.com/project/bluemusic") }
                )
            }

            item { SettingsCategoryHeader(stringResource(R.string.settings_licenses_label)) }

            item {
                SettingsBaseItem(
                    title = "Android",
                    subtitle = "Android Open Source Project (APACHE 2.0)",
                    onClick = { onOpenUrl("https://source.android.com/source/licenses.html") }
                )
                SettingsDivider()
            }

            item {
                SettingsBaseItem(
                    title = "Kotlin",
                    subtitle = "The Kotlin Programming Language. (APACHE 2.0)",
                    onClick = { onOpenUrl("https://github.com/JetBrains/kotlin") }
                )
                SettingsDivider()
            }

            item {
                SettingsBaseItem(
                    title = "Dagger",
                    subtitle = "A fast dependency injector for Android and Java. (APACHE 2.0)",
                    onClick = { onOpenUrl("https://github.com/google/dagger") }
                )
                SettingsDivider()
            }

            item {
                SettingsBaseItem(
                    title = "libRootJava",
                    subtitle = "Run Java (and Kotlin) code as root! (APACHE 2.0)",
                    onClick = { onOpenUrl("https://github.com/Chainfire/librootjava") }
                )
                SettingsDivider()
            }

            item {
                SettingsBaseItem(
                    title = "librootkotlinx",
                    subtitle =
                        "Run rooted Kotlin JVM code made easy with coroutines. (APACHE 2.0)",
                    onClick = { onOpenUrl("https://github.com/Mygod/librootkotlinx") }
                )
                SettingsDivider()
            }

            item {
                SettingsBaseItem(
                    title = "Shizuku",
                    subtitle =
                        "Using system APIs directly with adb/root privileges from normal apps through a Java process started with app_process. (APACHE 2.0)",
                    onClick = { onOpenUrl("https://github.com/RikkaApps/Shizuku") }
                )
                SettingsDivider()
            }

            item {
                SettingsBaseItem(
                    title = "Material Design Icons",
                    subtitle =
                        "materialdesignicons.com (SIL Open Font License 1.1 / Attribution 4.0 International)",
                    onClick = { onOpenUrl("https://github.com/Templarian/MaterialDesign") }
                )
                SettingsDivider()
            }

            item {
                SettingsBaseItem(
                    title = "Lottie",
                    subtitle = "Airbnb's Lottie for Android. (APACHE 2.0)",
                    onClick = { onOpenUrl("https://github.com/airbnb/lottie-android") }
                )
                SettingsDivider()
            }

            item {
                SettingsBaseItem(
                    title = "Android",
                    subtitle =
                        "The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.",
                    onClick = {
                        onOpenUrl(
                            "https://developer.android.com/distribute/tools/promote/brand.html"
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun AcknowledgementsScreenHost(vm: AcknowledgementsScreenViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)

    val state by waitForState(vm.state)

    state?.let { state ->
        AcknowledgementsScreen(
            state = state,
            onNavigateUp = { vm.navUp() },
            onOpenUrl = { url -> vm.openUrl(url) },
        )
    }
}

@Preview2
@Composable
private fun AcknowledgementsScreenPreview() {
    PreviewWrapper {
        AcknowledgementsScreen(
            state = AcknowledgementsScreenViewModel.State(),
            onNavigateUp = {},
            onOpenUrl = { _ -> },
        )
    }
}