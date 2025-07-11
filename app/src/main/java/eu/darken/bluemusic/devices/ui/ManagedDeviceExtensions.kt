package eu.darken.bluemusic.devices.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Alarm
import androidx.compose.material.icons.twotone.MusicNote
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.Phone
import androidx.compose.material.icons.twotone.PhoneInTalk
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.bluemusic.main.core.audio.AudioStream


val AudioStream.Type.icon: ImageVector
    get() = when (this) {
        AudioStream.Type.MUSIC -> Icons.TwoTone.MusicNote
        AudioStream.Type.CALL -> Icons.TwoTone.PhoneInTalk
        AudioStream.Type.RINGTONE -> Icons.TwoTone.Phone
        AudioStream.Type.NOTIFICATION -> Icons.TwoTone.Notifications
        AudioStream.Type.ALARM -> Icons.TwoTone.Alarm
    }