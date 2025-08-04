package eu.darken.bluemusic.common

import android.app.PendingIntent

object PendingIntentCompat {
    @JvmStatic
    val FLAG_IMMUTABLE: Int = if (hasApiLevel(31)) {
        PendingIntent.FLAG_IMMUTABLE
    } else {
        0
    }
}