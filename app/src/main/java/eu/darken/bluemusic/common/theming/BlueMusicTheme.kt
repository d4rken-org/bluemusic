package eu.darken.bluemusic.common.theming

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import eu.darken.bluemusic.common.compose.SampleContent
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.common.theming.BlueMusicColors.backgroundDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.backgroundDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.backgroundDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.backgroundLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.backgroundLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.backgroundLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.errorContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.errorContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.errorContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.errorContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.errorContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.errorContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.errorDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.errorDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.errorDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.errorLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.errorLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.errorLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.inverseOnSurfaceDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.inverseOnSurfaceDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.inverseOnSurfaceDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.inverseOnSurfaceLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.inverseOnSurfaceLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.inverseOnSurfaceLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.inversePrimaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.inversePrimaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.inversePrimaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.inversePrimaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.inversePrimaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.inversePrimaryLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.inverseSurfaceDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.inverseSurfaceDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.inverseSurfaceDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.inverseSurfaceLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.inverseSurfaceLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.inverseSurfaceLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onBackgroundDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.onBackgroundDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onBackgroundDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onBackgroundLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.onBackgroundLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onBackgroundLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onErrorContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.onErrorContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onErrorContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onErrorContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.onErrorContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onErrorContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onErrorDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.onErrorDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onErrorDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onErrorLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.onErrorLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onErrorLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onPrimaryContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.onPrimaryContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onPrimaryContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onPrimaryContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.onPrimaryContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onPrimaryContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onPrimaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.onPrimaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onPrimaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onPrimaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.onPrimaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onPrimaryLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSecondaryContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSecondaryContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSecondaryContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSecondaryContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSecondaryContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSecondaryContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSecondaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSecondaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSecondaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSecondaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSecondaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSecondaryLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSurfaceDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSurfaceDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSurfaceDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSurfaceLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSurfaceLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSurfaceLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSurfaceVariantDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSurfaceVariantDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSurfaceVariantDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSurfaceVariantLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSurfaceVariantLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onSurfaceVariantLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onTertiaryContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.onTertiaryContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onTertiaryContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onTertiaryContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.onTertiaryContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onTertiaryContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onTertiaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.onTertiaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onTertiaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onTertiaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.onTertiaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.onTertiaryLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.outlineDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.outlineDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.outlineDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.outlineLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.outlineLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.outlineLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.outlineVariantDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.outlineVariantDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.outlineVariantDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.outlineVariantLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.outlineVariantLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.outlineVariantLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.primaryContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.primaryContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.primaryContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.primaryContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.primaryContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.primaryContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.primaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.primaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.primaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.primaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.primaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.primaryLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.scrimDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.scrimDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.scrimDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.scrimLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.scrimLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.scrimLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.secondaryContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.secondaryContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.secondaryContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.secondaryContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.secondaryContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.secondaryContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.secondaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.secondaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.secondaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.secondaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.secondaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.secondaryLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceBrightDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceBrightDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceBrightDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceBrightLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceBrightLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceBrightLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerHighDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerHighDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerHighDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerHighLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerHighLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerHighLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerHighestDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerHighestDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerHighestDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerHighestLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerHighestLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerHighestLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLowDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLowDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLowDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLowLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLowLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLowLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLowestDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLowestDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLowestDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLowestLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLowestLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceContainerLowestLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceDimDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceDimDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceDimDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceDimLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceDimLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceDimLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceVariantDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceVariantDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceVariantDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceVariantLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceVariantLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.surfaceVariantLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.tertiaryContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.tertiaryContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.tertiaryContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.tertiaryContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.tertiaryContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.tertiaryContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.tertiaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.tertiaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.tertiaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.tertiaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.tertiaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.tertiaryLightMediumContrast
import eu.darken.bluemusic.common.theming.ButlerTypography
import eu.darken.bluemusic.common.theming.ThemeState

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

