package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.WarmupStep
import java.math.BigDecimal

/**
 * Barbell compounds only: empty bar × 10, 50% × 5, 70% × 3, 85% × 1.
 * Each load is whatever [PlateCalculator] can actually put on the bar, and is
 * omitted once it is no heavier than the previous step or no longer under the
 * working weight.
 */
class WarmupRampGenerator(
    private val plates: PlateCalculator = PlateCalculator(),
) {
    fun ramp(
        equipment: Equipment,
        category: SlotCategory,
        workingKg: Double,
        barKg: Double,
        inventoryPerSideKg: List<Double>,
    ): List<WarmupStep> {
        if (!isBarbellCompound(equipment, category)) return emptyList()
        val working = LoadRounding.roundToDecimals(workingKg)
        val planned = listOf(
            barKg to BAR_REPS,
            percent(working, "0.50") to 5,
            percent(working, "0.70") to 3,
            percent(working, "0.85") to 1,
        )
        val steps = mutableListOf<WarmupStep>()
        for ((target, reps) in planned) {
            val loaded = plates.load(target, barKg, inventoryPerSideKg)
            if (loaded.totalKg >= working) continue
            val previousKg = steps.lastOrNull()?.weightKg
            if (previousKg != null && previousKg >= loaded.totalKg) continue
            steps += WarmupStep(
                weightKg = loaded.totalKg,
                reps = reps,
                platesPerSideKg = loaded.platesPerSideKg,
            )
        }
        return steps
    }

    private fun isBarbellCompound(equipment: Equipment, category: SlotCategory): Boolean {
        val barbell = equipment == Equipment.BARBELL || equipment == Equipment.EZ_BAR
        return barbell && category == SlotCategory.COMPOUND
    }

    private fun percent(workingKg: Double, fraction: String): Double {
        return BigDecimal.valueOf(workingKg).multiply(BigDecimal(fraction)).toDouble()
    }

    private companion object {
        const val BAR_REPS = 10
    }
}
