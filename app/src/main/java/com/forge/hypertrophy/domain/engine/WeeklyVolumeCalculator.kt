package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.SetType

data class VolumeSet(
    val setType: SetType,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
)

/**
 * Hard-set volume per muscle for one week.
 *
 * [secondaryWeight] defaults to 0.5. That fraction is a Phase 2 decision, not
 * a requirement in SPEC.md.
 *
 * [SetType.WORKING] and [SetType.AMRAP] each count as one hard set.
 * [SetType.WARMUP] and [SetType.DELOAD] do not. A muscle listed as both
 * primary and secondary on the same set counts once, as primary.
 */
class WeeklyVolumeCalculator(
    private val secondaryWeight: Double = DEFAULT_SECONDARY_WEIGHT,
) {
    fun volume(sets: List<VolumeSet>): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        for (set in sets) {
            if (set.setType != SetType.WORKING && set.setType != SetType.AMRAP) continue
            val primary = set.primaryMuscles.toSet()
            for (muscle in primary) {
                totals[muscle] = (totals[muscle] ?: 0.0) + 1.0
            }
            for (muscle in set.secondaryMuscles) {
                if (muscle in primary) continue
                totals[muscle] = (totals[muscle] ?: 0.0) + secondaryWeight
            }
        }
        return totals
    }

    private companion object {
        const val DEFAULT_SECONDARY_WEIGHT = 0.5
    }
}
