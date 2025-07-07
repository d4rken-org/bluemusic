package eu.darken.bluemusic.upgrade.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.ButlerIcon
import eu.darken.bluemusic.common.compose.ColoredTitleText
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.error.ErrorEventHandler

@Composable
fun UpgradeScreenHost(vm: UpgradeViewModel = hiltViewModel()) {
    ErrorEventHandler(vm)
    UpgradeScreen(
        onNavigateBack = { vm.navUp() },
        onSponsorClick = { vm.openSponsor() }
    )
}

@Composable
fun UpgradeScreen(
    onNavigateBack: () -> Unit,
    onSponsorClick: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.upgrade_screen_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ButlerIcon(
                modifier = Modifier.padding(bottom = 8.dp),
                size = 96.dp
            )

            ColoredTitleText(
                fullTitle = stringResource(R.string.app_name_upgraded),
                postfix = stringResource(R.string.app_name_upgrade_postfix),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
            ) {
                Text(
                    text = stringResource(R.string.upgrade_screen_preamble),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.upgrade_screen_how_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = stringResource(R.string.upgrade_screen_how_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Text(
                    text = stringResource(R.string.upgrade_screen_why_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = stringResource(R.string.upgrade_screen_why_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Button(
                    onClick = onSponsorClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) { Text(stringResource(R.string.upgrade_screen_sponsor_action)) }

                Text(
                    text = stringResource(R.string.upgrade_screen_sponsor_action_hint),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview2
@Composable
fun UpgradeScreenPreview() {
    PreviewWrapper {
        UpgradeScreen(
            onNavigateBack = {},
            onSponsorClick = {}
        )
    }
}
