package eu.darken.bluemusic.devices.ui.manage.rows.device

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import eu.darken.bluemusic.common.apps.AppInfo
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper

@Composable
fun AppIconsRow(
    appInfos: List<AppInfo>,
    modifier: Modifier = Modifier,
    maxIcons: Int = 3,
) {

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        appInfos.take(maxIcons).forEach { appInfo ->
            if (appInfo.icon != null) {
                val bitmap = remember(appInfo.icon) {
                    appInfo.icon.toBitmap(
                        width = appInfo.icon.intrinsicWidth.coerceAtLeast(1),
                        height = appInfo.icon.intrinsicHeight.coerceAtLeast(1)
                    ).asImageBitmap()
                }

                Image(
                    bitmap = bitmap,
                    contentDescription = appInfo.label,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            } else {
                // Show placeholder icon for apps without icons
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = appInfo.label.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Show +N if there are more apps
        val remainingCount = appInfos.size - maxIcons
        if (remainingCount > 0) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$remainingCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Preview2
@Composable
private fun AppIconsRowPreview() {
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
            )
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text("With 3 apps (default):")
            AppIconsRow(
                appInfos = mockApps.take(3)
            )

            Text("With 5 apps (showing +2):")
            AppIconsRow(
                appInfos = mockApps,
                maxIcons = 3
            )

            Text("With 2 apps:")
            AppIconsRow(
                appInfos = mockApps.take(2)
            )

            Text("Empty state:")
            AppIconsRow(
                appInfos = emptyList()
            )
        }
    }
}