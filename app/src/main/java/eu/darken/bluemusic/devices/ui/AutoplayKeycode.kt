package eu.darken.bluemusic.devices.ui

import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.HelpOutline
import androidx.compose.material.icons.twotone.FastForward
import androidx.compose.material.icons.twotone.FastRewind
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.PlayCircle
import androidx.compose.material.icons.twotone.SkipNext
import androidx.compose.material.icons.twotone.SkipPrevious
import androidx.compose.material.icons.twotone.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import eu.darken.bluemusic.R

sealed interface AutoplayKeycodeOption {
    val keycode: Int
    val icon: ImageVector

    @Composable
    fun label(): String

    data class Known(
        override val keycode: Int,
        @StringRes val labelRes: Int,
        @StringRes val descriptionRes: Int,
        override val icon: ImageVector,
    ) : AutoplayKeycodeOption {
        @Composable
        override fun label(): String = stringResource(labelRes)

        @Composable
        fun description(): String = stringResource(descriptionRes)
    }

    data class Unknown(
        override val keycode: Int,
    ) : AutoplayKeycodeOption {
        override val icon: ImageVector = Icons.AutoMirrored.TwoTone.HelpOutline

        @Composable
        override fun label(): String = stringResource(R.string.devices_autoplay_keycode_unknown_label, keycode)
    }
}

object AutoplayKeycodes {
    val knownCodes: List<AutoplayKeycodeOption.Known> = listOf(
        AutoplayKeycodeOption.Known(
            keycode = KeyEvent.KEYCODE_MEDIA_PLAY,
            labelRes = R.string.devices_autoplay_keycode_play_label,
            descriptionRes = R.string.devices_autoplay_keycode_play_desc,
            icon = Icons.TwoTone.PlayArrow,
        ),
        AutoplayKeycodeOption.Known(
            keycode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            labelRes = R.string.devices_autoplay_keycode_play_pause_label,
            descriptionRes = R.string.devices_autoplay_keycode_play_pause_desc,
            icon = Icons.TwoTone.PlayCircle,
        ),
        AutoplayKeycodeOption.Known(
            keycode = KeyEvent.KEYCODE_MEDIA_NEXT,
            labelRes = R.string.devices_autoplay_keycode_next_label,
            descriptionRes = R.string.devices_autoplay_keycode_next_desc,
            icon = Icons.TwoTone.SkipNext,
        ),
        AutoplayKeycodeOption.Known(
            keycode = KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            labelRes = R.string.devices_autoplay_keycode_previous_label,
            descriptionRes = R.string.devices_autoplay_keycode_previous_desc,
            icon = Icons.TwoTone.SkipPrevious,
        ),
        AutoplayKeycodeOption.Known(
            keycode = KeyEvent.KEYCODE_MEDIA_STOP,
            labelRes = R.string.devices_autoplay_keycode_stop_label,
            descriptionRes = R.string.devices_autoplay_keycode_stop_desc,
            icon = Icons.TwoTone.Stop,
        ),
        AutoplayKeycodeOption.Known(
            keycode = KeyEvent.KEYCODE_MEDIA_REWIND,
            labelRes = R.string.devices_autoplay_keycode_rewind_label,
            descriptionRes = R.string.devices_autoplay_keycode_rewind_desc,
            icon = Icons.TwoTone.FastRewind,
        ),
        AutoplayKeycodeOption.Known(
            keycode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            labelRes = R.string.devices_autoplay_keycode_fast_forward_label,
            descriptionRes = R.string.devices_autoplay_keycode_fast_forward_desc,
            icon = Icons.TwoTone.FastForward,
        ),
    )

    private val byKeycode: Map<Int, AutoplayKeycodeOption.Known> = knownCodes.associateBy { it.keycode }

    fun resolve(keycode: Int): AutoplayKeycodeOption =
        byKeycode[keycode] ?: AutoplayKeycodeOption.Unknown(keycode)
}
