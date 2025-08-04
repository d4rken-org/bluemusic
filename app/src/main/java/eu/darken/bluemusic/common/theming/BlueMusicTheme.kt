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
import eu.darken.bluemusic.common.theming.BlueMusicColors.BackgroundDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.BackgroundDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.BackgroundDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.BackgroundLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.BackgroundLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.BackgroundLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.ErrorContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.ErrorContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.ErrorContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.ErrorContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.ErrorContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.ErrorContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.ErrorDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.ErrorDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.ErrorDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.ErrorLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.ErrorLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.ErrorLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.InverseOnSurfaceDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.InverseOnSurfaceDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.InverseOnSurfaceDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.InverseOnSurfaceLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.InverseOnSurfaceLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.InverseOnSurfaceLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.InversePrimaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.InversePrimaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.InversePrimaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.InversePrimaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.InversePrimaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.InversePrimaryLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.InverseSurfaceDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.InverseSurfaceDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.InverseSurfaceDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.InverseSurfaceLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.InverseSurfaceLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.InverseSurfaceLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnBackgroundDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnBackgroundDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnBackgroundDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnBackgroundLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnBackgroundLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnBackgroundLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnErrorContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnErrorContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnErrorContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnErrorContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnErrorContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnErrorContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnErrorDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnErrorDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnErrorDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnErrorLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnErrorLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnErrorLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnPrimaryContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnPrimaryContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnPrimaryContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnPrimaryContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnPrimaryContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnPrimaryContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnPrimaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnPrimaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnPrimaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnPrimaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnPrimaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnPrimaryLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSecondaryContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSecondaryContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSecondaryContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSecondaryContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSecondaryContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSecondaryContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSecondaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSecondaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSecondaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSecondaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSecondaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSecondaryLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSurfaceDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSurfaceDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSurfaceDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSurfaceLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSurfaceLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSurfaceLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSurfaceVariantDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSurfaceVariantDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSurfaceVariantDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSurfaceVariantLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSurfaceVariantLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnSurfaceVariantLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnTertiaryContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnTertiaryContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnTertiaryContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnTertiaryContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnTertiaryContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnTertiaryContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnTertiaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnTertiaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnTertiaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnTertiaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnTertiaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OnTertiaryLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OutlineDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.OutlineDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OutlineDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OutlineLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.OutlineLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OutlineLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OutlineVariantDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.OutlineVariantDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OutlineVariantDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OutlineVariantLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.OutlineVariantLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.OutlineVariantLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.PrimaryContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.PrimaryContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.PrimaryContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.PrimaryContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.PrimaryContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.PrimaryContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.PrimaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.PrimaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.PrimaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.PrimaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.PrimaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.PrimaryLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.ScrimDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.ScrimDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.ScrimDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.ScrimLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.ScrimLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.ScrimLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SecondaryContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.SecondaryContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SecondaryContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SecondaryContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.SecondaryContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SecondaryContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SecondaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.SecondaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SecondaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SecondaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.SecondaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SecondaryLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceBrightDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceBrightDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceBrightDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceBrightLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceBrightLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceBrightLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerHighDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerHighDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerHighDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerHighLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerHighLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerHighLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerHighestDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerHighestDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerHighestDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerHighestLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerHighestLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerHighestLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLowDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLowDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLowDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLowLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLowLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLowLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLowestDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLowestDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLowestDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLowestLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLowestLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceContainerLowestLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceDimDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceDimDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceDimDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceDimLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceDimLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceDimLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceTintDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceTintDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceTintDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceTintLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceTintLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceTintLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceVariantDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceVariantDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceVariantDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceVariantLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceVariantLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.SurfaceVariantLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.TertiaryContainerDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.TertiaryContainerDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.TertiaryContainerDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.TertiaryContainerLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.TertiaryContainerLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.TertiaryContainerLightMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.TertiaryDark
import eu.darken.bluemusic.common.theming.BlueMusicColors.TertiaryDarkHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.TertiaryDarkMediumContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.TertiaryLight
import eu.darken.bluemusic.common.theming.BlueMusicColors.TertiaryLightHighContrast
import eu.darken.bluemusic.common.theming.BlueMusicColors.TertiaryLightMediumContrast

