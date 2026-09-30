package com.forge.hypertrophy.domain.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Two different roundings. [roundToIncrement] snaps to a loadable step (plate
 * or dumbbell increment). [roundToDecimals] snaps to a fixed decimal precision
 * and is what e1RM uses. Loadable results are still passed through
 * [roundToDecimals] so a caller never sees a value like 82.49999.
 */
object LoadRounding {
    const val DECIMAL_PLACES = 2

    fun roundToIncrement(kg: Double, incrementKg: Double): Double {
        if (incrementKg <= 0.0) return roundToDecimals(kg)
        val increment = BigDecimal.valueOf(incrementKg)
        val steps = BigDecimal.valueOf(kg).divide(increment, 0, RoundingMode.HALF_UP)
        return roundToDecimals(steps.multiply(increment).toDouble())
    }

    fun roundToDecimals(kg: Double, places: Int = DECIMAL_PLACES): Double {
        val text = BigDecimal.valueOf(kg).setScale(places, RoundingMode.HALF_UP).toPlainString()
        return text.toDouble()
    }
}
