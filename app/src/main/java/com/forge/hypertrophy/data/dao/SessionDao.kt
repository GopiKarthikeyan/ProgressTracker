package com.forge.hypertrophy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.forge.hypertrophy.data.entity.SessionSlotEntity
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM workout_session WHERE id = :id")
    fun observe(id: Long): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE status = 'IN_PROGRESS' ORDER BY id")
    fun observeInProgress(): Flow<List<WorkoutSessionEntity>>

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
}