private val lightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    inversePrimary = InversePrimaryLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceTint = SurfaceTintLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = ScrimLight,
    surfaceBright = SurfaceBrightLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceDim = SurfaceDimLight,
//    primaryFixed = PrimaryFixed,
//    primaryFixedDim = PrimaryFixedDim,
//    onPrimaryFixed = OnPrimaryFixed,
//    onPrimaryFixedVariant = OnPrimaryFixedVariant,
//    secondaryFixed = SecondaryFixed,
//    secondaryFixedDim = SecondaryFixedDim,
//    onSecondaryFixed = OnSecondaryFixed,
//    onSecondaryFixedVariant = OnSecondaryFixedVariant,
//    tertiaryFixed = TertiaryFixed,
//    tertiaryFixedDim = TertiaryFixedDim,
//    onTertiaryFixed = OnTertiaryFixed,
//    onTertiaryFixedVariant = OnTertiaryFixedVariant,
)

private val darkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    inversePrimary = InversePrimaryDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceTint = SurfaceTintDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = ScrimDark,
    surfaceBright = SurfaceBrightDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceDim = SurfaceDimDark,
//    primaryFixed = PrimaryFixed,
//    primaryFixedDim = PrimaryFixedDim,
//    onPrimaryFixed = OnPrimaryFixed,
//    onPrimaryFixedVariant = OnPrimaryFixedVariant,
//    secondaryFixed = SecondaryFixed,
//    secondaryFixedDim = SecondaryFixedDim,
//    onSecondaryFixed = OnSecondaryFixed,
//    onSecondaryFixedVariant = OnSecondaryFixedVariant,
//    tertiaryFixed = TertiaryFixed,
//    tertiaryFixedDim = TertiaryFixedDim,
//    onTertiaryFixed = OnTertiaryFixed,
//    onTertiaryFixedVariant = OnTertiaryFixedVariant,
)

private val mediumContrastLightColorScheme = lightColorScheme(
    primary = PrimaryLightMediumContrast,
    onPrimary = OnPrimaryLightMediumContrast,
    primaryContainer = PrimaryContainerLightMediumContrast,
    onPrimaryContainer = OnPrimaryContainerLightMediumContrast,
    inversePrimary = InversePrimaryLightMediumContrast,
    secondary = SecondaryLightMediumContrast,
    onSecondary = OnSecondaryLightMediumContrast,
    secondaryContainer = SecondaryContainerLightMediumContrast,
    onSecondaryContainer = OnSecondaryContainerLightMediumContrast,
    tertiary = TertiaryLightMediumContrast,
    onTertiary = OnTertiaryLightMediumContrast,
    tertiaryContainer = TertiaryContainerLightMediumContrast,
    onTertiaryContainer = OnTertiaryContainerLightMediumContrast,
    background = BackgroundLightMediumContrast,
    onBackground = OnBackgroundLightMediumContrast,
    surface = SurfaceLightMediumContrast,
    onSurface = OnSurfaceLightMediumContrast,
    surfaceVariant = SurfaceVariantLightMediumContrast,
    onSurfaceVariant = OnSurfaceVariantLightMediumContrast,
    surfaceTint = SurfaceTintLightMediumContrast,
    inverseSurface = InverseSurfaceLightMediumContrast,
    inverseOnSurface = InverseOnSurfaceLightMediumContrast,
    error = ErrorLightMediumContrast,
    onError = OnErrorLightMediumContrast,
    errorContainer = ErrorContainerLightMediumContrast,
    onErrorContainer = OnErrorContainerLightMediumContrast,
    outline = OutlineLightMediumContrast,
    outlineVariant = OutlineVariantLightMediumContrast,
    scrim = ScrimLightMediumContrast,
    surfaceBright = SurfaceBrightLightMediumContrast,
    surfaceContainer = SurfaceContainerLightMediumContrast,
    surfaceContainerHigh = SurfaceContainerHighLightMediumContrast,
    surfaceContainerHighest = SurfaceContainerHighestLightMediumContrast,
    surfaceContainerLow = SurfaceContainerLowLightMediumContrast,
    surfaceContainerLowest = SurfaceContainerLowestLightMediumContrast,
    surfaceDim = SurfaceDimLightMediumContrast,
//    primaryFixed = PrimaryFixedMediumContrast,
//    primaryFixedDim = PrimaryFixedDimMediumContrast,
//    onPrimaryFixed = OnPrimaryFixedMediumContrast,
//    onPrimaryFixedVariant = OnPrimaryFixedVariantMediumContrast,
//    secondaryFixed = SecondaryFixedMediumContrast,
//    secondaryFixedDim = SecondaryFixedDimMediumContrast,
//    onSecondaryFixed = OnSecondaryFixedMediumContrast,
//    onSecondaryFixedVariant = OnSecondaryFixedVariantMediumContrast,
//    tertiaryFixed = TertiaryFixedMediumContrast,
//    tertiaryFixedDim = TertiaryFixedDimMediumContrast,
//    onTertiaryFixed = OnTertiaryFixedMediumContrast,
//    onTertiaryFixedVariant = OnTertiaryFixedVariantMediumContrast,
)

