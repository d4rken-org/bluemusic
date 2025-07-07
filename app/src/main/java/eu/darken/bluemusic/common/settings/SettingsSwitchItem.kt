package eu.darken.bluemusic.common.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsBaseItem(
        icon = icon,
        title = title,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        subtitle = subtitle,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    )
}

@Preview2
@Composable
private fun SettingsSwitchItemPreview() {
    PreviewWrapper {
        SettingsSwitchItem(
            icon = Icons.Default.Settings,
            title = "Settings",
            subtitle = "General settings",
            checked = true,
            onCheckedChange = {},
            modifier = Modifier,
        )
    }
}