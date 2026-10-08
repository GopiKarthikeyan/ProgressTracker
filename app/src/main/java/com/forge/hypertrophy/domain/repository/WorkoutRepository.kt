package com.forge.hypertrophy.domain.repository

import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.workout.RecordedSet
import com.forge.hypertrophy.domain.workout.WorkoutSlot
import java.time.Instant

data class WorkoutSessionState(
    val status: SessionStatus,
    val isShortOnTime: Boolean,
)

data class ExerciseDetails(
    val name: String,
    val setupNotes: String,
    val isUnilateral: Boolean,
    val equipment: Equipment,
    val barWeightKg: Double?,
)

interface WorkoutRepository {
    suspend fun findSession(id: Long): WorkoutSessionState?

    /** Returns false when the session does not exist. */
    suspend fun beginSession(
        id: Long,
        sleep: Int?,
        soreness: Int?,
        energy: Int?,
        startedAt: Instant,
    ): Boolean

    suspend fun updateRecordedSet(sessionSlotId: Long, set: RecordedSet, loggedAt: Instant)

    suspend fun deleteSet(id: Long)

    suspend fun persistSlot(sessionId: Long, slot: WorkoutSlot)

    suspend fun findExercise(id: Long): ExerciseDetails?

    suspend fun updateSetupNotes(exerciseId: Long, notes: String)
}
