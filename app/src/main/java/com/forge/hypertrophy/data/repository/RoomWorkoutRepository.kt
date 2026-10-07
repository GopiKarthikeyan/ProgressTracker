package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.repository.ExerciseDetails
import com.forge.hypertrophy.domain.repository.WorkoutRepository
import com.forge.hypertrophy.domain.repository.WorkoutSessionState
import com.forge.hypertrophy.domain.workout.RecordedSet
import com.forge.hypertrophy.domain.workout.WorkoutSlot
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class RoomWorkoutRepository @Inject constructor(
    private val sessions: SessionRepository,
    private val exercises: ExerciseRepository,
) : WorkoutRepository {
    override suspend fun findSession(id: Long): WorkoutSessionState? {
        val session = sessions.get(id) ?: return null
        return WorkoutSessionState(status = session.status, isShortOnTime = session.isShortOnTime)
    }

    override suspend fun beginSession(
        id: Long,
        sleep: Int?,
        soreness: Int?,
        energy: Int?,
        startedAt: Instant,
    ): Boolean {
        val session = sessions.get(id) ?: return false
        sessions.update(
            session.copy(
                status = SessionStatus.IN_PROGRESS,
                readinessSleep = sleep,
                readinessSoreness = soreness,
                readinessEnergy = energy,
                startedAt = startedAt,
            ),
        )
        return true
    }

    override suspend fun updateRecordedSet(sessionSlotId: Long, set: RecordedSet, loggedAt: Instant) {
        sessions.updateSet(
            SetEntryEntity(
                id = set.id,
                sessionSlotId = sessionSlotId,
                setNumber = set.setNumber,
                side = set.side,
                setType = SetType.WORKING,
                weightKg = set.weightKg,
                reps = set.reps,
                holdSec = set.holdSec,
                rpe = set.rpe,
                jointFlags = set.jointFlags,
                entryMethod = set.entryMethod,
                loggedAt = loggedAt,
            ),
        )
    }

    override suspend fun deleteSet(id: Long) {
        sessions.deleteSet(id)
    }

    override suspend fun persistSlot(sessionId: Long, slot: WorkoutSlot) {
        val entity = sessions.observeSlots(sessionId).first().firstOrNull { it.id == slot.sessionSlotId } ?: return
        sessions.updateSlot(
            entity.copy(
                prescriptionSnapshot = entity.prescriptionSnapshot.copy(
                    sortOrder = slot.sortOrder,
                    setsMin = slot.prescription.setsMin,
                    setsMax = slot.prescription.setsMax,
                ),
                chosenAlternativeExerciseId = slot.chosenAlternativeExerciseId,
                skipped = slot.skipped,
                skipReason = slot.skipReason,
                formConfirmed = slot.formConfirmed,
            ),
        )
    }

    override suspend fun findExercise(id: Long): ExerciseDetails? {
        val exercise = exercises.get(id) ?: return null
        return ExerciseDetails(
            name = exercise.name,
            setupNotes = exercise.setupNotes,
            isUnilateral = exercise.isUnilateral,
        )
    }

    override suspend fun updateSetupNotes(exerciseId: Long, notes: String) {
        val exercise = exercises.get(exerciseId) ?: return
        exercises.update(exercise.copy(setupNotes = notes))
    }
}
