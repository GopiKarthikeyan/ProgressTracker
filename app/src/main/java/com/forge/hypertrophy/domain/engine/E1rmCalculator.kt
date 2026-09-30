package com.forge.hypertrophy.domain.engine

/**
 * Epley: weight × (1 + reps / 30.0). The divisor is a double so an int rep
 * count cannot collapse the estimate to the weight itself. The result is
 * rounded to [LoadRounding.DECIMAL_PLACES], not to a plate increment.
 */
object E1rmCalculator {
    fun epley(weightKg: Double, reps: Int): Double? {
        if (weightKg <= 0.0 || reps <= 0) return null
        val estimate = weightKg * (1.0 + reps / 30.0)
        return LoadRounding.roundToDecimals(estimate)
    }
}
