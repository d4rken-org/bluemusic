package eu.darken.bluemusic.bluetooth.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material.icons.twotone.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import eu.darken.bluemusic.bluetooth.core.MockDevice
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.speaker.FakeSpeakerDevice
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper

@Composable
fun DeviceItem(
    device: SourceDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                text = device.label,
                maxLines = 1,
                overflow = TextOverflow.Companion.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = device.address,
                maxLines = 1,
                overflow = TextOverflow.Companion.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                imageVector = when (device.address) {
                    FakeSpeakerDevice.Companion.address -> Icons.TwoTone.Phone
                    else -> Icons.TwoTone.Headphones
                },
                contentDescription = null
            )
        },
        modifier = modifier.clickable(onClick = onClick)
    )
}

@Preview2
@Composable
private fun DeviceItemPreview() {
    PreviewWrapper {
        DeviceItem(
            device = MockDevice(),
            onClick = {},
        )
    }
}