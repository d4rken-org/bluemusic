package eu.darken.bluemusic.devices.ui.config.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.common.compose.PreviewWrapper

@Composable
fun SwitchPreference(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    requiresPro: Boolean = false,
    isProVersion: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading icon
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        // Title and description
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Switch or Upgrade icon
        if (requiresPro && !isProVersion) {
            Icon(
                imageVector = Icons.TwoTone.Stars,
                contentDescription = "Pro feature",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp)
            )
        } else {
            Switch(
                checked = isChecked,
                onCheckedChange = null, // Disable direct switch interaction
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

@Composable
fun ClickablePreference(
    title: String,
    description: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    textColor: Color? = null,
    modifier: Modifier = Modifier,
    requiresPro: Boolean = false,
    isProVersion: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )

        // Title and description
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor ?: MaterialTheme.colorScheme.onSurface
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Upgrade icon for pro features
        if (requiresPro && !isProVersion) {
            Icon(
                imageVector = Icons.TwoTone.Stars,
                contentDescription = "Pro feature",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Preview
@Composable
private fun SwitchPreferencePreview() {
    PreviewWrapper {
        Column {
            SwitchPreference(
                title = "Volume Lock",
                description = "Lock volume at current level when connected",
                isChecked = true,
                icon = Icons.TwoTone.Lock,
                onCheckedChange = {}
            )
            SwitchPreference(
                title = "Nudge Volume",
                description = "Briefly change volume to confirm connection",
                isChecked = false,
                icon = Icons.TwoTone.GraphicEq,
                onCheckedChange = {}
            )
        }
    }
}

@Preview
@Composable
private fun ClickablePreferencePreview() {
    PreviewWrapper {
        Column {
            ClickablePreference(
                title = "Reaction Delay",
                description = "4000 ms",
                icon = Icons.TwoTone.Timer,
                onClick = {}
            )
            ClickablePreference(
                title = "Select App",
                description = "No app selected",
                icon = Icons.TwoTone.Timer,
                onClick = {}
            )
        }
    }
}

@Preview
@Composable
private fun SectionHeaderPreview() {
    PreviewWrapper {
        SectionHeader(title = "Volume Controls")
    }
}