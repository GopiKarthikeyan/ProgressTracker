package com.forge.hypertrophy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

private val DarkColorScheme = darkColorScheme(
    primary = Rose,
    onPrimary = White,
    primaryContainer = Charcoal,
    onPrimaryContainer = White,
    secondary = DarkMuted,
    onSecondary = AmoledBlack,
    secondaryContainer = DarkSurface,
    onSecondaryContainer = White,
    tertiary = RoseSoft,
    onTertiary = AmoledBlack,
    tertiaryContainer = Charcoal,
    onTertiaryContainer = White,
    background = AmoledBlack,
    onBackground = White,
    surface = DarkSurface,
    onSurface = White,
    surfaceVariant = Charcoal,
    onSurfaceVariant = DarkMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = Cream,
    inverseOnSurface = Ink,
    inversePrimary = Rose,
    outline = DarkOutline,
    outlineVariant = Charcoal,
    scrim = Color.Black.copy(alpha = 0.6f),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun HypertrophyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else WarmColorScheme,
        typography = Typography,
        content = content,
    )
}
