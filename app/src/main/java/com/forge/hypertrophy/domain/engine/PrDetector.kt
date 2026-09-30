package com.forge.hypertrophy.domain.engine

/**
 * Best e1RM per exercise, across every slot that exercise appears in.
 *
 * Estimates come from [E1rmCalculator], which already rounds to 2 decimal
 * places, and the comparison uses those rounded values. An equal rounded
 * estimate is not a new PR.
 *
 * e1RM is undefined when there is no positive external load, including
 * bodyweight reps. [E1rmCalculator.epley] returns null for those, and they
 * are skipped. A PR also requires a previous rounded estimate to beat, so an
 * empty history yields no PR.
 */
class PrDetector {
    fun bestByExercise(history: List<LiftSample>): Map<Long, Double> {
        val best = mutableMapOf<Long, Double>()
        for (lift in history) {
            val estimate = estimate(lift) ?: continue
            val previous = best[lift.exerciseId]
            if (previous == null || estimate > previous) {
                best[lift.exerciseId] = estimate
            }
        }
        return best
    }

    fun isNewPr(history: List<LiftSample>, candidate: LiftSample): Boolean {
        val estimate = estimate(candidate) ?: return false
        val previous = bestByExercise(history)[candidate.exerciseId] ?: return false
        return estimate > previous
    }

    private fun estimate(lift: LiftSample): Double? {
        val weight = lift.weightKg ?: return null
        val reps = lift.reps ?: return null
        val rounded = E1rmCalculator.epley(weight, reps) ?: return null
        return LoadRounding.roundToDecimals(rounded)
    }
}

data class LiftSample(
    val exerciseId: Long,
    val slotId: Long,
    val weightKg: Double?,
    val reps: Int?,
)
