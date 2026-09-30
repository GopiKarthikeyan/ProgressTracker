package com.forge.hypertrophy.domain.model

/**
 * Targets for one tier. Stage 2 advances at [stage2TotalHighSec], the top of
 * the 15–18s range. [stage2TotalLowSec] is the bottom of that range and is not
 * itself the advance line.
 */
data class SkillStageTargets(
    val stage1TotalSec: Int = 12,
    val stage2TotalLowSec: Int = 15,
    val stage2TotalHighSec: Int = 18,
    val stage3UnbrokenSec: Int = 10,
)

/** [tierIndex] is 0-based along the ladder. [stage] is 1, 2, or 3. */
data class SkillPosition(
    val tierIndex: Int,
    val stage: Int,
)
