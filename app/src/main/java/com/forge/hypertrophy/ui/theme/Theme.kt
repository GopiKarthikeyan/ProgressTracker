package com.forge.hypertrophy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AmoledColorScheme = darkColorScheme(
    primary = NeonAccent,
    onPrimary = Black,
    primaryContainer = Black,
    onPrimaryContainer = NeonAccent,
    secondary = NeonAccent,
    onSecondary = Black,
    secondaryContainer = Black,
    onSecondaryContainer = NeonAccent,
    tertiary = NeonAccent,
    onTertiary = Black,
    tertiaryContainer = Black,
    onTertiaryContainer = NeonAccent,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Black,
    onSurfaceVariant = White,
    surfaceTint = Color.Transparent,
    inverseSurface = White,
    inverseOnSurface = Black,
    inversePrimary = NeonAccent,
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF1A1A1A),
    scrim = Black,
    error = Color(0xFFFFB4AB),
    onError = Black,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = White,
)

@Composable
fun HypertrophyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmoledColorScheme,
        typography = Typography,
        content = content,
    )
}