private val mediumContrastLightColorScheme = lightColorScheme(
    primary = primaryLightMediumContrast,
    onPrimary = onPrimaryLightMediumContrast,
    primaryContainer = primaryContainerLightMediumContrast,
    onPrimaryContainer = onPrimaryContainerLightMediumContrast,
    secondary = secondaryLightMediumContrast,
    onSecondary = onSecondaryLightMediumContrast,
    secondaryContainer = secondaryContainerLightMediumContrast,
    onSecondaryContainer = onSecondaryContainerLightMediumContrast,
    tertiary = tertiaryLightMediumContrast,
    onTertiary = onTertiaryLightMediumContrast,
    tertiaryContainer = tertiaryContainerLightMediumContrast,
    onTertiaryContainer = onTertiaryContainerLightMediumContrast,
    error = errorLightMediumContrast,
    onError = onErrorLightMediumContrast,
    errorContainer = errorContainerLightMediumContrast,
    onErrorContainer = onErrorContainerLightMediumContrast,
    background = backgroundLightMediumContrast,
    onBackground = onBackgroundLightMediumContrast,
    surface = surfaceLightMediumContrast,
    onSurface = onSurfaceLightMediumContrast,
    surfaceVariant = surfaceVariantLightMediumContrast,
    onSurfaceVariant = onSurfaceVariantLightMediumContrast,
    outline = outlineLightMediumContrast,
    outlineVariant = outlineVariantLightMediumContrast,
    scrim = scrimLightMediumContrast,
    inverseSurface = inverseSurfaceLightMediumContrast,
    inverseOnSurface = inverseOnSurfaceLightMediumContrast,
    inversePrimary = inversePrimaryLightMediumContrast,
    surfaceDim = surfaceDimLightMediumContrast,
    surfaceBright = surfaceBrightLightMediumContrast,
    surfaceContainerLowest = surfaceContainerLowestLightMediumContrast,
    surfaceContainerLow = surfaceContainerLowLightMediumContrast,
    surfaceContainer = surfaceContainerLightMediumContrast,
    surfaceContainerHigh = surfaceContainerHighLightMediumContrast,
    surfaceContainerHighest = surfaceContainerHighestLightMediumContrast,
)

private val highContrastLightColorScheme = lightColorScheme(
    primary = primaryLightHighContrast,
    onPrimary = onPrimaryLightHighContrast,
    primaryContainer = primaryContainerLightHighContrast,
    onPrimaryContainer = onPrimaryContainerLightHighContrast,
    secondary = secondaryLightHighContrast,
    onSecondary = onSecondaryLightHighContrast,
    secondaryContainer = secondaryContainerLightHighContrast,
    onSecondaryContainer = onSecondaryContainerLightHighContrast,
    tertiary = tertiaryLightHighContrast,
    onTertiary = onTertiaryLightHighContrast,
    tertiaryContainer = tertiaryContainerLightHighContrast,
    onTertiaryContainer = onTertiaryContainerLightHighContrast,
    error = errorLightHighContrast,
    onError = onErrorLightHighContrast,
    errorContainer = errorContainerLightHighContrast,
    onErrorContainer = onErrorContainerLightHighContrast,
    background = backgroundLightHighContrast,
    onBackground = onBackgroundLightHighContrast,
    surface = surfaceLightHighContrast,
    onSurface = onSurfaceLightHighContrast,
    surfaceVariant = surfaceVariantLightHighContrast,
    onSurfaceVariant = onSurfaceVariantLightHighContrast,
    outline = outlineLightHighContrast,
    outlineVariant = outlineVariantLightHighContrast,
    scrim = scrimLightHighContrast,
    inverseSurface = inverseSurfaceLightHighContrast,
    inverseOnSurface = inverseOnSurfaceLightHighContrast,
    inversePrimary = inversePrimaryLightHighContrast,
    surfaceDim = surfaceDimLightHighContrast,
    surfaceBright = surfaceBrightLightHighContrast,
    surfaceContainerLowest = surfaceContainerLowestLightHighContrast,
    surfaceContainerLow = surfaceContainerLowLightHighContrast,
    surfaceContainer = surfaceContainerLightHighContrast,
    surfaceContainerHigh = surfaceContainerHighLightHighContrast,
    surfaceContainerHighest = surfaceContainerHighestLightHighContrast,
)

