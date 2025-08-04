package eu.darken.bluemusic.common.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper

@Composable
fun SettingsPreferenceItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
) {
    SettingsBaseItem(
        icon = icon,
        title = title,
        onClick = onClick,
        modifier = modifier,
        subtitle = subtitle,
        trailingContent = if (value != null) {
            {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        } else null
    )
}

@Preview2
@Composable
private fun SettingsPreferenceItemPreview() {
    PreviewWrapper {
        SettingsPreferenceItem(
            icon = Icons.TwoTone.Settings,
            title = "Settings",
            subtitle = "General settings",
            onClick = {},
            modifier = Modifier,
            value = "Value"
        )
    }
}