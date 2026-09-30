package com.forge.hypertrophy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forge.hypertrophy.data.entity.BiometricsEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface BiometricsDao {
    @Query("SELECT * FROM biometrics ORDER BY date DESC")
    fun observeAll(): Flow<List<BiometricsEntity>>

    @Query("SELECT * FROM biometrics WHERE date = :date")
    suspend fun get(date: LocalDate): BiometricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(biometrics: BiometricsEntity): Long
}
