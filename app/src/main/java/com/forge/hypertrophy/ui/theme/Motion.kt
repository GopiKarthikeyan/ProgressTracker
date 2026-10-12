package com.forge.hypertrophy.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset

/**
 * One place for the app's motion, so transitions stay consistent as screens
 * gain animation.
 *
 * Material 3 Expressive's `MotionScheme` would own this, but it is `internal`
 * in material3 1.4.0 (see [HypertrophyTheme]). These springs mirror the
 * expressive spatial/effects tokens: spatial motion is allowed a little
 * overshoot, effects such as fades are damped so colour and alpha never
 * bounce.
 */
object ForgeMotion {
    /** Spatial movement — offsets, slides, list reordering. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Non-spatial change — alpha, colour. Never overshoots. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Slide distance for forward/back navigation, as a fraction of width. */
    const val NAV_SLIDE_FRACTION = 0.12f

    fun navSlideIn(width: Int): IntOffset = IntOffset((width * NAV_SLIDE_FRACTION).toInt(), 0)
}
