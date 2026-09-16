package ru.railbrake.calculator.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AccentPalette(val title: String, val primary: Color, val container: Color) {
    BLUE("Янтарный", RailAmber, RailAmberSoft),
    GREEN("Стальной зелёный", Color(0xFF68C9A5), Color(0xFF18342C)),
    YELLOW("Сигнальный жёлтый", Color(0xFFFFC857), Color(0xFF3B3017)),
    PURPLE("Холодный фиолетовый", Color(0xFFB9A3E8), Color(0xFF302744))
}

private fun darkColors(palette: AccentPalette) = darkColorScheme(
    primary = palette.primary,
    onPrimary = Color(0xFF11161B),
    primaryContainer = palette.container,
    onPrimaryContainer = Ink,
    secondary = RailMetal,
    onSecondary = Color(0xFF1C252E),
    secondaryContainer = Color(0xFF232C35),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF88A7C5),
    onTertiary = Color(0xFF10202E),
    tertiaryContainer = Color(0xFF20303D),
    onTertiaryContainer = Color(0xFFDCECF7),
    background = Night,
    onBackground = Ink,
    surface = NightRaised,
    onSurface = Ink,
    surfaceVariant = NightCardAlt,
    onSurfaceVariant = InkMuted,
    error = Danger,
    errorContainer = Color(0xFF3B1C1E),
    onErrorContainer = Color(0xFFFFDAD9),
    outline = Divider,
    outlineVariant = Color(0xFF222B34)
)

@Composable
fun RailBrakeTheme(palette: AccentPalette = AccentPalette.BLUE, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(colorScheme = darkColors(palette), content = content)
}
