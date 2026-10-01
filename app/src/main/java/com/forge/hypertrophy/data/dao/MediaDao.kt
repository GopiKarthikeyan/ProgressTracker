package com.forge.hypertrophy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.forge.hypertrophy.data.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    /** Newest clip first. */
    @Query("SELECT * FROM media_item WHERE exerciseId = :exerciseId ORDER BY id DESC")
    fun observeForExercise(exerciseId: Long): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_item WHERE setEntryId = :setEntryId ORDER BY id")
    fun observeForSet(setEntryId: Long): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_item ORDER BY id DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_item WHERE type = 'PHOTO' AND pose IS NOT NULL ORDER BY capturedAt, id")
    fun observePhotos(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_item WHERE id = :id")
    suspend fun get(id: Long): MediaItemEntity?

    @Query("SELECT * FROM media_item ORDER BY id")
    suspend fun all(): List<MediaItemEntity>

    @Insert
    suspend fun insert(item: MediaItemEntity): Long

    @Update
    suspend fun update(item: MediaItemEntity)

    @Query("DELETE FROM media_item WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT exerciseId FROM media_item WHERE exerciseId IS NOT NULL")
    suspend fun referencedExerciseIds(): List<Long>
}
