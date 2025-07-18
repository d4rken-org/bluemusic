package eu.darken.bluemusic.devices.ui.manage.rows.device

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import eu.darken.bluemusic.common.AppTool
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppIconsRow(
    packageNames: List<String>,
    modifier: Modifier = Modifier,
    maxIcons: Int = 3,
) {
    val context = LocalContext.current
    var appInfos by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(packageNames) {
        appInfos = withContext(Dispatchers.IO) {
            packageNames.take(maxIcons + 1).mapNotNull { pkg ->
                try {
                    // TODO provide this via viewmodel directly, don't resolve here
                    val icon = AppTool.getIcon(context, pkg)
                    val label = AppTool.getLabel(context, pkg)
                    AppInfo(pkg, label, icon)
                } catch (e: Exception) {
                    // TODO Fallback to sane value
                    null
                }
            }
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        appInfos.take(maxIcons).forEach { appInfo ->
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
        }

        // Show +N if there are more apps
        val remainingCount = packageNames.size - maxIcons
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

private data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

@Preview2
@Composable
private fun AppIconsRowPreview() {
    PreviewWrapper {
        AppIconsRow(
            packageNames = listOf("app1", "app2", "app3", "app4")
        )
    }
}