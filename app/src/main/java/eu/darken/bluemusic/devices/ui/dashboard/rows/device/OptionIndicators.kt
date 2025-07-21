package eu.darken.bluemusic.devices.ui.dashboard.rows.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Launch
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.PowerOff
import androidx.compose.material.icons.twotone.Speed
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.common.apps.AppInfo
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.devices.core.ManagedDevice

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionIndicators(
    device: ManagedDevice,
    launchApps: List<AppInfo> = emptyList(),
    modifier: Modifier = Modifier
) {
    val indicators = buildList {
        if (device.volumeLock) add(Icons.TwoTone.Lock to stringResource(R.string.devices_device_config_volume_lock_label))
        if (device.keepAwake) add(Icons.TwoTone.BatteryFull to stringResource(R.string.devices_device_config_keep_awake_label))
        if (device.nudgeVolume) add(Icons.TwoTone.GraphicEq to stringResource(R.string.devices_device_config_nudge_volume_label))
        if (device.volumeObserving) add(Icons.TwoTone.Visibility to stringResource(R.string.devices_device_config_volume_observe_label))
        if (device.volumeSaveOnDisconnect) add(Icons.TwoTone.PowerOff to stringResource(R.string.devices_device_config_volume_save_on_disconnect_label))
        if (device.volumeRateLimiter) add(Icons.TwoTone.Speed to stringResource(R.string.devices_device_config_volume_rate_limiter_label))
        if (device.autoplay) add(Icons.TwoTone.PlayArrow to "Autoplay")
        if (device.showHomeScreen) add(Icons.TwoTone.Home to stringResource(R.string.devices_device_config_show_home_screen_label))
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
            indicators.forEach { (icon, _) ->
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
                        text = when (icon) {
                            Icons.TwoTone.Lock -> "Lock"
                            Icons.TwoTone.BatteryFull -> "Awake"
                            Icons.TwoTone.GraphicEq -> "Nudge"
                            Icons.TwoTone.Visibility -> "Observe"
                            Icons.TwoTone.PowerOff -> "Disconnect"
                            Icons.TwoTone.Speed -> "Limit"
                            Icons.AutoMirrored.TwoTone.Launch -> "Launch"
                            Icons.TwoTone.PlayArrow -> "Auto"
                            Icons.TwoTone.Home -> "Home"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
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