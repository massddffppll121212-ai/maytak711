package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.model.ThemePreference

private val MeethaqLightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = PureWhite,
    primaryContainer = SlateSurfaceLight,
    onPrimaryContainer = NavyPrimary,
    secondary = GoldMetallic,
    onSecondary = NavyDeep,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = GoldDeep,
    tertiary = PendingBlue,
    onTertiary = PureWhite,
    background = IvoryBackground,
    onBackground = InkNavy,
    surface = PureWhite,
    onSurface = InkNavy,
    surfaceVariant = SlateSurfaceLight,
    onSurfaceVariant = InkMutedLight,
    outline = SlateBorderLight,
    outlineVariant = SlateBorderLight,
    error = CrimsonRejected,
    onError = PureWhite,
    errorContainer = CrimsonRejectedContainerLight,
    onErrorContainer = CrimsonRejected
)

private val MeethaqDarkColorScheme = darkColorScheme(
    primary = GoldBright,
    onPrimary = NavyDeep,
    primaryContainer = NavySurfaceVariantDark,
    onPrimaryContainer = GoldSoft,
    secondary = GoldMetallic,
    onSecondary = NavyDeep,
    secondaryContainer = NavySurfaceVariantDark,
    onSecondaryContainer = GoldBright,
    tertiary = GoldSoft,
    onTertiary = NavyDeep,
    background = NavyDeep,
    onBackground = PureWhite,
    surface = NavySurfaceDark,
    onSurface = PureWhite,
    surfaceVariant = NavySurfaceVariantDark,
    onSurfaceVariant = InkMutedDark,
    outline = NavyBorderDark,
    outlineVariant = NavyBorderDark,
    error = ColorTokens.ErrorDark,
    onError = NavyDeep,
    errorContainer = CrimsonRejectedContainerDark,
    onErrorContainer = ColorTokens.ErrorDark
)

private object ColorTokens {
    val ErrorDark = androidx.compose.ui.graphics.Color(0xFFFF8A96)
}

val MeethaqShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MeethaqTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = if (darkTheme) MeethaqDarkColorScheme else MeethaqLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MeethaqTypography,
            shapes = MeethaqShapes,
            content = content
        )
    }
}
