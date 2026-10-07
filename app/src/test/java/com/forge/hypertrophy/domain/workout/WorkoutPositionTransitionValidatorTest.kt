package com.forge.hypertrophy.domain.workout

import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPositionTransitionValidatorTest {
    private val validator = WorkoutPositionTransitionValidator()
    private val positions: List<Pair<String, WorkoutPosition>> = listOf(
        "Readiness" to WorkoutPosition.Readiness,
        "Prep" to WorkoutPosition.Prep(emptyList()),
        "PracticeBlock" to WorkoutPosition.PracticeBlock(slot()),
        "WorkingSet" to workingSet(),
        "Resting" to WorkoutPosition.Resting(slot(), RestKind.BETWEEN_SETS, round = 1, next = null),
        "Cooldown" to WorkoutPosition.Cooldown(emptyList()),
        "Summary" to WorkoutPosition.Summary,
    )

    private val allowed = mapOf(
        "Readiness" to setOf("Readiness", "Prep", "PracticeBlock", "WorkingSet", "Resting", "Cooldown", "Summary"),
        "Prep" to setOf("Prep", "PracticeBlock", "WorkingSet", "Cooldown", "Summary"),
        "PracticeBlock" to setOf("PracticeBlock", "WorkingSet", "Resting", "Cooldown", "Summary"),
        "WorkingSet" to setOf("WorkingSet", "PracticeBlock", "Resting", "Cooldown", "Summary"),
        "Resting" to setOf("Resting", "WorkingSet", "PracticeBlock", "Cooldown", "Summary"),
        "Cooldown" to setOf("Cooldown", "Summary"),
        "Summary" to setOf("Summary"),
    )

    @Test
    fun everyPhasePairMatchesTheAllowList() {
        positions.forEach { (fromName, from) ->
            positions.forEach { (toName, to) ->
                assertEquals(
                    "$fromName -> $toName",
                    toName in allowed.getValue(fromName),
                    validator.allow(from, to),
                )
            }
        }
    }

    @Test
    fun summaryToWorkingSetIsRejectedWithoutResetSession() {
        assertFalse(validator.allow(WorkoutPosition.Summary, workingSet(), resetSession = false))
        assertTrue(validator.allow(WorkoutPosition.Summary, workingSet(), resetSession = true))
    }
}

private fun workingSet() = WorkoutPosition.WorkingSet(
    slot = slot(),
    setNumber = 1,
    setCount = 1,
    side = SetSide.BOTH,
    suggestion = SetSuggestion(weightKg = null, reps = 5, holdSec = null, fromPreviousSession = false),
    suggestSkip = false,
    needsFormCheck = false,
)

private fun slot() = WorkoutSlot(
    sessionSlotId = 1,
    sortOrder = 0,
    prescription = SlotPrescription(
        exerciseId = 1,
        category = SlotCategory.COMPOUND,
        sortOrder = 0,
        metricType = MetricType.WEIGHT_REPS,
        setsMin = 1,
        setsMax = 1,
        progressionRule = ProgressionRule.NONE,
    ),
    exerciseName = "press",
    setupNotes = "",
    unilateral = false,
)