private val mediumContrastDarkColorScheme = darkColorScheme(
    primary = primaryDarkMediumContrast,
    onPrimary = onPrimaryDarkMediumContrast,
    primaryContainer = primaryContainerDarkMediumContrast,
    onPrimaryContainer = onPrimaryContainerDarkMediumContrast,
    secondary = secondaryDarkMediumContrast,
    onSecondary = onSecondaryDarkMediumContrast,
    secondaryContainer = secondaryContainerDarkMediumContrast,
    onSecondaryContainer = onSecondaryContainerDarkMediumContrast,
    tertiary = tertiaryDarkMediumContrast,
    onTertiary = onTertiaryDarkMediumContrast,
    tertiaryContainer = tertiaryContainerDarkMediumContrast,
    onTertiaryContainer = onTertiaryContainerDarkMediumContrast,
    error = errorDarkMediumContrast,
    onError = onErrorDarkMediumContrast,
    errorContainer = errorContainerDarkMediumContrast,
    onErrorContainer = onErrorContainerDarkMediumContrast,
    background = backgroundDarkMediumContrast,
    onBackground = onBackgroundDarkMediumContrast,
    surface = surfaceDarkMediumContrast,
    onSurface = onSurfaceDarkMediumContrast,
    surfaceVariant = surfaceVariantDarkMediumContrast,
    onSurfaceVariant = onSurfaceVariantDarkMediumContrast,
    outline = outlineDarkMediumContrast,
    outlineVariant = outlineVariantDarkMediumContrast,
    scrim = scrimDarkMediumContrast,
    inverseSurface = inverseSurfaceDarkMediumContrast,
    inverseOnSurface = inverseOnSurfaceDarkMediumContrast,
    inversePrimary = inversePrimaryDarkMediumContrast,
    surfaceDim = surfaceDimDarkMediumContrast,
    surfaceBright = surfaceBrightDarkMediumContrast,
    surfaceContainerLowest = surfaceContainerLowestDarkMediumContrast,
    surfaceContainerLow = surfaceContainerLowDarkMediumContrast,
    surfaceContainer = surfaceContainerDarkMediumContrast,
    surfaceContainerHigh = surfaceContainerHighDarkMediumContrast,
    surfaceContainerHighest = surfaceContainerHighestDarkMediumContrast,
)

private val highContrastDarkColorScheme = darkColorScheme(
    primary = primaryDarkHighContrast,
    onPrimary = onPrimaryDarkHighContrast,
    primaryContainer = primaryContainerDarkHighContrast,
    onPrimaryContainer = onPrimaryContainerDarkHighContrast,
    secondary = secondaryDarkHighContrast,
    onSecondary = onSecondaryDarkHighContrast,
    secondaryContainer = secondaryContainerDarkHighContrast,
    onSecondaryContainer = onSecondaryContainerDarkHighContrast,
    tertiary = tertiaryDarkHighContrast,
    onTertiary = onTertiaryDarkHighContrast,
    tertiaryContainer = tertiaryContainerDarkHighContrast,
    onTertiaryContainer = onTertiaryContainerDarkHighContrast,
    error = errorDarkHighContrast,
    onError = onErrorDarkHighContrast,
    errorContainer = errorContainerDarkHighContrast,
    onErrorContainer = onErrorContainerDarkHighContrast,
    background = backgroundDarkHighContrast,
    onBackground = onBackgroundDarkHighContrast,
    surface = surfaceDarkHighContrast,
    onSurface = onSurfaceDarkHighContrast,
    surfaceVariant = surfaceVariantDarkHighContrast,
    onSurfaceVariant = onSurfaceVariantDarkHighContrast,
    outline = outlineDarkHighContrast,
    outlineVariant = outlineVariantDarkHighContrast,
    scrim = scrimDarkHighContrast,
    inverseSurface = inverseSurfaceDarkHighContrast,
    inverseOnSurface = inverseOnSurfaceDarkHighContrast,
    inversePrimary = inversePrimaryDarkHighContrast,
    surfaceDim = surfaceDimDarkHighContrast,
    surfaceBright = surfaceBrightDarkHighContrast,
    surfaceContainerLowest = surfaceContainerLowestDarkHighContrast,
    surfaceContainerLow = surfaceContainerLowDarkHighContrast,
    surfaceContainer = surfaceContainerDarkHighContrast,
    surfaceContainerHigh = surfaceContainerHighDarkHighContrast,
    surfaceContainerHighest = surfaceContainerHighestDarkHighContrast,
)

