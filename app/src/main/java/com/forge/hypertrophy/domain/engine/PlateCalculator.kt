package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.PlateLoad
import java.math.BigDecimal

/**
 * [inventoryPerSideKg] is the multiset of plates available on one side. Two
 * 20kg plates in the gym, one per side, is a single 20.0 entry.
 */
class PlateCalculator {
    fun load(
        targetKg: Double,
        barKg: Double,
        inventoryPerSideKg: List<Double>,
    ): PlateLoad {
        val bar = LoadRounding.roundToDecimals(barKg)
        val requested = LoadRounding.roundToDecimals(targetKg)
        var remaining = cents(requested) - cents(bar)
        if (remaining < 0) remaining = 0
        val perSideBudget = remaining / 2
        val chosen = mutableListOf<Double>()
        var left = perSideBudget
        val plates = inventoryPerSideKg
            .map { LoadRounding.roundToDecimals(it) }
            .sortedDescending()
        for (plate in plates) {
            val size = cents(plate)
            if (size in 1..left) {
                chosen += plate
                left -= size
            }
        }
        val perSideKg = LoadRounding.roundToDecimals(chosen.sum())
        val totalKg = LoadRounding.roundToDecimals(bar + perSideKg + perSideKg)
        return PlateLoad(
            barKg = bar,
            platesPerSideKg = chosen,
            totalKg = totalKg,
            requestedKg = requested,
            exactMatch = cents(totalKg) == cents(requested),
        )
    }

    private fun cents(kg: Double): Int {
        return BigDecimal.valueOf(LoadRounding.roundToDecimals(kg))
            .movePointRight(LoadRounding.DECIMAL_PLACES)
            .intValueExact()
    }
}
