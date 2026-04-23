package eu.darken.bluemusic.devices.ui.dashboard.rows.device

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Launch
import androidx.compose.material.icons.automirrored.twotone.QueueMusic
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.twotone.DoNotDisturb
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.NotificationsActive
import androidx.compose.material.icons.twotone.PowerOff
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.common.apps.AppInfo
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.ui.AutoplayKeycodeOption
import eu.darken.bluemusic.devices.ui.AutoplayKeycodes
import eu.darken.bluemusic.monitor.core.alert.AlertType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionIndicators(
    device: ManagedDevice,
    launchApps: List<AppInfo> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val indicators: List<Pair<ImageVector, String>> = buildList {
        if (device.volumeLock) add(Icons.TwoTone.Lock to stringResource(R.string.devices_indicator_lock))
        if (device.keepAwake) add(Icons.TwoTone.BatteryFull to stringResource(R.string.devices_indicator_awake))
        if (device.nudgeVolume) add(Icons.TwoTone.GraphicEq to stringResource(R.string.devices_indicator_nudge))
        if (device.volumeObservingEffective) add(Icons.TwoTone.Visibility to stringResource(R.string.devices_indicator_observe))
        if (device.volumeSaveOnDisconnect) add(Icons.TwoTone.PowerOff to stringResource(R.string.devices_indicator_disconnect))
        if (device.volumeRateLimiterEffective) add(Icons.TwoTone.Speed to stringResource(R.string.devices_indicator_limit))
        autoplayChip(device)?.let { add(it) }
        if (device.showHomeScreen) add(Icons.TwoTone.Home to stringResource(R.string.devices_indicator_home))
        if (device.dndMode != null) add(Icons.TwoTone.DoNotDisturb to stringResource(R.string.devices_indicator_dnd))
        if (device.connectionAlertType != AlertType.NONE) add(Icons.TwoTone.NotificationsActive to stringResource(R.string.devices_indicator_alert))
    }

    val hasLaunchApps = launchApps.isNotEmpty()

    if (indicators.isNotEmpty() || hasLaunchApps) {
        FlowRow(
            modifier = modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Show app icons first
            if (hasLaunchApps) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.TwoTone.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    AppIconsRow(
                        appInfos = launchApps,
                        maxIcons = 3
                    )
                }
            }
            indicators.forEach { (icon, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun autoplayChip(device: ManagedDevice): Pair<ImageVector, String>? {
    if (!device.autoplay) return null
    val codes = device.autoplayKeycodes
    if (codes.isEmpty()) return null

    val singleKnown = codes.singleOrNull()?.let {
        AutoplayKeycodes.resolve(it) as? AutoplayKeycodeOption.Known
    }
    return if (singleKnown != null) {
        singleKnown.icon to singleKnown.label()
    } else {
        // Multiple codes OR a single unknown code → fall back to count form so we never invent a label.
        Icons.AutoMirrored.TwoTone.QueueMusic to pluralStringResource(R.plurals.devices_indicator_auto_count, codes.size, codes.size)
    }
}

@Preview2
@Composable
private fun OptionIndicatorsPreview() {
    PreviewWrapper {
        // The mock device already has these settings enabled by default
        val mockDevice = MockDevice().toManagedDevice()

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
            )
        )

        OptionIndicators(
            device = mockDevice,
            launchApps = mockApps,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview2
@Composable
private fun OptionIndicatorsAutoplayVariantsPreview() {
    PreviewWrapper {
        val base = MockDevice().toManagedDevice()
        val singlePlay = base.copy(config = base.config.copy(autoplay = true, autoplayKeycodes = listOf(KeyEvent.KEYCODE_MEDIA_PLAY)))
        val singleNext = base.copy(config = base.config.copy(autoplay = true, autoplayKeycodes = listOf(KeyEvent.KEYCODE_MEDIA_NEXT)))
        val singleFastForward = base.copy(config = base.config.copy(autoplay = true, autoplayKeycodes = listOf(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)))
        val multi = base.copy(config = base.config.copy(autoplay = true, autoplayKeycodes = listOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS)))
        val empty = base.copy(config = base.config.copy(autoplay = true, autoplayKeycodes = emptyList()))
        val unknown = base.copy(config = base.config.copy(autoplay = true, autoplayKeycodes = listOf(9999)))

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OptionIndicators(device = singlePlay)
            OptionIndicators(device = singleNext)
            OptionIndicators(device = singleFastForward)
            OptionIndicators(device = multi)
            OptionIndicators(device = empty)
            OptionIndicators(device = unknown)
        }
    }
}
