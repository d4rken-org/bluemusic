package eu.darken.bluemusic.common.theming

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import eu.darken.bluemusic.common.compose.PreviewWrapper
import eu.darken.bluemusic.common.compose.Preview2
import eu.darken.bluemusic.common.compose.SampleContent
import eu.darken.bluemusic.common.hasApiLevel

val LocalIsDynamicColorActive = compositionLocalOf { false }

@Composable
fun BlueMusicTheme(state: ThemeState = ThemeState(), content: @Composable () -> Unit) {
    val darkTheme = when (state.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val isDynamic = state.style == ThemeStyle.MATERIAL_YOU && hasApiLevel(31)
    val context = LocalContext.current

    @SuppressLint("NewApi")
    val colorScheme = remember(state, darkTheme, isDynamic, context) {
        when {
            isDynamic && darkTheme -> dynamicDarkColorScheme(context)
            isDynamic && !darkTheme -> dynamicLightColorScheme(context)
            darkTheme -> ThemeColorProvider.getDarkColorScheme(state.color, state.style)
            else -> ThemeColorProvider.getLightColorScheme(state.color, state.style)
        }
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !darkTheme
        insetsController.isAppearanceLightNavigationBars = !darkTheme
    }

    CompositionLocalProvider(LocalIsDynamicColorActive provides isDynamic) {
        MaterialTheme(colorScheme = colorScheme, content = content, typography = BlueMusicTypography)
    }
}

@Preview2
@Composable
fun DefaultThemePreview() = PreviewWrapper { SampleContent() }

@Preview2
@Composable
fun MaterialYouThemePreview() =
    PreviewWrapper(theme = ThemeState(style = ThemeStyle.MATERIAL_YOU)) { SampleContent() }

@Preview2
@Composable
fun MediumContrastThemePreview() =
    PreviewWrapper(theme = ThemeState(style = ThemeStyle.MEDIUM_CONTRAST)) { SampleContent() }

@Preview2
@Composable
fun HighContrastThemePreview() =
    PreviewWrapper(theme = ThemeState(style = ThemeStyle.HIGH_CONTRAST)) { SampleContent() }
