package eu.darken.bluemusic.common.theming

import androidx.compose.runtime.Immutable

@Immutable
data class ThemeState(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val style: ThemeStyle = ThemeStyle.DEFAULT,
    val color: ThemeColor = ThemeColor.BLUE,
)
