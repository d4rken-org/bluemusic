package eu.darken.bluemusic.main.ui.settings.advanced

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.PreviewWrapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(
    state: AdvancedState,
    onEvent: (AdvancedEvent) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_advanced)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = ""
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
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                SwitchPreference(
                    title = stringResource(R.string.label_exclude_health),
                    description = stringResource(R.string.description_exclude_health),
                    isChecked = state.excludeHealthDevices,
                    onCheckedChange = { onEvent(AdvancedEvent.OnExcludeHealthDevicesToggled(it)) }
                )
            }
        }
    }
}

@Composable
private fun SwitchPreference(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = modifier
    )
}

@Preview
@Composable
private fun AdvancedScreenPreview() {
    PreviewWrapper {
        AdvancedScreen(
            state = AdvancedState(
                excludeHealthDevices = true
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}