private val mediumContrastDarkColorScheme = darkColorScheme(
    primary = PrimaryDarkMediumContrast,
    onPrimary = OnPrimaryDarkMediumContrast,
    primaryContainer = PrimaryContainerDarkMediumContrast,
    onPrimaryContainer = OnPrimaryContainerDarkMediumContrast,
    inversePrimary = InversePrimaryDarkMediumContrast,
    secondary = SecondaryDarkMediumContrast,
    onSecondary = OnSecondaryDarkMediumContrast,
    secondaryContainer = SecondaryContainerDarkMediumContrast,
    onSecondaryContainer = OnSecondaryContainerDarkMediumContrast,
    tertiary = TertiaryDarkMediumContrast,
    onTertiary = OnTertiaryDarkMediumContrast,
    tertiaryContainer = TertiaryContainerDarkMediumContrast,
    onTertiaryContainer = OnTertiaryContainerDarkMediumContrast,
    background = BackgroundDarkMediumContrast,
    onBackground = OnBackgroundDarkMediumContrast,
    surface = SurfaceDarkMediumContrast,
    onSurface = OnSurfaceDarkMediumContrast,
    surfaceVariant = SurfaceVariantDarkMediumContrast,
    onSurfaceVariant = OnSurfaceVariantDarkMediumContrast,
    surfaceTint = SurfaceTintDarkMediumContrast,
    inverseSurface = InverseSurfaceDarkMediumContrast,
    inverseOnSurface = InverseOnSurfaceDarkMediumContrast,
    error = ErrorDarkMediumContrast,
    onError = OnErrorDarkMediumContrast,
    errorContainer = ErrorContainerDarkMediumContrast,
    onErrorContainer = OnErrorContainerDarkMediumContrast,
    outline = OutlineDarkMediumContrast,
    outlineVariant = OutlineVariantDarkMediumContrast,
    scrim = ScrimDarkMediumContrast,
    surfaceBright = SurfaceBrightDarkMediumContrast,
    surfaceContainer = SurfaceContainerDarkMediumContrast,
    surfaceContainerHigh = SurfaceContainerHighDarkMediumContrast,
    surfaceContainerHighest = SurfaceContainerHighestDarkMediumContrast,
    surfaceContainerLow = SurfaceContainerLowDarkMediumContrast,
    surfaceContainerLowest = SurfaceContainerLowestDarkMediumContrast,
    surfaceDim = SurfaceDimDarkMediumContrast,
//    primaryFixed = PrimaryFixedMediumContrast,
//    primaryFixedDim = PrimaryFixedDimMediumContrast,
//    onPrimaryFixed = OnPrimaryFixedMediumContrast,
//    onPrimaryFixedVariant = OnPrimaryFixedVariantMediumContrast,
//    secondaryFixed = SecondaryFixedMediumContrast,
//    secondaryFixedDim = SecondaryFixedDimMediumContrast,
//    onSecondaryFixed = OnSecondaryFixedMediumContrast,
//    onSecondaryFixedVariant = OnSecondaryFixedVariantMediumContrast,
//    tertiaryFixed = TertiaryFixedMediumContrast,
//    tertiaryFixedDim = TertiaryFixedDimMediumContrast,
//    onTertiaryFixed = OnTertiaryFixedMediumContrast,
//    onTertiaryFixedVariant = OnTertiaryFixedVariantMediumContrast,
)


