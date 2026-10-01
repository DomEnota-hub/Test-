package ru.railbrake.calculator.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AccentPalette(val title: String, val primary: Color, val container: Color) {
    BLUE("Холодный синий", Color(0xFF82A9FF), Color(0xFF182947)),
    AMBER("Янтарный", RailAmber, RailAmberSoft),
    GREEN("Яркий зелёный", Color(0xFF4ADE80), Color(0xFF14532D)),
    YELLOW("Сигнальный жёлтый", Color(0xFFFFC857), Color(0xFF3B3017)),
    PURPLE("Холодный фиолетовый", Color(0xFFB9A3E8), Color(0xFF302744))
}

enum class AppThemeMode(val title: String) {
    LIGHT("Светлая"),
    DARK("Тёмная")
}

@Immutable
data class RailSemanticColors(
    val info: Color,
    val infoContainer: Color,
    val infoBorder: Color,
    val warning: Color,
    val warningContainer: Color,
    val warningBorder: Color,
    val success: Color,
    val successContainer: Color,
    val successBorder: Color,
    val disabledContent: Color
)

private val LocalRailSemanticColors = staticCompositionLocalOf {
    RailSemanticColors(
        info = Accent,
        infoContainer = AccentSoft,
        infoBorder = Accent.copy(alpha = 0.42f),
        warning = Warning,
        warningContainer = Warning.copy(alpha = 0.08f),
        warningBorder = Warning.copy(alpha = 0.32f),
        success = Success,
        successContainer = Success.copy(alpha = 0.10f),
        successBorder = Success.copy(alpha = 0.38f),
        disabledContent = RailSteel
    )
}

object RailTheme {
    val colors: RailSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalRailSemanticColors.current
}

private data class LightAccentTokens(
    val primary: Color,
    val container: Color
)

private fun lightAccentTokens(palette: AccentPalette): LightAccentTokens = when (palette) {
    AccentPalette.BLUE -> LightAccentTokens(Color(0xFF1D5FAE), Color(0xFFDCE8FF))
    AccentPalette.AMBER -> LightAccentTokens(Color(0xFFA34B00), Color(0xFFFFE0C2))
    AccentPalette.GREEN -> LightAccentTokens(Color(0xFF16A34A), Color(0xFFDCFCE7))
    AccentPalette.YELLOW -> LightAccentTokens(Color(0xFF765C00), Color(0xFFFFEFA8))
    AccentPalette.PURPLE -> LightAccentTokens(Color(0xFF69499E), Color(0xFFEADFFA))
}

private fun darkColors(palette: AccentPalette) = darkColorScheme(
    primary = palette.primary,
    onPrimary = Color(0xFF11161B),
    primaryContainer = palette.container,
    onPrimaryContainer = Ink,
    secondary = RailMetal,
    onSecondary = Color(0xFF1C252E),
    secondaryContainer = palette.container,
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF88A7C5),
    onTertiary = Color(0xFF10202E),
    tertiaryContainer = Color(0xFF20303D),
    onTertiaryContainer = Color(0xFFDCECF7),
    background = Night,
    onBackground = Ink,
    surface = NightRaised,
    onSurface = Ink,
    surfaceVariant = palette.container,
    onSurfaceVariant = InkMuted,
    error = Danger,
    errorContainer = Color(0xFF3B1C1E),
    onErrorContainer = Color(0xFFFFDAD9),
    outline = Divider,
    outlineVariant = Color(0xFF222B34)
)

private fun lightColors(palette: AccentPalette) = lightAccentTokens(palette).let { accent ->
    lightColorScheme(
        primary = accent.primary,
        onPrimary = if (palette == AccentPalette.GREEN) Color(0xFF052E16) else Color.White,
        primaryContainer = accent.container,
        onPrimaryContainer = Color(0xFF172033),
        secondary = Color(0xFF4A5B6E),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCE4ED),
        onSecondaryContainer = Color(0xFF17202A),
        tertiary = Color(0xFF425F77),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD8E7F2),
        onTertiaryContainer = Color(0xFF102331),
        background = Color(0xFFF4F6F8),
        onBackground = Color(0xFF191C20),
        surface = Color(0xFFFDFDFE),
        onSurface = Color(0xFF191C20),
        surfaceVariant = Color(0xFFE9EDF1),
        onSurfaceVariant = Color(0xFF3F454C),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF676D75),
        outlineVariant = Color(0xFFB7BCC4)
    )
}

private fun darkSemanticColors(palette: AccentPalette) = RailSemanticColors(
    info = palette.primary,
    infoContainer = palette.container,
    infoBorder = palette.primary.copy(alpha = 0.42f),
    warning = Warning,
    warningContainer = Warning.copy(alpha = 0.08f),
    warningBorder = Warning.copy(alpha = 0.32f),
    success = Success,
    successContainer = Success.copy(alpha = 0.10f),
    successBorder = Success.copy(alpha = 0.38f),
    disabledContent = RailSteel
)

private fun lightSemanticColors(palette: AccentPalette): RailSemanticColors {
    val accent = lightAccentTokens(palette)
    return RailSemanticColors(
        info = accent.primary,
        infoContainer = accent.container.copy(alpha = 0.72f),
        infoBorder = accent.primary.copy(alpha = 0.58f),
        warning = Color(0xFF984600),
        warningContainer = Color(0xFFFFF0DF),
        warningBorder = Color(0xFFD47B27),
        success = Color(0xFF176A4B),
        successContainer = Color(0xFFE0F4EB),
        successBorder = Color(0xFF65AA8F),
        disabledContent = Color(0xFF68717A)
    )
}

@Composable
fun RailBrakeTheme(
    palette: AccentPalette = AccentPalette.BLUE,
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val isLight = themeMode == AppThemeMode.LIGHT
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isLight
        }
    }
    CompositionLocalProvider(
        LocalRailSemanticColors provides if (isLight) lightSemanticColors(palette) else darkSemanticColors(palette)
    ) {
        MaterialTheme(
            colorScheme = if (isLight) lightColors(palette) else darkColors(palette),
            content = content
        )
    }
}
