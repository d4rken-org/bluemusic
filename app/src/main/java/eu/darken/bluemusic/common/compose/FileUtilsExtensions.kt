package eu.darken.bluemusic.common.compose

import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt()

    return String.format(
        Locale.ROOT,
        "%.1f %s",
        bytes / 1024.0.pow(digitGroups.toDouble()),
        units[digitGroups]
    )
}

@Composable
fun Instant.toRelativeTime(): String {
    val now = Instant.now()
    val duration = ChronoUnit.SECONDS.between(this, now)

    return when {
        duration < 60 -> "Just now"
        duration < 3600 -> "${duration / 60}m ago"
        duration < 86400 -> "${duration / 3600}h ago"
        duration < 604800 -> "${duration / 86400}d ago"
        duration < 2592000 -> "${duration / 604800}w ago"
        duration < 31536000 -> "${duration / 2592000}mo ago"
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(this)
    }
}