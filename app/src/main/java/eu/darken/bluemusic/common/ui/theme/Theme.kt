package eu.darken.bluemusic.common.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ColorPrimary,
    onPrimary = Color.White,
    primaryContainer = ColorPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = ColorAccent,
    onSecondary = Color.Black,
    secondaryContainer = ColorAccentLight,
    onSecondaryContainer = Color.Black,
    background = ColorBackgroundDark,
    onBackground = Color.White,
    surface = ColorSurfaceDark,
    onSurface = Color.White,
    error = ColorError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ColorPrimary,
    onPrimary = Color.White,
    primaryContainer = ColorPrimaryLight,
    onPrimaryContainer = Color.Black,
    secondary = ColorAccent,
    onSecondary = Color.White,
    secondaryContainer = ColorAccentLight,
    onSecondaryContainer = Color.Black,
    background = ColorBackground,
    onBackground = Color.Black,
    surface = ColorSurface,
    onSurface = Color.Black,
    error = ColorError,
    onError = Color.White
)

@Composable
fun BlueMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}