@Composable
fun MyAppTheme(state: ThemeState = ThemeState(), content: @Composable () -> Unit) {
    val dynamicColors =
        when (state.style) {
            ThemeStyle.DEFAULT -> false
            ThemeStyle.MATERIAL_YOU -> hasApiLevel(31)
            ThemeStyle.MEDIUM_CONTRAST -> false
            ThemeStyle.HIGH_CONTRAST -> false
        }
    val darkTheme =
        when (state.mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !darkTheme
        insetsController.isAppearanceLightNavigationBars = !darkTheme
    }
    @SuppressLint("NewApi")
    val colors = when {
        dynamicColors && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
        dynamicColors && !darkTheme -> dynamicLightColorScheme(LocalContext.current)
        state.style == ThemeStyle.MEDIUM_CONTRAST && darkTheme -> mediumContrastDarkColorScheme
        state.style == ThemeStyle.MEDIUM_CONTRAST && !darkTheme -> mediumContrastLightColorScheme
        state.style == ThemeStyle.HIGH_CONTRAST && darkTheme -> highContrastDarkColorScheme
        state.style == ThemeStyle.HIGH_CONTRAST && !darkTheme -> highContrastLightColorScheme
        darkTheme -> darkScheme
        else -> lightScheme
    }
    MaterialTheme(colorScheme = colors, content = content, typography = ButlerTypography)
}

@Preview(showBackground = true, name = "Light Mode")
@Composable
fun LightThemePreview() =
    MyAppTheme(ThemeState(ThemeMode.LIGHT, ThemeStyle.DEFAULT)) { SampleContent() }

@Preview(showBackground = true, name = "Dark Mode")
@Composable
fun DarkThemePreview() =
    MyAppTheme(ThemeState(ThemeMode.DARK, ThemeStyle.DEFAULT)) { SampleContent() }

@Preview(showBackground = true, name = "Material You Light Mode")
@Composable
fun MaterialYouLightThemePreview() =
    MyAppTheme(ThemeState(ThemeMode.LIGHT, ThemeStyle.MATERIAL_YOU)) { SampleContent() }

@Preview(showBackground = true, name = "Material You Dark Mode")
@Composable
fun MaterialYouDarkThemePreview() =
    MyAppTheme(ThemeState(ThemeMode.DARK, ThemeStyle.MATERIAL_YOU)) { SampleContent() }

@Preview(showBackground = true, name = "Medium Contrast Light Mode")
@Composable
fun MediumContrastLightThemePreview() =
    MyAppTheme(ThemeState(ThemeMode.LIGHT, ThemeStyle.MEDIUM_CONTRAST)) { SampleContent() }

@Preview(showBackground = true, name = "Medium Contrast Dark Mode")
@Composable
fun MediumContrastDarkThemePreview() =
    MyAppTheme(ThemeState(ThemeMode.DARK, ThemeStyle.MEDIUM_CONTRAST)) { SampleContent() }

@Preview(showBackground = true, name = "High Contrast Light Mode")
@Composable
fun HighContrastLightThemePreview() =
    MyAppTheme(ThemeState(ThemeMode.LIGHT, ThemeStyle.HIGH_CONTRAST)) { SampleContent() }

@Preview(showBackground = true, name = "High Contrast Dark Mode")
@Composable
fun HighContrastDarkThemePreview() =
    MyAppTheme(ThemeState(ThemeMode.DARK, ThemeStyle.HIGH_CONTRAST)) { SampleContent() }
