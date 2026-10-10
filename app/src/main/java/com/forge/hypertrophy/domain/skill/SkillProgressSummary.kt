package com.forge.hypertrophy.domain.skill

enum class SkillHoldTargetKind {
    TOTAL,
    UNBROKEN,
}

data class SkillProgressSummary(
    val stepIndex: Int,
    val stepCount: Int,
    val stepName: String,
    val stage: Int,
    val targetSec: Int,
    val targetKind: SkillHoldTargetKind,
)

data class SkillStepTargets(
    val id: Long,
    val name: String,
    val stage1TotalSec: Int,
    val stage2TotalHighSec: Int,
    val stage3UnbrokenSec: Int,
)

data class SkillProgressRef(
    val currentStepId: Long,
    val stage: Int,
)

/**
 * Current rung for a skill ladder. Ordered [steps] are the tiers. Missing
 * progress means the first step at stage 1. Stages 1 and 2 use a session
 * total; stage 3 uses one unbroken hold, matching [com.forge.hypertrophy.domain.engine.StaticSkillEngine].
 */
fun skillProgressSummary(
    steps: List<SkillStepTargets>,
    progress: SkillProgressRef?,
): SkillProgressSummary? {
    if (steps.isEmpty()) return null
    val current = steps.firstOrNull { it.id == progress?.currentStepId } ?: steps.first()
    val index = steps.indexOf(current).coerceAtLeast(0)
    val stage = (progress?.stage ?: 1).coerceIn(1, 3)
    val (targetSec, kind) = when (stage) {
        2 -> current.stage2TotalHighSec to SkillHoldTargetKind.TOTAL
        3 -> current.stage3UnbrokenSec to SkillHoldTargetKind.UNBROKEN
        else -> current.stage1TotalSec to SkillHoldTargetKind.TOTAL
    }
    return SkillProgressSummary(
        stepIndex = index,
        stepCount = steps.size,
        stepName = current.name,
        stage = stage,
        targetSec = targetSec,
        targetKind = kind,
    )
}
