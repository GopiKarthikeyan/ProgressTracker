package com.forge.hypertrophy.domain.workout

import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SkillStageTargets
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMachineTest {
    @Test
    fun supersetWalksA1ThenA2ThenRestThenTransition() {
        var state = started(slot(1, 0, group = 1), slot(2, 1, group = 1))

        val first = workoutPosition(state) as WorkoutPosition.WorkingSet
        assertEquals(1L, first.slot.sessionSlotId)
        assertEquals(1, first.setNumber)

        state = log(state)
        val second = workoutPosition(state) as WorkoutPosition.WorkingSet
        assertEquals(2L, second.slot.sessionSlotId)
        assertEquals(1, second.setNumber)

        state = log(state)
        val rest = workoutPosition(state) as WorkoutPosition.Resting
        assertEquals(RestKind.BETWEEN_SETS, rest.kind)
        assertEquals(1L, rest.next?.sessionSlotId)

        state = dismissRest(state)
        assertEquals(1L, (workoutPosition(state) as WorkoutPosition.WorkingSet).slot.sessionSlotId)
        state = log(state)
        state = log(state)
        val transition = workoutPosition(state) as WorkoutPosition.Resting
        assertEquals(RestKind.TRANSITION, transition.kind)
    }

    @Test
    fun unilateralLogsLeftThenRightBeforeRest() {
        var state = started(slot(1, 0, group = null, sets = 1, unilateral = true))
        val left = workoutPosition(state) as WorkoutPosition.WorkingSet
        assertEquals(SetSide.LEFT, left.side)
        state = log(state)
        assertEquals(SetSide.RIGHT, (workoutPosition(state) as WorkoutPosition.WorkingSet).side)
        state = log(state)
        assertEquals(RestKind.TRANSITION, (workoutPosition(state) as WorkoutPosition.Resting).kind)
    }

    @Test
    fun reorderRewritesIndexesWithoutGaps() {
        val state = moveSlot(started(slot(1, 5, null), slot(2, 9, null), slot(3, 1, null)), from = 0, to = 2)
        val ordered = state.slots.sortedBy { it.sortOrder }
        assertEquals(listOf(0, 1, 2), ordered.map { it.sortOrder })
        assertEquals(listOf(1L, 2L, 3L), ordered.map { it.sessionSlotId })
        assertEquals(ordered.size, ordered.map { it.sortOrder }.distinct().size)
    }

    @Test
    fun jointFlagSuggestsSkippingAnOptionalCompound() {
        val optional = slot(2, 1, null, sets = 1, optional = true)
        val state = started(slot(1, 0, null, sets = 1), optional).copy(sessionJoints = setOf("knee"))
        assertFalse(suggestSkip(state.slots.first(), state))
        assertTrue(suggestSkip(optional, state))
    }

    @Test
    fun aHoldWithoutItsOwnTargetUsesTheSkillStage() {
        val hint = SkillHoldHint(stage = 1, targets = SkillStageTargets(stage1TotalSec = 12))
        val fresh = suggestionFor(holdSlot(skillHold = hint), emptyMap())
        assertEquals(12, fresh.holdSec)
        assertFalse(fresh.fromPreviousSession)

        val logged = holdSlot(skillHold = hint).copy(
            sets = listOf(RecordedSet(1, 1, SetSide.BOTH, null, null, 5, null, emptyList(), EntryMethod.SCREEN)),
        )
        assertEquals(7, suggestionFor(logged, emptyMap()).holdSec)
    }

    @Test
    fun anExplicitHoldTargetBeatsTheSkillStage() {
        val suggestion = suggestionFor(
            holdSlot(holdTargetSec = 30, skillHold = SkillHoldHint(stage = 1, targets = SkillStageTargets())),
            emptyMap(),
        )
        assertEquals(30, suggestion.holdSec)
    }

    @Test
    fun shortSetUsesRestMaxAndAMetTargetUsesRestMin() {
        assertEquals(90 to 90, restWindowSeconds(restAfter(reps = 4), transitionRestSeconds = 120))
        assertEquals(60 to 90, restWindowSeconds(restAfter(reps = 5), transitionRestSeconds = 120))
        assertEquals(60 to 90, restWindowSeconds(restAfter(reps = 8), transitionRestSeconds = 120))
    }

    @Test
    fun spokenCueMatchesTheSetScript() {
        assertEquals(
            "Set 2 of 3, weighted pull-ups, plus 20 kg, 5 to 8 reps",
            workoutCue(2, 3, "weighted pull-ups", 20.0, 5, 8, null),
        )
    }

    private fun started(vararg slots: WorkoutSlot) = WorkoutMachineState(slots = slots.toList(), started = true)

    private fun restAfter(reps: Int): WorkoutPosition.Resting {
        val state = started(slot(1, 0, null))
        val working = workoutPosition(state) as WorkoutPosition.WorkingSet
        val logged = logCurrentSet(
            state,
            RecordedSet(1, working.setNumber, working.side, null, reps, null, null, emptyList(), EntryMethod.SCREEN),
        )
        return workoutPosition(logged) as WorkoutPosition.Resting
    }

    private fun log(state: WorkoutMachineState): WorkoutMachineState {
        val working = workoutPosition(state) as WorkoutPosition.WorkingSet
        return logCurrentSet(
            state,
            RecordedSet(1, working.setNumber, working.side, null, 5, null, null, emptyList(), EntryMethod.SCREEN),
        )
    }

    private fun slot(
        id: Long,
        order: Int,
        group: Int?,
        sets: Int = 2,
        optional: Boolean = false,
        unilateral: Boolean = false,
    ) = WorkoutSlot(
        sessionSlotId = id,
        sortOrder = order,
        prescription = SlotPrescription(
            exerciseId = id,
            category = SlotCategory.COMPOUND,
            sortOrder = order,
            supersetGroup = group,
            metricType = MetricType.WEIGHT_REPS,
            setsMin = sets,
            setsMax = sets,
            repsLow = 5,
            repsHigh = 8,
            restMinSec = 60,
            restMaxSec = 90,
            isOptional = optional,
            progressionRule = ProgressionRule.DOUBLE,
        ),
        exerciseName = "slot-$id",
        setupNotes = "",
        unilateral = unilateral,
    )

    private fun holdSlot(holdTargetSec: Int? = null, skillHold: SkillHoldHint? = null) = WorkoutSlot(
        sessionSlotId = 1,
        sortOrder = 0,
        prescription = SlotPrescription(
            exerciseId = 1,
            category = SlotCategory.SKILL,
            sortOrder = 0,
            metricType = MetricType.HOLD,
            setsMin = 4,
            setsMax = 5,
            holdTargetSec = holdTargetSec,
            progressionRule = ProgressionRule.NONE,
        ),
        exerciseName = "hold",
        setupNotes = "",
        unilateral = false,
        skillHold = skillHold,
    )
}
