package com.forge.hypertrophy.domain.engine

data class ReadinessCheck(
    val sleep: Int?,
    val soreness: Int?,
    val energy: Int?,
)

enum class ReadinessAdvice {
    NONE,
    SHORT_ON_TIME_HOLD_WEIGHTS,
}

/**
 * Three unclamped component scores. Nothing rejects or clamps a value, so a 7
 * is added as 7. On the intended 1–3 scale, 1 is worst and 3 is best: a sum
 * of 3 is the worst total and a sum of 9 is the best.
 *
 * Any null component means the check was skipped and yields
 * [ReadinessAdvice.NONE]. When all three are present and their raw sum is
 * 4 or less, advise a short session and holding the current weights. A sum
 * of 5 or more yields no advice.
 */
class ReadinessAdvisor {
    fun advise(check: ReadinessCheck): ReadinessAdvice {
        val sleep = check.sleep ?: return ReadinessAdvice.NONE
        val soreness = check.soreness ?: return ReadinessAdvice.NONE
        val energy = check.energy ?: return ReadinessAdvice.NONE
        val sum = sleep + soreness + energy
        return if (sum <= ADVISE_AT_OR_BELOW) {
            ReadinessAdvice.SHORT_ON_TIME_HOLD_WEIGHTS
        } else {
            ReadinessAdvice.NONE
        }
    }

    private companion object {
        const val ADVISE_AT_OR_BELOW = 4
    }
}
