package com.forge.hypertrophy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.forge.hypertrophy.data.entity.SessionSlotEntity
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.model.SlotPrescription
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM workout_session WHERE id = :id")
    fun observe(id: Long): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE status = 'IN_PROGRESS' ORDER BY id")
    fun observeInProgress(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_session ORDER BY date DESC, id DESC")
    suspend fun history(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun get(id: Long): WorkoutSessionEntity?

    @Insert
    suspend fun insert(session: WorkoutSessionEntity): Long

    @Update
    suspend fun update(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM session_slot WHERE sessionId = :sessionId ORDER BY id")
    fun observeSlots(sessionId: Long): Flow<List<SessionSlotEntity>>

    @Query("SELECT * FROM session_slot")
    suspend fun allSlots(): List<SessionSlotEntity>

    @Insert
    suspend fun insertSlot(slot: SessionSlotEntity): Long

    @Update
    suspend fun updateSlot(slot: SessionSlotEntity)

    @Query("SELECT * FROM set_entry WHERE sessionSlotId = :sessionSlotId ORDER BY setNumber")
    fun observeSets(sessionSlotId: Long): Flow<List<SetEntryEntity>>

    @Insert
    suspend fun insertSet(entry: SetEntryEntity): Long

    @Update
    suspend fun updateSet(entry: SetEntryEntity)

    @Query("DELETE FROM set_entry WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT * FROM set_entry WHERE id = :id")
    suspend fun getSet(id: Long): SetEntryEntity?

    @Query("SELECT * FROM session_slot WHERE id = :id")
    suspend fun getSlot(id: Long): SessionSlotEntity?

    @Query(
        """
        SELECT set_entry.*
        FROM set_entry
        INNER JOIN session_slot ON set_entry.sessionSlotId = session_slot.id
        LEFT JOIN routine_slot ON session_slot.slotId = routine_slot.id
        WHERE COALESCE(session_slot.chosenAlternativeExerciseId, routine_slot.exerciseId) = :exerciseId
        ORDER BY set_entry.loggedAt DESC, set_entry.id DESC
        LIMIT :limit
        """,
    )
    suspend fun recentSetsForExercise(exerciseId: Long, limit: Int): List<SetEntryEntity>

    @Query("SELECT MIN(date) FROM workout_session WHERE status = 'COMPLETED'")
    suspend fun earliestCompletedDate(): LocalDate?

    @Query("SELECT date, kind FROM workout_session WHERE status = 'COMPLETED'")
    suspend fun completedDays(): List<CompletedSessionDay>

    @Query(
        """
        SELECT workout_session.date AS sessionDate,
               workout_session.isDeload AS isDeload,
               workout_session.id AS sessionId,
               workout_session.completedAt AS completedAt,
               session_slot.slotId AS slotId,
               session_slot.prescriptionSnapshot AS prescriptionSnapshot,
               session_slot.chosenAlternativeExerciseId AS chosenAlternativeExerciseId,
               set_entry.setType AS setType,
               set_entry.weightKg AS weightKg,
               set_entry.reps AS reps,
               set_entry.holdSec AS holdSec
        FROM set_entry
        INNER JOIN session_slot ON set_entry.sessionSlotId = session_slot.id
        INNER JOIN workout_session ON session_slot.sessionId = workout_session.id
        WHERE workout_session.status = 'COMPLETED'
        """,
    )
    suspend fun completedSets(): List<CompletedSetRow>
}

data class CompletedSessionDay(
    val date: LocalDate,
    val kind: SessionKind,
)

data class CompletedSetRow(
    val sessionDate: LocalDate,
    val isDeload: Boolean,
    val sessionId: Long,
    val completedAt: Instant?,
    val slotId: Long?,
    val prescriptionSnapshot: SlotPrescription,
    val chosenAlternativeExerciseId: Long?,
    val setType: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val holdSec: Int?,
)
