package com.forge.hypertrophy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.forge.hypertrophy.data.entity.ProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Query("SELECT * FROM program LIMIT 1")
    fun observe(): Flow<ProgramEntity?>

    @Query("SELECT * FROM program LIMIT 1")
    suspend fun get(): ProgramEntity?

    @Query("SELECT * FROM program ORDER BY id")
    fun observeAll(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM program ORDER BY id")
    suspend fun all(): List<ProgramEntity>

    @Query("SELECT * FROM program WHERE isActive = 1 ORDER BY id LIMIT 1")
    fun observeActive(): Flow<ProgramEntity?>

    @Query("SELECT * FROM program WHERE id = :id")
    suspend fun getById(id: Long): ProgramEntity?

    @Insert
    suspend fun insert(program: ProgramEntity): Long

    @Update
    suspend fun update(program: ProgramEntity)

    @Query("UPDATE program SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE program SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: Long)

    @Transaction
    suspend fun setActive(id: Long) {
        if (getById(id) == null) return
        clearActive()
        markActive(id)
    }

    /**
     * Deletes a program and, through the routine-day foreign key, its days.
     *
     * When the deleted program is active, the remaining program with the lowest
     * id greater than the deleted id becomes active. When no higher id remains,
     * the lowest remaining id becomes active. Deleting the only program leaves
     * [observeActive] empty.
     */
    @Transaction
    suspend fun delete(id: Long) {
        val existing = getById(id) ?: return
        val successorId = if (existing.isActive) nextIdAfter(id) ?: lowestOtherId(id) else null
        deleteById(id)
        if (successorId != null) {
            clearActive()
            markActive(successorId)
        }
    }

    @Query("SELECT id FROM program WHERE id > :id ORDER BY id LIMIT 1")
    suspend fun nextIdAfter(id: Long): Long?

    @Query("SELECT id FROM program WHERE id != :id ORDER BY id LIMIT 1")
    suspend fun lowestOtherId(id: Long): Long?

    @Query("DELETE FROM program WHERE id = :id")
    suspend fun deleteById(id: Long)
}
