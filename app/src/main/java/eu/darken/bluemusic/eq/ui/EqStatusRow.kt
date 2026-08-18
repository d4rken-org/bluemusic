package eu.darken.bluemusic.eq.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import eu.darken.bluemusic.R
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.PreviewWrapper

/**
 * What the equalizer is doing right now, in one line. The row keeps a minimum height so the content
 * around it doesn't jump while sessions come and go.
 */
@Composable
fun EqStatusRow(
    status: EqStatus,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.heightIn(min = ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EqAppIcon(icon = status.app?.icon, size = ICON_SIZE)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = eqStatusLine(status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The app an [EqStatus] is about, if it names one. */
val EqStatus.app: EqStatusApp?
    get() = when (this) {
        is EqStatus.Active -> app
        is EqStatus.NoControl -> app
        else -> null
    }

/** The label to show for an app we could not resolve, and for a session that named none. */
@Composable
fun eqAppLabel(app: EqStatusApp?): String = app?.label ?: stringResource(R.string.eq_status_generic_app_label)

/** The one-line summary, the same wherever the status shows up. */
@Composable
fun eqStatusLine(status: EqStatus): String = when (status) {
    is EqStatus.Active -> when {
        status.multiple -> stringResource(R.string.eq_status_active_multiple_label)
        else -> stringResource(R.string.eq_status_active_label, eqAppLabel(status.app))
    }

    is EqStatus.NoControl -> stringResource(R.string.eq_status_no_control_label, eqAppLabel(status.app))
    EqStatus.Waiting -> stringResource(R.string.eq_status_waiting_label)
    EqStatus.InactiveForDevice -> stringResource(R.string.eq_status_inactive_label)
}

/** The app's own icon where we have it, a music note where we don't. */
@Composable
fun EqAppIcon(
    icon: Drawable?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (icon == null) {
        Icon(
            imageVector = Icons.TwoTone.MusicNote,
            contentDescription = null,
            modifier = modifier.size(size),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val bitmap = remember(icon) {
        icon.toBitmap(
            width = icon.intrinsicWidth.coerceAtLeast(1),
            height = icon.intrinsicHeight.coerceAtLeast(1),
        ).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

private val ROW_HEIGHT = 32.dp
private val ICON_SIZE = 20.dp

@Preview2
@Composable
private fun EqStatusRowActivePreview() {
    PreviewWrapper {
        EqStatusRow(status = EqStatus.Active(EqStatusApp("com.spotify.music", label = "Spotify"), multiple = false))
    }
}

@Preview2
@Composable
private fun EqStatusRowMultiplePreview() {
    PreviewWrapper {
        EqStatusRow(status = EqStatus.Active(EqStatusApp("com.spotify.music", label = "Spotify"), multiple = true))
    }
}

@Preview2
@Composable
private fun EqStatusRowNoControlPreview() {
    PreviewWrapper {
        EqStatusRow(status = EqStatus.NoControl(app = null))
    }
}

@Preview2
@Composable
private fun EqStatusRowWaitingPreview() {
    PreviewWrapper {
        EqStatusRow(status = EqStatus.Waiting)
    }
}
