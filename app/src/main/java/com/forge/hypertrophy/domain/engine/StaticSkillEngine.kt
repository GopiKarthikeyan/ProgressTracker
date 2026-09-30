package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.SkillPosition
import com.forge.hypertrophy.domain.model.SkillStageTargets

/**
 * Tier ladder for a static skill.
 *
 * Stage 1 advances when the total hold reaches [SkillStageTargets.stage1TotalSec].
 * Stage 2 advances when the total hold reaches [SkillStageTargets.stage2TotalHighSec]
 * (18s by default). A total inside 15–17s is still stage 2. Stage 3 advances
 * when the unbroken hold reaches [SkillStageTargets.stage3UnbrokenSec], and the
 * next position is stage 1 of the next tier.
 *
 * An attempt advances only when that target is met and form was confirmed once
 * for this skill in this session. [promote] and [demote] ignore the attempt.
 *
 * [demote] from stage 1 moves to stage 3 of the previous tier. Tier 0 stage 1
 * clamps there. [promote] from stage 3 of the last tier clamps there.
 */
class StaticSkillEngine {
    fun afterAttempt(
        position: SkillPosition,
        targets: SkillStageTargets,
        totalHoldSec: Int,
        unbrokenHoldSec: Int,
        formConfirmed: Boolean,
        tierCount: Int,
    ): SkillPosition {
        if (!formConfirmed || !targetMet(position.stage, targets, totalHoldSec, unbrokenHoldSec)) {
            return position
        }
        return promote(position, tierCount)
    }

    fun promote(position: SkillPosition, tierCount: Int): SkillPosition {
        return when (position.stage) {
            1 -> position.copy(stage = 2)
            2 -> position.copy(stage = 3)
            else -> {
                val nextTier = position.tierIndex + 1
                if (nextTier >= tierCount) {
                    position.copy(stage = LAST_STAGE)
                } else {
                    SkillPosition(tierIndex = nextTier, stage = 1)
                }
            }
        }
    }

    fun demote(position: SkillPosition): SkillPosition {
        return when {
            position.stage > 1 -> position.copy(stage = position.stage - 1)
            position.tierIndex > 0 -> SkillPosition(tierIndex = position.tierIndex - 1, stage = LAST_STAGE)
            else -> SkillPosition(tierIndex = 0, stage = 1)
        }
    }

    private fun targetMet(
        stage: Int,
        targets: SkillStageTargets,
        totalHoldSec: Int,
        unbrokenHoldSec: Int,
    ): Boolean {
        return when (stage) {
            1 -> totalHoldSec >= targets.stage1TotalSec
            2 -> totalHoldSec >= targets.stage2TotalHighSec
            3 -> unbrokenHoldSec >= targets.stage3UnbrokenSec
            else -> false
        }
    }

    private companion object {
        const val LAST_STAGE = 3
    }
}
