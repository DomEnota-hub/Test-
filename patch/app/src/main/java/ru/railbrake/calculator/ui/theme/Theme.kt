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
    BLUE("Холодный синий", Color(0xFF82A9FF), Color(0xFF182947)),
    GREEN("Яркий зелёный", Color(0xFF62E675), Color(0xFF153B24)),
    YELLOW("Сигнальный жёлтый", Color(0xFFFFD54F), Color(0xFF443814)),
    PURPLE("Фиолетовый", Color(0xFFC6A0FF), Color(0xFF35204F)),
    RED("Красный", Color(0xFFFF8A80), Color(0xFF4B2020))
}

private fun darkColors(palette: AccentPalette) = darkColorScheme(
    primary = palette.primary,
    onPrimary = Color(0xFF0A1833),
    primaryContainer = palette.container,
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFBAC8DD),
    onSecondary = Color(0xFF1B2839),
    background = Night,
    onBackground = Ink,
    surface = NightRaised,
    onSurface = Ink,
    surfaceVariant = NightCardAlt,
    onSurfaceVariant = InkMuted,
    error = Danger,
    outline = Divider,
    outlineVariant = Color(0xFF202B39)
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