private val highContrastLightColorScheme = lightColorScheme(
    primary = PrimaryLightHighContrast,
    onPrimary = OnPrimaryLightHighContrast,
    primaryContainer = PrimaryContainerLightHighContrast,
    onPrimaryContainer = OnPrimaryContainerLightHighContrast,
    inversePrimary = InversePrimaryLightHighContrast,
    secondary = SecondaryLightHighContrast,
    onSecondary = OnSecondaryLightHighContrast,
    secondaryContainer = SecondaryContainerLightHighContrast,
    onSecondaryContainer = OnSecondaryContainerLightHighContrast,
    tertiary = TertiaryLightHighContrast,
    onTertiary = OnTertiaryLightHighContrast,
    tertiaryContainer = TertiaryContainerLightHighContrast,
    onTertiaryContainer = OnTertiaryContainerLightHighContrast,
    background = BackgroundLightHighContrast,
    onBackground = OnBackgroundLightHighContrast,
    surface = SurfaceLightHighContrast,
    onSurface = OnSurfaceLightHighContrast,
    surfaceVariant = SurfaceVariantLightHighContrast,
    onSurfaceVariant = OnSurfaceVariantLightHighContrast,
    surfaceTint = SurfaceTintLightHighContrast,
    inverseSurface = InverseSurfaceLightHighContrast,
    inverseOnSurface = InverseOnSurfaceLightHighContrast,
    error = ErrorLightHighContrast,
    onError = OnErrorLightHighContrast,
    errorContainer = ErrorContainerLightHighContrast,
    onErrorContainer = OnErrorContainerLightHighContrast,
    outline = OutlineLightHighContrast,
    outlineVariant = OutlineVariantLightHighContrast,
    scrim = ScrimLightHighContrast,
    surfaceBright = SurfaceBrightLightHighContrast,
    surfaceContainer = SurfaceContainerLightHighContrast,
    surfaceContainerHigh = SurfaceContainerHighLightHighContrast,
    surfaceContainerHighest = SurfaceContainerHighestLightHighContrast,
    surfaceContainerLow = SurfaceContainerLowLightHighContrast,
    surfaceContainerLowest = SurfaceContainerLowestLightHighContrast,
    surfaceDim = SurfaceDimLightHighContrast,
//    primaryFixed = PrimaryFixedHighContrast,
//    primaryFixedDim = PrimaryFixedDimHighContrast,
//    onPrimaryFixed = OnPrimaryFixedHighContrast,
//    onPrimaryFixedVariant = OnPrimaryFixedVariantHighContrast,
//    secondaryFixed = SecondaryFixedHighContrast,
//    secondaryFixedDim = SecondaryFixedDimHighContrast,
//    onSecondaryFixed = OnSecondaryFixedHighContrast,
//    onSecondaryFixedVariant = OnSecondaryFixedVariantHighContrast,
//    tertiaryFixed = TertiaryFixedHighContrast,
//    tertiaryFixedDim = TertiaryFixedDimHighContrast,
//    onTertiaryFixed = OnTertiaryFixedHighContrast,
//    onTertiaryFixedVariant = OnTertiaryFixedVariantHighContrast,
)

