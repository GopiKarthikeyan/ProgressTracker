package com.forge.hypertrophy.domain.workout

/**
 * Allow-list for [WorkoutPosition] phases produced by [workoutPosition].
 * Same-phase updates are allowed because ticks, steppers, and partial check-offs republish the current phase.
 * [WorkoutPosition.Summary] returns to [WorkoutPosition.WorkingSet] only when [resetSession] is true.
 */
class WorkoutPositionTransitionValidator {
    fun allow(from: WorkoutPosition, to: WorkoutPosition, resetSession: Boolean = false): Boolean {
        val source = phase(from)
        val target = phase(to)
        if (source == Phase.SUMMARY && target == Phase.WORKING_SET) return resetSession
        return target in allowed.getValue(source)
    }

    private enum class Phase {
        READINESS,
        PREP,
        PRACTICE_BLOCK,
        WORKING_SET,
        RESTING,
        COOLDOWN,
        SUMMARY,
    }

    private fun phase(position: WorkoutPosition): Phase = when (position) {
        WorkoutPosition.Readiness -> Phase.READINESS
        is WorkoutPosition.Prep -> Phase.PREP
        is WorkoutPosition.PracticeBlock -> Phase.PRACTICE_BLOCK
        is WorkoutPosition.WorkingSet -> Phase.WORKING_SET
        is WorkoutPosition.Resting -> Phase.RESTING
        is WorkoutPosition.Cooldown -> Phase.COOLDOWN
        WorkoutPosition.Summary -> Phase.SUMMARY
    }

    private companion object {
        val allowed: Map<Phase, Set<Phase>> = mapOf(
            Phase.READINESS to setOf(
                Phase.READINESS,
                Phase.PREP,
                Phase.PRACTICE_BLOCK,
                Phase.WORKING_SET,
                Phase.RESTING,
                Phase.COOLDOWN,
                Phase.SUMMARY,
            ),
            Phase.PREP to setOf(
                Phase.PREP,
                Phase.PRACTICE_BLOCK,
                Phase.WORKING_SET,
                Phase.COOLDOWN,
                Phase.SUMMARY,
            ),
            Phase.PRACTICE_BLOCK to setOf(
                Phase.PRACTICE_BLOCK,
                Phase.WORKING_SET,
                Phase.RESTING,
                Phase.COOLDOWN,
                Phase.SUMMARY,
            ),
            Phase.WORKING_SET to setOf(
                Phase.WORKING_SET,
                Phase.PRACTICE_BLOCK,
                Phase.RESTING,
                Phase.COOLDOWN,
                Phase.SUMMARY,
            ),
            Phase.RESTING to setOf(
                Phase.RESTING,
                Phase.WORKING_SET,
                Phase.PRACTICE_BLOCK,
                Phase.COOLDOWN,
                Phase.SUMMARY,
            ),
            Phase.COOLDOWN to setOf(
                Phase.COOLDOWN,
                Phase.SUMMARY,
            ),
            Phase.SUMMARY to setOf(
                Phase.SUMMARY,
            ),
        )
    }
}
