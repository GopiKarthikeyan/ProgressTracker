package com.forge.hypertrophy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.forge.hypertrophy.data.entity.SkillEntity
import com.forge.hypertrophy.data.entity.SkillProgressEntity
import com.forge.hypertrophy.data.entity.SkillStageEventEntity
import com.forge.hypertrophy.data.entity.SkillStepEntity
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillDao {
    @Query("SELECT * FROM skill WHERE archivedAt IS NULL ORDER BY name")
    fun observeActive(): Flow<List<SkillEntity>>

    @Query("SELECT * FROM skill WHERE id = :id")
    suspend fun get(id: Long): SkillEntity?

    @Query("SELECT * FROM skill ORDER BY id")
    suspend fun all(): List<SkillEntity>

    @Insert
    suspend fun insert(skill: SkillEntity): Long

    @Update
    suspend fun update(skill: SkillEntity)

    @Query("UPDATE skill SET archivedAt = :archivedAt WHERE id = :id")
    suspend fun archive(id: Long, archivedAt: Instant)

    @Query("DELETE FROM skill WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM skill_step WHERE skillId = :skillId ORDER BY sortOrder")
    fun observeSteps(skillId: Long): Flow<List<SkillStepEntity>>

    @Query("SELECT * FROM skill_step WHERE skillId = :skillId ORDER BY sortOrder")
    suspend fun getSteps(skillId: Long): List<SkillStepEntity>

    @Query("SELECT * FROM skill_step WHERE id IN (:ids)")
    suspend fun stepsByIds(ids: List<Long>): List<SkillStepEntity>

    @Insert
    suspend fun insertStep(step: SkillStepEntity): Long

    @Update
    suspend fun updateStep(step: SkillStepEntity)

    @Query("DELETE FROM skill_step WHERE id = :id")
    suspend fun deleteStep(id: Long)

    @Query("SELECT * FROM skill_step ORDER BY skillId, sortOrder")
    suspend fun allSteps(): List<SkillStepEntity>

    @Query("SELECT * FROM skill_progress")
    suspend fun allProgress(): List<SkillProgressEntity>

    @Query("SELECT * FROM skill_progress WHERE skillId = :skillId")
    suspend fun getProgress(skillId: Long): SkillProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: SkillProgressEntity): Long

    @Insert
    suspend fun insertStageEvent(event: SkillStageEventEntity): Long

    @Query("SELECT * FROM skill_stage_event WHERE date BETWEEN :from AND :to ORDER BY date, id")
    suspend fun stageEventsBetween(from: LocalDate, to: LocalDate): List<SkillStageEventEntity>
}
