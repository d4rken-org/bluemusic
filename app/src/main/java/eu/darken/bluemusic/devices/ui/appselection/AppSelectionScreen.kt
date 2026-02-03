package eu.darken.bluemusic.devices.ui.appselection

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.apps.AppInfo
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import eu.darken.bluemusic.common.compose.navigationBarBottomPadding
import eu.darken.bluemusic.common.ui.waitForState
import eu.darken.bluemusic.devices.core.DeviceAddr

@Composable
fun AppSelectionScreenHost(
    addr: DeviceAddr,
    vm: AppSelectionViewModel = hiltViewModel(
        key = addr,
        creationCallback = { factory: AppSelectionViewModel.Factory -> factory.create(deviceAddress = addr) }
    ),
) {
    val state by waitForState(vm.state)

    state?.let { state ->
        AppSelectionScreen(
            state = state,
            onSearchQueryChanged = vm::onSearchQueryChanged,
            onAppToggled = vm::onAppToggled,
            onClearSelection = vm::onClearSelection,
            onNavigateBack = { vm.navUp() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppSelectionScreen(
    state: AppSelectionViewModel.State,
    onSearchQueryChanged: (String) -> Unit,
    onAppToggled: (String) -> Unit,
    onClearSelection: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val searchFocusRequester = remember { FocusRequester() }
    val (localSearchQuery, setLocalSearchQuery) = remember { mutableStateOf(state.searchQuery) }

    // Sync local state with external state
    LaunchedEffect(state.searchQuery) {
        if (localSearchQuery != state.searchQuery) {
            setLocalSearchQuery(state.searchQuery)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.devices_app_selection_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = state.deviceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.general_navigate_back_action)
                        )
                    }
                },
                actions = {
                    if (state.selectedPackages.isNotEmpty()) {
                        TextButton(
                            onClick = onClearSelection,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(R.string.general_clear_action))
                        }
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.statusBars
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .horizontalCutoutPadding()
        ) {
            // Search bar
            OutlinedTextField(
                value = localSearchQuery,
                onValueChange = { newQuery ->
                    setLocalSearchQuery(newQuery)
                    onSearchQueryChanged(newQuery)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(searchFocusRequester),
                placeholder = { Text(stringResource(R.string.devices_app_selection_search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (localSearchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            setLocalSearchQuery("")
                            onSearchQueryChanged("")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.general_clear_action)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Show selected apps
            if (state.selectedPackages.isNotEmpty()) {
                val selectedApps = state.apps.filter { app ->
                    app.packageName in state.selectedPackages
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.devices_app_selection_selected_count,
                                state.selectedPackages.size
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )

                        if (selectedApps.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                selectedApps.forEach { app ->
                                    SelectedAppChip(
                                        app = app,
                                        onRemove = { onAppToggled(app.packageName) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Loading indicator
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // App list
                val navBarPadding = navigationBarBottomPadding()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp + navBarPadding)
                ) {
                    items(
                        items = state.filteredApps,
                        key = { it.packageName }
                    ) { app ->
                        AppListItem(
                            app = app,
                            isSelected = state.selectedPackages.contains(app.packageName),
                            onAppSelected = { onAppToggled(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    app: AppInfo,
    isSelected: Boolean,
    onAppSelected: () -> Unit,
) {
    Card(
        onClick = onAppSelected,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app = app, size = 36)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onAppSelected() }
            )
        }
    }
}

@Composable
private fun AppIcon(app: AppInfo, size: Int = 48) {
    app.icon?.let { drawable ->
        val bitmap = remember(drawable) {
            drawable.toBitmap(
                width = drawable.intrinsicWidth.coerceAtLeast(1),
                height = drawable.intrinsicHeight.coerceAtLeast(1)
            ).asImageBitmap()
        }
        Image(
            bitmap = bitmap,
            contentDescription = app.label,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } ?: Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = app.label.firstOrNull()?.toString() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectedAppChip(
    app: AppInfo,
    onRemove: () -> Unit,
) {
    FilterChip(
        selected = true,
        onClick = onRemove,
        modifier = Modifier.height(28.dp),
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIcon(app = app, size = 20)
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 100.dp)
                )
            }
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = stringResource(R.string.general_clear_action),
                modifier = Modifier.size(16.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Preview2
@Composable
private fun AppSelectionScreenPreview() {
    PreviewWrapper {
        val mockApps = listOf(
            AppInfo(
                packageName = "com.spotify.music",
                label = "Spotify",
                icon = null
            ),
            AppInfo(
                packageName = "com.google.android.apps.youtube.music",
                label = "YouTube Music",
                icon = null
            ),
            AppInfo(
                packageName = "com.bambuna.podcastaddict",
                label = "Podcast Addict",
                icon = null
            ),
            AppInfo(
                packageName = "com.audible.application",
                label = "Audible",
                icon = null
            ),
            AppInfo(
                packageName = "com.soundcloud.android",
                label = "SoundCloud",
                icon = null
            ),
            AppInfo(
                packageName = "com.apple.android.music",
                label = "Apple Music",
                icon = null
            ),
            AppInfo(
                packageName = "deezer.android.app",
                label = "Deezer",
                icon = null
            ),
            AppInfo(
                packageName = "com.amazon.mp3",
                label = "Amazon Music",
                icon = null
            ),
            AppInfo(
                packageName = "com.aspiro.tidal",
                label = "Tidal",
                icon = null
            ),
            AppInfo(
                packageName = "com.pandora.android",
                label = "Pandora",
                icon = null
            )
        )

        AppSelectionScreen(
            state = AppSelectionViewModel.State(
                deviceName = "Sony WH-1000XM4",
                apps = mockApps,
                filteredApps = mockApps,
                selectedPackages = setOf(
                    "com.spotify.music",
                    "com.google.android.apps.youtube.music",
                    "com.bambuna.podcastaddict"
                ),
                searchQuery = "",
                isLoading = false
            ),
            onSearchQueryChanged = {},
            onAppToggled = {},
            onClearSelection = {},
            onNavigateBack = {}
        )
    }
}