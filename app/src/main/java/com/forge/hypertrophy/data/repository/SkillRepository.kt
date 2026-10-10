package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.ExerciseDao
import com.forge.hypertrophy.data.dao.RoutineDao
import com.forge.hypertrophy.data.dao.SessionDao
import com.forge.hypertrophy.data.dao.SkillDao
import com.forge.hypertrophy.data.entity.SkillEntity
import com.forge.hypertrophy.data.entity.SkillProgressEntity
import com.forge.hypertrophy.data.entity.SkillStageEventEntity
import com.forge.hypertrophy.data.entity.SkillStepEntity
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface SkillRepository {
    fun observeActive(): Flow<List<SkillEntity>>

    suspend fun get(id: Long): SkillEntity?

    suspend fun all(): List<SkillEntity>

    suspend fun insert(skill: SkillEntity): Long

    suspend fun update(skill: SkillEntity)

    suspend fun archive(id: Long)

    suspend fun delete(id: Long)

    fun observeSteps(skillId: Long): Flow<List<SkillStepEntity>>

    suspend fun getSteps(skillId: Long): List<SkillStepEntity>

    suspend fun insertStep(step: SkillStepEntity): Long

    suspend fun updateStep(step: SkillStepEntity)

    suspend fun deleteStep(id: Long)

    suspend fun getProgress(skillId: Long): SkillProgressEntity?

    suspend fun allSteps(): List<SkillStepEntity>

    suspend fun allProgress(): List<SkillProgressEntity>

    suspend fun upsertProgress(progress: SkillProgressEntity): Long

    suspend fun recordStageEvent(event: SkillStageEventEntity): Long

    /** Tier and stage changes dated inside [from]..[to], oldest first. */
    suspend fun stageEventsBetween(from: LocalDate, to: LocalDate): List<SkillStageEventEntity>

    /** Skill ids still pointed at by an exercise, progress, or a slot's target step. */
    suspend fun referencedIds(): Set<Long>
}

@Singleton
class RoomSkillRepository @Inject constructor(
    private val skillDao: SkillDao,
    private val exerciseDao: ExerciseDao,
    private val routineDao: RoutineDao,
    private val sessionDao: SessionDao,
    private val clock: Clock,
) : SkillRepository {
    override fun observeActive(): Flow<List<SkillEntity>> = skillDao.observeActive()

    override suspend fun get(id: Long): SkillEntity? = skillDao.get(id)

    override suspend fun all(): List<SkillEntity> = skillDao.all()

    override suspend fun insert(skill: SkillEntity): Long = skillDao.insert(skill)

    override suspend fun update(skill: SkillEntity) = skillDao.update(skill)

    override suspend fun archive(id: Long) {
        skillDao.archive(id, clock.instant())
    }

    override suspend fun delete(id: Long) = skillDao.delete(id)

    override fun observeSteps(skillId: Long): Flow<List<SkillStepEntity>> = skillDao.observeSteps(skillId)

    override suspend fun getSteps(skillId: Long): List<SkillStepEntity> = skillDao.getSteps(skillId)

    override suspend fun insertStep(step: SkillStepEntity): Long = skillDao.insertStep(step)

    override suspend fun updateStep(step: SkillStepEntity) = skillDao.updateStep(step)

    override suspend fun deleteStep(id: Long) = skillDao.deleteStep(id)

    override suspend fun getProgress(skillId: Long): SkillProgressEntity? = skillDao.getProgress(skillId)

    override suspend fun allSteps(): List<SkillStepEntity> = skillDao.allSteps()

    override suspend fun allProgress(): List<SkillProgressEntity> = skillDao.allProgress()

    override suspend fun upsertProgress(progress: SkillProgressEntity): Long = skillDao.upsertProgress(progress)

    override suspend fun recordStageEvent(event: SkillStageEventEntity): Long = skillDao.insertStageEvent(event)

    override suspend fun stageEventsBetween(from: LocalDate, to: LocalDate): List<SkillStageEventEntity> =
        skillDao.stageEventsBetween(from, to)

    override suspend fun referencedIds(): Set<Long> {
        val ids = exerciseDao.all().mapNotNullTo(mutableSetOf()) { it.skillId }
        val targeted = routineDao.targetedStepIds().toMutableSet()
        sessionDao.allSlots().forEach { slot ->
            slot.prescriptionSnapshot.targetSkillStepId?.let { targeted += it }
        }
        skillDao.all().forEach { skill ->
            if (skill.id in ids) return@forEach
            val steps = skillDao.getSteps(skill.id)
            if (skillDao.getProgress(skill.id) != null || steps.any { it.id in targeted }) {
                ids += skill.id
            }
        }
        return ids
    }
}
