package com.forge.hypertrophy.domain.workout

import com.forge.hypertrophy.domain.model.SkillStageTargets

/** The lifter's current rung, used when a hold slot has no target of its own. */
data class SkillHoldHint(
    val stage: Int,
    val targets: SkillStageTargets,
)

/**
 * Seconds to offer for the next hold. Stages 1 and 2 are session totals, so a
 * set suggests whatever is still needed. Stage 3 is one unbroken hold.
 * Once the total is already met, later sets suggest the full target again.
 */
fun skillHoldSeconds(stage: Int, targets: SkillStageTargets, loggedHoldSec: Int): Int {
    val logged = loggedHoldSec.coerceAtLeast(0)
    return when (stage) {
        3 -> targets.stage3UnbrokenSec.coerceAtLeast(1)
        2 -> remainingOrFull(targets.stage2TotalHighSec, logged)
        else -> remainingOrFull(targets.stage1TotalSec, logged)
    }
}

private fun remainingOrFull(target: Int, logged: Int): Int {
    val goal = target.coerceAtLeast(1)
    val remaining = goal - logged
    return if (remaining > 0) remaining else goal
}
