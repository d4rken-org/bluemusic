package eu.darken.bluemusic.common.theming

import androidx.compose.material3.ColorScheme

object ThemeColorProvider {

    fun getLightColorScheme(color: ThemeColor, style: ThemeStyle): ColorScheme = when (color) {
        ThemeColor.BLUE -> BlueMusicColorsBlue.lightScheme(style)
        ThemeColor.SUNSET -> BlueMusicColorsSunset.lightScheme(style)
    }

    fun getDarkColorScheme(color: ThemeColor, style: ThemeStyle): ColorScheme = when (color) {
        ThemeColor.BLUE -> BlueMusicColorsBlue.darkScheme(style)
        ThemeColor.SUNSET -> BlueMusicColorsSunset.darkScheme(style)
    }
}
