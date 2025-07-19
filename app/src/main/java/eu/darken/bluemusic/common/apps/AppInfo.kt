package eu.darken.bluemusic.common.apps

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null
)