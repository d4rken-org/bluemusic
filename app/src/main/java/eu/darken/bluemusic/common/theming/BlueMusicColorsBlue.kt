package eu.darken.bluemusic.common.theming

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object BlueMusicColorsBlue {

    val seed = Color(0xFF1976D2)

    // region Light Default
    val LightDefault = lightColorScheme(
        primary = Color(0xFF0060B0),
        onPrimary = Color(0xFFF8F8FF),
        primaryContainer = Color(0xFF5AA2FF),
        onPrimaryContainer = Color(0xFF002347),
        inversePrimary = Color(0xFF4E9AF9),
        secondary = Color(0xFF486084),
        onSecondary = Color(0xFFF8F8FF),
        secondaryContainer = Color(0xFFD4E3FF),
        onSecondaryContainer = Color(0xFF3A5376),
        tertiary = Color(0xFF984900),
        onTertiary = Color(0xFFFFF7F4),
        tertiaryContainer = Color(0xFFFF964B),
        onTertiaryContainer = Color(0xFF512400),
        background = Color(0xFFF9F9FF),
        onBackground = Color(0xFF2F3239),
        surface = Color(0xFFF9F9FF),
        onSurface = Color(0xFF2F3239),
        surfaceVariant = Color(0xFFE0E2EA),
        onSurfaceVariant = Color(0xFF5C5F66),
        surfaceTint = Color(0xFF0060B0),
        inverseSurface = Color(0xFF0B0E14),
        inverseOnSurface = Color(0xFF9A9DA4),
        error = Color(0xFFBB1B1B),
        onError = Color(0xFFFFF7F6),
        errorContainer = Color(0xFFFE4E44),
        onErrorContainer = Color(0xFF570003),
        outline = Color(0xFF787B82),
        outlineVariant = Color(0xFFAFB2B9),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF9F9FF),
        surfaceContainer = Color(0xFFECEDF6),
        surfaceContainerHigh = Color(0xFFE6E8F0),
        surfaceContainerHighest = Color(0xFFE0E2EA),
        surfaceContainerLow = Color(0xFFF2F3FC),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFD8DAE2),
    )
    // endregion

    // region Dark Default
    val DarkDefault = darkColorScheme(
        primary = Color(0xFF77B0FF),
        onPrimary = Color(0xFF002F5B),
        primaryContainer = Color(0xFF5AA2FF),
        onPrimaryContainer = Color(0xFF002347),
        inversePrimary = Color(0xFF005FB0),
        secondary = Color(0xFFAFC8F1),
        onSecondary = Color(0xFF284163),
        secondaryContainer = Color(0xFF233C5E),
        onSecondaryContainer = Color(0xFFA8C1EA),
        tertiary = Color(0xFFFFA364),
        onTertiary = Color(0xFF5A2900),
        tertiaryContainer = Color(0xFFFD8E3B),
        onTertiaryContainer = Color(0xFF4A2100),
        background = Color(0xFF0B0E14),
        onBackground = Color(0xFFE3E5ED),
        surface = Color(0xFF0B0E14),
        onSurface = Color(0xFFE3E5ED),
        surfaceVariant = Color(0xFF23262C),
        onSurfaceVariant = Color(0xFF91939B),
        surfaceTint = Color(0xFF77B0FF),
        inverseSurface = Color(0xFFF9F9FF),
        inverseOnSurface = Color(0xFF52555C),
        error = Color(0xFFFF7164),
        onError = Color(0xFF4A0002),
        errorContainer = Color(0xFFAC0C12),
        onErrorContainer = Color(0xFFFFB8B0),
        outline = Color(0xFF73757D),
        outlineVariant = Color(0xFF45484F),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF292C32),
        surfaceContainer = Color(0xFF161A1F),
        surfaceContainerHigh = Color(0xFF1C2026),
        surfaceContainerHighest = Color(0xFF23262C),
        surfaceContainerLow = Color(0xFF101319),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceDim = Color(0xFF0B0E14),
    )
    // endregion

    // region Light Medium Contrast
    val LightMediumContrast = lightColorScheme(
        primary = Color(0xFF00437F),
        onPrimary = Color(0xFFC6DBFF),
        primaryContainer = Color(0xFF1775D1),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFF539FFD),
        secondary = Color(0xFF2B4466),
        onSecondary = Color(0xFFC6DBFF),
        secondaryContainer = Color(0xFF5D769B),
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color(0xFF6D3300),
        onTertiary = Color(0xFFFFD0B4),
        tertiaryContainer = Color(0xFFB85A00),
        onTertiaryContainer = Color(0xFFFFFFFF),
        background = Color(0xFFF9F9FF),
        onBackground = Color(0xFF21242A),
        surface = Color(0xFFF9F9FF),
        onSurface = Color(0xFF21242A),
        surfaceVariant = Color(0xFFE0E2EA),
        onSurfaceVariant = Color(0xFF404349),
        surfaceTint = Color(0xFF00437F),
        inverseSurface = Color(0xFF0B0E14),
        inverseOnSurface = Color(0xFFC2C4CC),
        error = Color(0xFF8C0009),
        onError = Color(0xFFFFCEC8),
        errorContainer = Color(0xFFDA342E),
        onErrorContainer = Color(0xFFFFFFFF),
        outline = Color(0xFF5C5F66),
        outlineVariant = Color(0xFF787B82),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF9F9FF),
        surfaceContainer = Color(0xFFECEDF6),
        surfaceContainerHigh = Color(0xFFE6E8F0),
        surfaceContainerHighest = Color(0xFFE0E2EA),
        surfaceContainerLow = Color(0xFFF2F3FC),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFD8DAE2),
    )
    // endregion

    // region Dark Medium Contrast
    val DarkMediumContrast = darkColorScheme(
        primary = Color(0xFF8DBBFF),
        onPrimary = Color(0xFF002C57),
        primaryContainer = Color(0xFF5AA2FF),
        onPrimaryContainer = Color(0xFF00152E),
        inversePrimary = Color(0xFF00559F),
        secondary = Color(0xFFAFC8F1),
        onSecondary = Color(0xFF1D3759),
        secondaryContainer = Color(0xFF5D769B),
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color(0xFFFFA364),
        onTertiary = Color(0xFF4A2000),
        tertiaryContainer = Color(0xFFFD8E3B),
        onTertiaryContainer = Color(0xFF391700),
        background = Color(0xFF0B0E14),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF0B0E14),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF23262C),
        onSurfaceVariant = Color(0xFFB6B8C0),
        surfaceTint = Color(0xFF8DBBFF),
        inverseSurface = Color(0xFFF9F9FF),
        inverseOnSurface = Color(0xFF35383F),
        error = Color(0xFFFF9F94),
        onError = Color(0xFF600004),
        errorContainer = Color(0xFFDA342E),
        onErrorContainer = Color(0xFFFFFFFF),
        outline = Color(0xFF91939B),
        outlineVariant = Color(0xFF73757D),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF292C32),
        surfaceContainer = Color(0xFF161A1F),
        surfaceContainerHigh = Color(0xFF1C2026),
        surfaceContainerHighest = Color(0xFF23262C),
        surfaceContainerLow = Color(0xFF101319),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceDim = Color(0xFF0B0E14),
    )
    // endregion

    // region Light High Contrast
    val LightHighContrast = lightColorScheme(
        primary = Color(0xFF002449),
        onPrimary = Color(0xFFC7DBFF),
        primaryContainer = Color(0xFF0060B0),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFFA2C7FF),
        secondary = Color(0xFF062445),
        onSecondary = Color(0xFFC7DBFF),
        secondaryContainer = Color(0xFF486084),
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color(0xFF3E1A00),
        onTertiary = Color(0xFFFFD1B5),
        tertiaryContainer = Color(0xFF984900),
        onTertiaryContainer = Color(0xFFFFFFFF),
        background = Color(0xFFF9F9FF),
        onBackground = Color(0xFF000000),
        surface = Color(0xFFF9F9FF),
        onSurface = Color(0xFF000000),
        surfaceVariant = Color(0xFFE0E2EA),
        onSurfaceVariant = Color(0xFF21242A),
        surfaceTint = Color(0xFF002449),
        inverseSurface = Color(0xFF0B0E14),
        inverseOnSurface = Color(0xFFFFFFFF),
        error = Color(0xFF510003),
        onError = Color(0xFFFFCFC9),
        errorContainer = Color(0xFFBB1B1B),
        onErrorContainer = Color(0xFFFFFFFF),
        outline = Color(0xFF404349),
        outlineVariant = Color(0xFF5C5F66),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF9F9FF),
        surfaceContainer = Color(0xFFECEDF6),
        surfaceContainerHigh = Color(0xFFE6E8F0),
        surfaceContainerHighest = Color(0xFFE0E2EA),
        surfaceContainerLow = Color(0xFFF2F3FC),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFD8DAE2),
    )
    // endregion

    // region Dark High Contrast
    val DarkHighContrast = darkColorScheme(
        primary = Color(0xFFD9E6FF),
        onPrimary = Color(0xFF002C57),
        primaryContainer = Color(0xFF5AA2FF),
        onPrimaryContainer = Color(0xFF000000),
        inversePrimary = Color(0xFF00386C),
        secondary = Color(0xFFD9E6FF),
        onSecondary = Color(0xFF112D4E),
        secondaryContainer = Color(0xFF879FC7),
        onSecondaryContainer = Color(0xFF000000),
        tertiary = Color(0xFFFFDFCD),
        onTertiary = Color(0xFF4A2000),
        tertiaryContainer = Color(0xFFFD8E3B),
        onTertiaryContainer = Color(0xFF000000),
        background = Color(0xFF0B0E14),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF0B0E14),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF23262C),
        onSurfaceVariant = Color(0xFFE3E5ED),
        surfaceTint = Color(0xFFD9E6FF),
        inverseSurface = Color(0xFFF9F9FF),
        inverseOnSurface = Color(0xFF000000),
        error = Color(0xFFFFDEDA),
        onError = Color(0xFF600004),
        errorContainer = Color(0xFFFF7164),
        onErrorContainer = Color(0xFF000000),
        outline = Color(0xFFB6B8C0),
        outlineVariant = Color(0xFF91939B),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF292C32),
        surfaceContainer = Color(0xFF161A1F),
        surfaceContainerHigh = Color(0xFF1C2026),
        surfaceContainerHighest = Color(0xFF23262C),
        surfaceContainerLow = Color(0xFF101319),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceDim = Color(0xFF0B0E14),
    )
    // endregion

    fun lightScheme(style: ThemeStyle): ColorScheme = when (style) {
        ThemeStyle.MEDIUM_CONTRAST -> LightMediumContrast
        ThemeStyle.HIGH_CONTRAST -> LightHighContrast
        else -> LightDefault
    }

    fun darkScheme(style: ThemeStyle): ColorScheme = when (style) {
        ThemeStyle.MEDIUM_CONTRAST -> DarkMediumContrast
        ThemeStyle.HIGH_CONTRAST -> DarkHighContrast
        else -> DarkDefault
    }
}
