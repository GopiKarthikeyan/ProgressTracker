package com.forge.hypertrophy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.forge.hypertrophy.data.entity.GearEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface GearDao {
    @Query("SELECT * FROM gear WHERE archivedAt IS NULL ORDER BY name")
    fun observeActive(): Flow<List<GearEntity>>

    @Query("SELECT * FROM gear WHERE id = :id")
    suspend fun get(id: Long): GearEntity?

    @Insert
    suspend fun insert(gear: GearEntity): Long

    @Update
    suspend fun update(gear: GearEntity)

    @Query("UPDATE gear SET archivedAt = :archivedAt WHERE id = :id")
    suspend fun archive(id: Long, archivedAt: Instant)

    @Query("DELETE FROM gear WHERE id = :id")
    suspend fun delete(id: Long)
}
