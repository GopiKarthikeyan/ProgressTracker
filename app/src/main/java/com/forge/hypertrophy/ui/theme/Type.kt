package com.forge.hypertrophy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

private fun TextStyle.tabularNumerals(): TextStyle = copy(fontFeatureSettings = "tnum")

private fun Typography.withTabularNumerals(): Typography = copy(
    displayLarge = displayLarge.tabularNumerals(),
    displayMedium = displayMedium.tabularNumerals(),
    displaySmall = displaySmall.tabularNumerals(),
    headlineLarge = headlineLarge.tabularNumerals(),
    headlineMedium = headlineMedium.tabularNumerals(),
    headlineSmall = headlineSmall.tabularNumerals(),
    titleLarge = titleLarge.tabularNumerals(),
    titleMedium = titleMedium.tabularNumerals(),
    titleSmall = titleSmall.tabularNumerals(),
    bodyLarge = bodyLarge.tabularNumerals(),
    bodyMedium = bodyMedium.tabularNumerals(),
    bodySmall = bodySmall.tabularNumerals(),
    labelLarge = labelLarge.tabularNumerals(),
    labelMedium = labelMedium.tabularNumerals(),
    labelSmall = labelSmall.tabularNumerals(),
)

val Typography = Typography().withTabularNumerals()