private val highContrastDarkColorScheme = darkColorScheme(
    primary = PrimaryDarkHighContrast,
    onPrimary = OnPrimaryDarkHighContrast,
    primaryContainer = PrimaryContainerDarkHighContrast,
    onPrimaryContainer = OnPrimaryContainerDarkHighContrast,
    inversePrimary = InversePrimaryDarkHighContrast,
    secondary = SecondaryDarkHighContrast,
    onSecondary = OnSecondaryDarkHighContrast,
    secondaryContainer = SecondaryContainerDarkHighContrast,
    onSecondaryContainer = OnSecondaryContainerDarkHighContrast,
    tertiary = TertiaryDarkHighContrast,
    onTertiary = OnTertiaryDarkHighContrast,
    tertiaryContainer = TertiaryContainerDarkHighContrast,
    onTertiaryContainer = OnTertiaryContainerDarkHighContrast,
    background = BackgroundDarkHighContrast,
    onBackground = OnBackgroundDarkHighContrast,
    surface = SurfaceDarkHighContrast,
    onSurface = OnSurfaceDarkHighContrast,
    surfaceVariant = SurfaceVariantDarkHighContrast,
    onSurfaceVariant = OnSurfaceVariantDarkHighContrast,
    surfaceTint = SurfaceTintDarkHighContrast,
    inverseSurface = InverseSurfaceDarkHighContrast,
    inverseOnSurface = InverseOnSurfaceDarkHighContrast,
    error = ErrorDarkHighContrast,
    onError = OnErrorDarkHighContrast,
    errorContainer = ErrorContainerDarkHighContrast,
    onErrorContainer = OnErrorContainerDarkHighContrast,
    outline = OutlineDarkHighContrast,
    outlineVariant = OutlineVariantDarkHighContrast,
    scrim = ScrimDarkHighContrast,
    surfaceBright = SurfaceBrightDarkHighContrast,
    surfaceContainer = SurfaceContainerDarkHighContrast,
    surfaceContainerHigh = SurfaceContainerHighDarkHighContrast,
    surfaceContainerHighest = SurfaceContainerHighestDarkHighContrast,
    surfaceContainerLow = SurfaceContainerLowDarkHighContrast,
    surfaceContainerLowest = SurfaceContainerLowestDarkHighContrast,
    surfaceDim = SurfaceDimDarkHighContrast,
//    primaryFixed = PrimaryFixedHighContrast,
//    primaryFixedDim = PrimaryFixedDimHighContrast,
//    onPrimaryFixed = OnPrimaryFixedHighContrast,
//    onPrimaryFixedVariant = OnPrimaryFixedVariantHighContrast,
//    secondaryFixed = SecondaryFixedHighContrast,
//    secondaryFixedDim = SecondaryFixedDimHighContrast,
//    onSecondaryFixed = OnSecondaryFixedHighContrast,
//    onSecondaryFixedVariant = OnSecondaryFixedVariantHighContrast,
//    tertiaryFixed = TertiaryFixedHighContrast,
//    tertiaryFixedDim = TertiaryFixedDimHighContrast,
//    onTertiaryFixed = OnTertiaryFixedHighContrast,
//    onTertiaryFixedVariant = OnTertiaryFixedVariantHighContrast,
)

@Composable
fun BlueMusicTheme(state: ThemeState = ThemeState(), content: @Composable () -> Unit) {
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
        darkTheme -> darkColorScheme
        else -> lightColorScheme
    }
    MaterialTheme(colorScheme = colors, content = content, typography = BlueMusicTypography)
}

@Preview(showBackground = true, name = "Light Mode")
@Composable
fun LightThemePreview() =
    BlueMusicTheme(ThemeState(ThemeMode.LIGHT, ThemeStyle.DEFAULT)) { SampleContent() }

@Preview(showBackground = true, name = "Dark Mode")
@Composable
fun DarkThemePreview() =
    BlueMusicTheme(ThemeState(ThemeMode.DARK, ThemeStyle.DEFAULT)) { SampleContent() }

@Preview(showBackground = true, name = "Material You Light Mode")
@Composable
fun MaterialYouLightThemePreview() =
    BlueMusicTheme(ThemeState(ThemeMode.LIGHT, ThemeStyle.MATERIAL_YOU)) { SampleContent() }

@Preview(showBackground = true, name = "Material You Dark Mode")
@Composable
fun MaterialYouDarkThemePreview() =
    BlueMusicTheme(ThemeState(ThemeMode.DARK, ThemeStyle.MATERIAL_YOU)) { SampleContent() }

@Preview(showBackground = true, name = "Medium Contrast Light Mode")
@Composable
fun MediumContrastLightThemePreview() =
    BlueMusicTheme(ThemeState(ThemeMode.LIGHT, ThemeStyle.MEDIUM_CONTRAST)) { SampleContent() }

@Preview(showBackground = true, name = "Medium Contrast Dark Mode")
@Composable
fun MediumContrastDarkThemePreview() =
    BlueMusicTheme(ThemeState(ThemeMode.DARK, ThemeStyle.MEDIUM_CONTRAST)) { SampleContent() }

@Preview(showBackground = true, name = "High Contrast Light Mode")
@Composable
fun HighContrastLightThemePreview() =
    BlueMusicTheme(ThemeState(ThemeMode.LIGHT, ThemeStyle.HIGH_CONTRAST)) { SampleContent() }

@Preview(showBackground = true, name = "High Contrast Dark Mode")
@Composable
fun HighContrastDarkThemePreview() =
    BlueMusicTheme(ThemeState(ThemeMode.DARK, ThemeStyle.HIGH_CONTRAST)) { SampleContent() }
