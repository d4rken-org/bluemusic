package eu.darken.bluemusic.common.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

@Composable
fun ColoredTitleText(
    fullTitle: String,
    postfix: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    baseColor: Color = MaterialTheme.colorScheme.primary,
    postfixColor: Color = MaterialTheme.colorScheme.tertiary
) {

    val annotatedString = buildAnnotatedString {
        val baseTitle = fullTitle.substringBeforeLast(postfix).trimEnd()

        withStyle(style = SpanStyle(color = baseColor)) {
            append(baseTitle)
        }

        if (baseTitle.isNotEmpty()) append(" ")

        withStyle(style = SpanStyle(color = postfixColor)) {
            append(postfix)
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        style = style
    )
}