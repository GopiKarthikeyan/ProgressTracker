package com.forge.hypertrophy.domain.usecase

import com.forge.hypertrophy.data.entity.SessionSlotEntity
import com.forge.hypertrophy.data.repository.BaselineRepository
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.model.ChecklistPhase
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.workout.BaselineHint
import com.forge.hypertrophy.domain.workout.ChecklistStep
import com.forge.hypertrophy.domain.workout.ExerciseChoice
import com.forge.hypertrophy.domain.workout.RecordedSet
import com.forge.hypertrophy.domain.workout.WorkoutMachineState
import com.forge.hypertrophy.domain.workout.WorkoutSlot
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class LoadWorkoutUseCase @Inject constructor(
    private val sessions: SessionRepository,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val preferences: TrainingPreferencesRepository,
    private val baselines: BaselineRepository,
) {
    suspend fun execute(sessionId: Long): WorkoutMachineState {
        val session = sessions.get(sessionId) ?: return WorkoutMachineState()
        val stored = sessions.observeSlots(sessionId).first()
        val workoutSlots = stored.map { toWorkoutSlot(it) }.sortedBy { it.sortOrder }
        
        val progressed = workoutSlots.any { it.skipped || it.sets.isNotEmpty() } || session.status == SessionStatus.COMPLETED
        val checklist = session.dayId?.let { routines.observeChecklist(it).first() }.orEmpty()
        
        return WorkoutMachineState(
            slots = workoutSlots,
            prep = checklist.filter { it.phase == ChecklistPhase.PREP }.map { ChecklistStep(it.id, it.text, done = progressed) },
            cooldown = checklist.filter { it.phase == ChecklistPhase.COOLDOWN }.map {
                ChecklistStep(it.id, it.text, done = session.status == SessionStatus.COMPLETED)
            },
            started = session.startedAt != null,
            transitionRestSeconds = preferences.transitionRestSeconds.first(),
            previousByExercise = previousSets(sessionId, workoutSlots),
            ownHistoryBySessionSlot = ownHistory(sessionId, workoutSlots),
            baselineBySessionSlot = baselineHints(workoutSlots),
            sessionJoints = workoutSlots.flatMap { slot -> slot.sets.flatMap { it.jointFlags } }.toSet(),
        )
    }

    private suspend fun toWorkoutSlot(entity: SessionSlotEntity): WorkoutSlot {
        val prescription = entity.prescriptionSnapshot
        val exerciseId = entity.chosenAlternativeExerciseId ?: prescription.exerciseId
        val exercise = exercises.get(exerciseId)
        val routineId = entity.slotId
        val alternatives = if (routineId == null) {
            emptyList()
        } else {
            routines.observeAlternatives(routineId).first().mapNotNull { alternative ->
                exercises.get(alternative.exerciseId)?.let { ExerciseChoice(it.id, it.name) }
            }
        }
        return WorkoutSlot(
            sessionSlotId = entity.id,
            routineSlotId = entity.slotId,
            sortOrder = prescription.sortOrder,
            prescription = prescription,
            exerciseName = exercise?.name.orEmpty(),
            setupNotes = exercise?.setupNotes.orEmpty(),
            unilateral = exercise?.isUnilateral == true,
            equipment = exercise?.equipment ?: com.forge.hypertrophy.domain.model.Equipment.BARBELL,
            barWeightKg = exercise?.barWeightKg,
            alternatives = alternatives,
            skipped = entity.skipped,
            skipReason = entity.skipReason,
            chosenAlternativeExerciseId = entity.chosenAlternativeExerciseId,
            formConfirmed = entity.formConfirmed == true,
            sets = sessions.sets(entity.id).map { it.toRecorded() },
        )
    }

    private suspend fun previousSets(sessionId: Long, slots: List<WorkoutSlot>): Map<Long, RecordedSet> {
        val wanted = slots.map { it.activeExerciseId }.toSet()
        val best = mutableMapOf<Long, Pair<Long, RecordedSet>>()
        sessions.allSlots().filter { it.sessionId != sessionId }.forEach { slot ->
            val exerciseId = slot.chosenAlternativeExerciseId ?: slot.prescriptionSnapshot.exerciseId
            if (exerciseId !in wanted) return@forEach
            val last = sessions.sets(slot.id).lastOrNull { it.setType == SetType.WORKING || it.setType == SetType.AMRAP } ?: return@forEach
            val current = best[exerciseId]
            if (current == null || slot.sessionId > current.first) best[exerciseId] = slot.sessionId to last.toRecorded()
        }
        return best.mapValues { it.value.second }
    }

    private suspend fun ownHistory(sessionId: Long, slots: List<WorkoutSlot>): Map<Long, RecordedSet> {
        val byRoutine = slots.filter { it.routineSlotId != null }.groupBy { it.routineSlotId }
        val best = mutableMapOf<Long, Pair<Long, RecordedSet>>()
        sessions.allSlots().filter { it.sessionId != sessionId && it.slotId != null }.forEach { stored ->
            val routineId = stored.slotId ?: return@forEach
            if (routineId !in byRoutine) return@forEach
            val last = sessions.sets(stored.id).lastOrNull { it.setType == SetType.WORKING || it.setType == SetType.AMRAP }
                ?: return@forEach
            val current = best[routineId]
            if (current == null || stored.sessionId > current.first) best[routineId] = stored.sessionId to last.toRecorded()
        }
        val result = mutableMapOf<Long, RecordedSet>()
        for ((routineId, pair) in best) {
            byRoutine[routineId].orEmpty().forEach { slot -> result[slot.sessionSlotId] = pair.second }
        }
        return result
    }

    private suspend fun baselineHints(slots: List<WorkoutSlot>): Map<Long, BaselineHint> {
        val rows = baselines.forSlots(slots.mapNotNull { it.routineSlotId }).associateBy { it.slotId }
        return slots.mapNotNull { slot ->
            val routineId = slot.routineSlotId ?: return@mapNotNull null
            val row = rows[routineId] ?: return@mapNotNull null
            slot.sessionSlotId to BaselineHint(row.weightKg, row.repsHint, row.weightKg == null)
        }.toMap()
    }

    private fun com.forge.hypertrophy.data.entity.SetEntryEntity.toRecorded() = RecordedSet(
        id = id,
        setNumber = setNumber,
        side = side,
        weightKg = weightKg,
        reps = reps,
        holdSec = holdSec,
        rpe = rpe,
        jointFlags = jointFlags,
        entryMethod = entryMethod,
    )
}
