package com.forge.hypertrophy.domain.engine

/**
 * Best single-set weight×reps per exercise, and the longest unbroken hold
 * per skill. e1RM stays in [PrDetector].
 *
 * A missing weight or rep count is skipped. A hold of zero is skipped. Ties
 * on the rounded product keep the heavier weight.
 */
class SetRecordCalculator {
    fun bestWeightTimesReps(history: List<LiftSample>): Map<Long, WeightRepsBest> {
        val best = mutableMapOf<Long, WeightRepsBest>()
        for (lift in history) {
            val weight = lift.weightKg ?: continue
            val reps = lift.reps ?: continue
            if (weight <= 0.0 || reps <= 0) continue
            val candidate = WeightRepsBest(LoadRounding.roundToDecimals(weight), reps)
            val previous = best[lift.exerciseId]
            if (previous == null || better(candidate, previous)) {
                best[lift.exerciseId] = candidate
            }
        }
        return best
    }

    fun maxUnbrokenHold(holds: List<HoldSample>): Map<Long, Int> {
        val best = mutableMapOf<Long, Int>()
        for (hold in holds) {
            if (hold.holdSec <= 0) continue
            val previous = best[hold.skillId]
            if (previous == null || hold.holdSec > previous) {
                best[hold.skillId] = hold.holdSec
            }
        }
        return best
    }

    private fun better(candidate: WeightRepsBest, previous: WeightRepsBest): Boolean {
        val left = product(candidate)
        val right = product(previous)
        return left > right || (left == right && candidate.weightKg > previous.weightKg)
    }

    private fun product(record: WeightRepsBest): Double {
        return LoadRounding.roundToDecimals(record.weightKg * record.reps)
    }
}

data class WeightRepsBest(
    val weightKg: Double,
    val reps: Int,
)

data class HoldSample(
    val skillId: Long,
    val holdSec: Int,
)
