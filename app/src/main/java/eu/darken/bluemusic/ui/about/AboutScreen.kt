package eu.darken.bluemusic.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.ui.theme.BlueMusicTheme
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    state: AboutState,
    onEvent: (AboutEvent) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_about)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.abc_action_bar_up_description)
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // App Info Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_bm_squircle),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = state.version,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            item { Divider(modifier = Modifier.padding(vertical = 8.dp)) }
            
            // Links Section
            item {
                LinkItem(
                    title = stringResource(R.string.privacy_policy_label),
                    description = "https://bluemusic.darken.eu/privacy",
                    onClick = {
                        openUrl(context, "https://bluemusic.darken.eu/privacy")
                    }
                )
            }
            
            item {
                LinkItem(
                    title = stringResource(R.string.label_gplay),
                    description = stringResource(R.string.description_check_out_my_apps),
                    onClick = {
                        openUrl(context, "https://play.google.com/store/apps/developer?id=darken")
                    }
                )
            }
            
            item {
                LinkItem(
                    title = stringResource(R.string.label_opensource),
                    description = "https://github.com/d4rken-org/bluemusic",
                    onClick = {
                        openUrl(context, "https://github.com/d4rken-org/bluemusic")
                    }
                )
            }
            
            // Contact Section
            item {
                SectionHeader(title = stringResource(R.string.label_contact))
            }
            
            item {
                LinkItem(
                    title = stringResource(R.string.label_website),
                    description = "darken.eu",
                    onClick = {
                        openUrl(context, "https://www.darken.eu")
                    }
                )
            }
            
            item {
                LinkItem(
                    title = stringResource(R.string.label_discord),
                    description = "https://discord.gg/PZR7M8p",
                    onClick = {
                        openUrl(context, "https://discord.gg/PZR7M8p")
                    }
                )
            }
            
            // Thanks Section
            item {
                SectionHeader(title = stringResource(R.string.label_thanks))
            }
            
            item {
                LinkItem(
                    title = "Max Patchs",
                    description = "For the lovely icon.",
                    onClick = {
                        openUrl(context, "https://twitter.com/250_max")
                    }
                )
            }
            
            // Licenses Section
            item {
                SectionHeader(title = stringResource(R.string.label_licenses))
            }
            
            item {
                LinkItem(
                    title = "Jetpack Compose",
                    description = "Modern Android UI toolkit (Apache 2.0)",
                    onClick = {
                        openUrl(context, "https://developer.android.com/jetpack/compose")
                    }
                )
            }
            
            item {
                LinkItem(
                    title = "Dagger2",
                    description = "A fast dependency injector for Android and Java. (Apache 2.0)",
                    onClick = {
                        openUrl(context, "https://github.com/google/dagger")
                    }
                )
            }
            
            item {
                LinkItem(
                    title = "Room",
                    description = "The Room persistence library provides an abstraction layer over SQLite (Apache 2.0)",
                    onClick = {
                        openUrl(context, "https://developer.android.com/training/data-storage/room")
                    }
                )
            }
            
            item {
                LinkItem(
                    title = "Kotlin Coroutines",
                    description = "Library support for Kotlin coroutines (Apache 2.0)",
                    onClick = {
                        openUrl(context, "https://github.com/Kotlin/kotlinx.coroutines")
                    }
                )
            }
            
            item {
                LinkItem(
                    title = "Timber",
                    description = "A logger with a small, extensible API which provides utility on top of Android's normal Log class. (Apache 2.0)",
                    onClick = {
                        openUrl(context, "https://github.com/JakeWharton/timber")
                    }
                )
            }
            
            item {
                LinkItem(
                    title = "Material Design Icons",
                    description = "materialdesignicons.com (SIL Open Font License 1.1 / Attribution 4.0 International)",
                    onClick = {
                        openUrl(context, "https://github.com/Templarian/MaterialDesign")
                    }
                )
            }
            
            item {
                LinkItem(
                    title = "Android",
                    description = "Android Open Source Project (Apache 2.0)",
                    onClick = {
                        openUrl(context, "https://source.android.com/source/licenses.html")
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun LinkItem(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        modifier = modifier.clickable(onClick = onClick)
    )
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Timber.e(e, "Failed to open URL: $url")
    }
}

@Preview
@Composable
private fun AboutScreenPreview() {
    BlueMusicTheme {
        AboutScreen(
            state = AboutState(
                version = "Version 2.59.1 (25901)"
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}