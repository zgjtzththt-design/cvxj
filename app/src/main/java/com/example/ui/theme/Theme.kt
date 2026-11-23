package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode {
    DARK, LIGHT, SYSTEM
}

@Immutable
data class DropLinkExtendedColors(
    val cardBorder: Color,
    val cardElevated: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val cyan: Color,
    val cyanContainer: Color,
    val isDark: Boolean
)

val LocalDropLinkColors = staticCompositionLocalOf {
    DropLinkExtendedColors(
        cardBorder = ObsidianBorder,
        cardElevated = ObsidianCardElevated,
        success = EmeraldSuccess,
        successContainer = EmeraldContainer,
        warning = AmberWarning,
        warningContainer = AmberContainer,
        cyan = SoftCyan,
        cyanContainer = SoftCyanContainer,
        isDark = true
    )
}

private val DropLinkDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = ElectricBlueContainer,
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = SoftCyan,
    onSecondary = Color(0xFF04222A),
    secondaryContainer = SoftCyanContainer,
    onSecondaryContainer = Color(0xFFBCF3FF),
    tertiary = IndigoAccent,
    onTertiary = Color.White,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianCard,
    surfaceContainerHigh = ObsidianCardElevated,
    surfaceContainerHighest = Color(0xFF202D45),
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1A253A),
    error = RoseError,
    onError = Color.White,
    errorContainer = RoseContainer,
    onErrorContainer = Color(0xFFFFD8DE)
)

private val DropLinkLightColorScheme = lightColorScheme(
    primary = ElectricBlueLight,
    onPrimary = Color.White,
    primaryContainer = ElectricBlueLightContainer,
    onPrimaryContainer = Color(0xFF082254),
    secondary = SoftCyanLight,
    onSecondary = Color.White,
    secondaryContainer = SoftCyanLightContainer,
    onSecondaryContainer = Color(0xFF042F3B),
    tertiary = IndigoAccent,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = Color(0xFFF9FBFF),
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = Color(0xFFDEE7F5),
    outline = LightBorder,
    outlineVariant = Color(0xFFE3ECF7),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E8),
    onErrorContainer = Color(0xFF5A081C)
)

val DropLinkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DropLinkTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) DropLinkDarkColorScheme else DropLinkLightColorScheme
    val extendedColors = if (useDark) {
        DropLinkExtendedColors(
            cardBorder = ObsidianBorder,
            cardElevated = ObsidianCardElevated,
            success = EmeraldSuccess,
            successContainer = EmeraldContainer,
            warning = AmberWarning,
            warningContainer = AmberContainer,
            cyan = SoftCyan,
            cyanContainer = SoftCyanContainer,
            isDark = true
        )
    } else {
        DropLinkExtendedColors(
            cardBorder = LightBorder,
            cardElevated = Color(0xFFF1F5FB),
            success = Color(0xFF059669),
            successContainer = Color(0xFFD1FAE5),
            warning = Color(0xFFD97706),
            warningContainer = Color(0xFFFEF3C7),
            cyan = SoftCyanLight,
            cyanContainer = SoftCyanLightContainer,
            isDark = false
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDark
                controller.isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalDropLinkColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DropLinkTypography,
            shapes = DropLinkShapes,
            content = content
        )
    }
}
