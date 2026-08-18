package eu.darken.bluemusic.eq.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.UpgradeBadge
import eu.darken.bluemusic.common.compose.horizontalCutoutPadding
import eu.darken.bluemusic.common.compose.navigationBarBottomPadding
import eu.darken.bluemusic.common.error.ErrorEventHandler
import eu.darken.bluemusic.common.navigation.Nav
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.ui.config.components.SectionHeader
import eu.darken.bluemusic.eq.core.EqCapabilities
import eu.darken.bluemusic.eq.core.EqEffectController.Companion.MAX_BOOST_GAIN_MB
import eu.darken.bluemusic.eq.core.EqPresets
import eu.darken.bluemusic.eq.core.levelsOf
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DeviceEqScreenHost(
    addr: DeviceAddr,
    vm: DeviceEqViewModel = hiltViewModel(
        key = addr,
        creationCallback = { factory: DeviceEqViewModel.Factory -> factory.create(deviceAddress = addr) }
    ),
) {
    ErrorEventHandler(vm)

    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                is DeviceEqViewModel.Event.RequiresPro -> vm.navTo(Nav.Main.Upgrade())
            }
        }
    }

    state?.let {
        DeviceEqScreen(
            state = it,
            onNavigateBack = { vm.navUp() },
            onToggleEq = { vm.onToggleEq() },
            onLevelsChanged = { levels -> vm.onLevelsChanged(levels) },
            onLevelsCommitted = { levels -> vm.onLevelsCommitted(levels) },
            onPresetSelected = { preset -> vm.applyPreset(preset) },
            onBoostChanged = { gain -> vm.onBoostChanged(gain) },
            onBoostCommitted = { gain -> vm.onBoostCommitted(gain) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceEqScreen(
    state: DeviceEqViewModel.State,
    onNavigateBack: () -> Unit,
    onToggleEq: () -> Unit,
    onLevelsChanged: (List<Int>) -> Unit,
    onLevelsCommitted: (List<Int>) -> Unit,
    onPresetSelected: (EqPresets.Id) -> Unit,
    onBoostChanged: (Int) -> Unit,
    onBoostCommitted: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val capabilities = state.capabilities
    val storedLevels = capabilities.levelsOf(state.device.eqBandLevels)
    val storedBoost = (state.device.eqBoostGain ?: 0).coerceIn(0, MAX_BOOST_GAIN_MB)

    // Slider drags only live here, they are handed to the ViewModel as a preview and persisted on release.
    var draggedLevels by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(storedLevels) { draggedLevels = null }
    val levels = draggedLevels ?: storedLevels

    var draggedBoost by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(storedBoost) { draggedBoost = null }
    val boost = draggedBoost ?: storedBoost

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.eq_screen_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = state.device.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.general_navigate_back_action),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { paddingValues ->
        val navBarPadding = navigationBarBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .horizontalCutoutPadding(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + navBarPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (capabilities == null) {
                item { UnsupportedCard() }
                return@LazyColumn
            }

            item {
                EnableCard(
                    eqEnabled = state.device.eqEnabled,
                    isProVersion = state.isProVersion,
                    onToggleEq = onToggleEq,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            state.status?.let { status ->
                item {
                    StatusRow(
                        status = status,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.eq_bands_label),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                        val context = LocalContext.current
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.presets.forEach { preset ->
                                FilterChip(
                                    selected = levels == preset.levels,
                                    onClick = { onPresetSelected(preset.id) },
                                    label = { Text(preset.label.get(context)) },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val bands = levels.mapIndexed { index, level ->
                            EqBandUi(
                                frequencyLabel = formatFrequency(capabilities.centerFrequencies.getOrElse(index) { 0 }),
                                gainLabel = formatGain(level),
                                level = level,
                            )
                        }
                        EqBandRow(
                            bands = bands,
                            minLevel = capabilities.minLevel,
                            maxLevel = capabilities.maxLevel,
                            onLevelChange = { index, newLevel ->
                                val updated = levels.toMutableList().also { it[index] = newLevel }
                                draggedLevels = updated
                                onLevelsChanged(updated)
                            },
                            onLevelChangeFinished = { onLevelsCommitted(draggedLevels ?: levels) },
                            // Narrower than the 16dp the bare screen used: the card already insets the
                            // row, and the bands need the width back to stay wide enough to grab.
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        BoostSectionHeader()

                        BoostSlider(
                            gain = boost,
                            onGainChange = { newGain ->
                                draggedBoost = newGain
                                onBoostChanged(newGain)
                            },
                            onGainChangeFinished = { onBoostCommitted(draggedBoost ?: boost) },
                        )

                        Text(
                            text = stringResource(R.string.eq_boost_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            item { InfoCard() }
        }
    }
}

/**
 * What the equalizer is doing right now. The row keeps a fixed minimum height so the content below
 * it doesn't jump around while sessions come and go.
 */
@Composable
private fun StatusRow(
    status: EqStatus,
    modifier: Modifier = Modifier,
) {
    val app = when (status) {
        is EqStatus.Active -> status.app
        is EqStatus.NoControl -> status.app
        else -> null
    }
    val appLabel = app?.label ?: stringResource(R.string.eq_status_generic_app_label)
    val text = when (status) {
        is EqStatus.Active -> when {
            status.multiple -> stringResource(R.string.eq_status_active_multiple_label)
            else -> stringResource(R.string.eq_status_active_label, appLabel)
        }

        is EqStatus.NoControl -> stringResource(R.string.eq_status_no_control_label, appLabel)
        EqStatus.Waiting -> stringResource(R.string.eq_status_waiting_label)
        EqStatus.InactiveForDevice -> stringResource(R.string.eq_status_inactive_label)
    }

    Row(
        modifier = modifier.heightIn(min = STATUS_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIcon(icon = app?.icon)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusIcon(icon: Drawable?) {
    if (icon == null) {
        Icon(
            imageVector = Icons.TwoTone.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(STATUS_ICON_SIZE),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val bitmap = remember(icon) {
        icon.toBitmap(
            width = icon.intrinsicWidth.coerceAtLeast(1),
            height = icon.intrinsicHeight.coerceAtLeast(1),
        ).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier.size(STATUS_ICON_SIZE),
    )
}

@Composable
private fun BoostSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.eq_boost_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BoostSlider(
    gain: Int,
    onGainChange: (Int) -> Unit,
    onGainChangeFinished: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = gain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            onValueChangeFinished = onGainChangeFinished,
            valueRange = 0f..MAX_BOOST_GAIN_MB.toFloat(),
            steps = BOOST_STEPS,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatBoost(gain),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp),
        )
    }
}

@Composable
private fun UnsupportedCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = stringResource(R.string.eq_unsupported_msg),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * The whole row is one toggle target, so the switch itself takes no click of its own: two
 * accessibility actions for the same thing would only be read out twice.
 */
@Composable
private fun EnableCard(
    eqEnabled: Boolean,
    isProVersion: Boolean,
    onToggleEq: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = eqEnabled,
                    role = Role.Switch,
                    onValueChange = { onToggleEq() },
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.eq_enable_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                // The sliders stay editable while the equalizer is off, so this is the only thing
                // telling the user why nothing they change is audible yet.
                if (!eqEnabled) {
                    Text(
                        text = stringResource(R.string.eq_disabled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!isProVersion) {
                UpgradeBadge(modifier = Modifier.padding(horizontal = 8.dp))
            }
            Switch(
                checked = eqEnabled,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = Icons.TwoTone.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.eq_info_msg),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun formatFrequency(milliHertz: Int): String {
    val hertz = milliHertz / 1000
    if (hertz < 1000) return stringResource(R.string.eq_frequency_hz_label, hertz.toString())
    val kilohertz = hertz / 1000f
    val formatted = if (kilohertz >= 10f) {
        kilohertz.roundToInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", kilohertz)
    }
    return stringResource(R.string.eq_frequency_khz_label, formatted)
}

/** A band gain the way the equalizer screen writes it, e.g. `+3.0 dB`. */
@Composable
internal fun formatGain(millibel: Int): String =
    stringResource(R.string.eq_gain_db_label, String.format(Locale.getDefault(), "%+.1f", millibel / 100f))

@Composable
private fun formatBoost(millibel: Int): String =
    stringResource(R.string.eq_gain_db_label, String.format(Locale.getDefault(), "%.1f", millibel / 100f))

/** Slider stops between the ends, so the slider moves in 1 dB steps. */
private const val BOOST_STEPS = 9

private val STATUS_ROW_HEIGHT = 32.dp
private val STATUS_ICON_SIZE = 20.dp

private val previewCaps = EqCapabilities.Caps(
    bandCount = 5,
    minLevel = -1500,
    maxLevel = 1500,
    centerFrequencies = listOf(60_000, 230_000, 910_000, 3_600_000, 14_000_000),
)

private fun previewState(
    capabilities: EqCapabilities.Caps? = previewCaps,
    eqEnabled: Boolean = true,
    levels: List<Int>? = listOf(900, 300, 0, -300, 600),
    boostGain: Int? = 300,
    isProVersion: Boolean = true,
    status: EqStatus? = EqStatus.Active(EqStatusApp("com.spotify.music", label = "Spotify"), multiple = false),
): DeviceEqViewModel.State {
    val device = MockDevice(label = "Sony WH-1000XM5", address = "AA:BB:CC:DD:EE:01")
        .toManagedDevice(isConnected = true)
    return DeviceEqViewModel.State(
        device = device.copy(
            config = device.config.copy(
                eqEnabled = eqEnabled,
                eqBandLevels = levels,
                eqBoostGain = boostGain,
            )
        ),
        capabilities = capabilities,
        presets = EqPresets().let { presets ->
            capabilities?.let { caps ->
                presets.presets.map { DeviceEqViewModel.PresetOption(it.id, it.label, presets.levelsFor(it.curve, caps)) }
            }
        } ?: emptyList(),
        isProVersion = isProVersion,
        status = status,
    )
}

@Preview2
@Composable
private fun DeviceEqScreenPreview() {
    PreviewWrapper {
        DeviceEqScreen(
            state = previewState(),
            onNavigateBack = {},
            onToggleEq = {},
            onLevelsChanged = {},
            onLevelsCommitted = {},
            onPresetSelected = {},
            onBoostChanged = {},
            onBoostCommitted = {},
        )
    }
}

@Preview2
@Composable
private fun DeviceEqScreenWaitingPreview() {
    PreviewWrapper {
        DeviceEqScreen(
            state = previewState(status = EqStatus.Waiting),
            onNavigateBack = {},
            onToggleEq = {},
            onLevelsChanged = {},
            onLevelsCommitted = {},
            onPresetSelected = {},
            onBoostChanged = {},
            onBoostCommitted = {},
        )
    }
}

@Preview2
@Composable
private fun DeviceEqScreenNoControlPreview() {
    PreviewWrapper {
        DeviceEqScreen(
            state = previewState(status = EqStatus.NoControl(app = null)),
            onNavigateBack = {},
            onToggleEq = {},
            onLevelsChanged = {},
            onLevelsCommitted = {},
            onPresetSelected = {},
            onBoostChanged = {},
            onBoostCommitted = {},
        )
    }
}

@Preview2
@Composable
private fun DeviceEqScreenDisabledPreview() {
    PreviewWrapper {
        DeviceEqScreen(
            state = previewState(eqEnabled = false, levels = null, boostGain = null, status = null),
            onNavigateBack = {},
            onToggleEq = {},
            onLevelsChanged = {},
            onLevelsCommitted = {},
            onPresetSelected = {},
            onBoostChanged = {},
            onBoostCommitted = {},
        )
    }
}

@Preview2
@Composable
private fun DeviceEqScreenFreePreview() {
    PreviewWrapper {
        DeviceEqScreen(
            state = previewState(eqEnabled = false, levels = null, boostGain = null, isProVersion = false, status = null),
            onNavigateBack = {},
            onToggleEq = {},
            onLevelsChanged = {},
            onLevelsCommitted = {},
            onPresetSelected = {},
            onBoostChanged = {},
            onBoostCommitted = {},
        )
    }
}

@Preview2
@Composable
private fun DeviceEqScreenUnsupportedPreview() {
    PreviewWrapper {
        DeviceEqScreen(
            state = previewState(capabilities = null, eqEnabled = false, levels = null, boostGain = null, status = null),
            onNavigateBack = {},
            onToggleEq = {},
            onLevelsChanged = {},
            onLevelsCommitted = {},
            onPresetSelected = {},
            onBoostChanged = {},
            onBoostCommitted = {},
        )
    }
}
