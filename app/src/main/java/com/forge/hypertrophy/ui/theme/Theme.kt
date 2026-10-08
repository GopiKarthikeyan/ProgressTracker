package com.forge.hypertrophy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WarmColorScheme = lightColorScheme(
    primary = Rose,
    onPrimary = White,
    primaryContainer = Sand,
    onPrimaryContainer = Ink,
    secondary = Charcoal,
    onSecondary = White,
    secondaryContainer = Sand,
    onSecondaryContainer = Ink,
    tertiary = Rose,
    onTertiary = White,
    tertiaryContainer = RoseSoft,
    onTertiaryContainer = Ink,
    background = Cream,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = Muted,
    surfaceTint = Color.Transparent,
    inverseSurface = Charcoal,
    inverseOnSurface = White,
    inversePrimary = Rose,
    outline = Color(0xFFE0D5C8),
    outlineVariant = Sand,
    scrim = Ink.copy(alpha = 0.4f),
    error = Color(0xFFB3261E),
    onError = White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

@Composable
fun HypertrophyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WarmColorScheme,
        typography = Typography,
        content = content,
    )
}
