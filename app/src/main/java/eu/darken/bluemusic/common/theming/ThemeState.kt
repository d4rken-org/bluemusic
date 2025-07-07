package eu.darken.bluemusic.common.theming

import eu.darken.bluemusic.common.theming.ThemeMode
import eu.darken.bluemusic.common.theming.ThemeStyle

data class ThemeState(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val style: ThemeStyle = ThemeStyle.DEFAULT,
)
