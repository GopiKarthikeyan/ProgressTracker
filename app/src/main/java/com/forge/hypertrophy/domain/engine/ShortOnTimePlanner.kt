package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.TrainingSlot

/**
 * Fits a day into [budgetSeconds].
 *
 * Optional slots are removed first, from the end of the day, whatever their
 * category. Only after none remain does it remove trailing isolation sets,
 * one set at a time. A required compound keeps every working set while any
 * isolation set is still on the day. If the budget is still missed, the
 * compounds stay.
 */
class ShortOnTimePlanner(
    private val estimator: SessionEstimator = SessionEstimator(),
) {
    fun plan(
        slots: List<TrainingSlot>,
        budgetSeconds: Int,
        transitionRestSeconds: Int,
    ): List<TrainingSlot> {
        val current = slots.toMutableList()
        while (current.isNotEmpty() && estimator.estimate(current, transitionRestSeconds) > budgetSeconds) {
            val optionalIndex = current.indexOfLast { it.prescription.isOptional }
            if (optionalIndex >= 0) {
                current.removeAt(optionalIndex)
                continue
            }
            val isolationIndex = current.indexOfLast { slot ->
                slot.prescription.category == SlotCategory.ISOLATION && slot.prescription.setsMax > 0
            }
            if (isolationIndex < 0) break
            val slot = current[isolationIndex]
            val nextMax = slot.prescription.setsMax - 1
            if (nextMax <= 0) {
                current.removeAt(isolationIndex)
            } else {
                val nextMin = minOf(slot.prescription.setsMin, nextMax)
                current[isolationIndex] = slot.copy(
                    prescription = slot.prescription.copy(setsMax = nextMax, setsMin = nextMin),
                )
            }
        }
        return current
    }
}
