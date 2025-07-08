package eu.darken.bluemusic.common.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.bluemusic.R

@Composable
fun BlueMusicIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = stringResource(R.string.bluemusic_mascot_description),
) {
    Image(
        painter = painterResource(id = R.drawable.mascot),
        contentDescription = contentDescription,
        modifier = modifier.size(size)
    )
}

@Preview2
@Composable
private fun ButlerIconPreview() {
    PreviewWrapper {
        BlueMusicIcon(size = 48.dp)
    }
}