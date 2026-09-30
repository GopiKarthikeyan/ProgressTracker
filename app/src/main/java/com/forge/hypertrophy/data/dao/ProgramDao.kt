package com.forge.hypertrophy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.forge.hypertrophy.data.entity.ProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Query("SELECT * FROM program LIMIT 1")
    fun observe(): Flow<ProgramEntity?>

    @Query("SELECT * FROM program LIMIT 1")
    suspend fun get(): ProgramEntity?

    @Insert
    suspend fun insert(program: ProgramEntity): Long

    @Update
    suspend fun update(program: ProgramEntity)
}
