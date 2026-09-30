package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.SkillDao
import com.forge.hypertrophy.data.entity.SkillEntity
import com.forge.hypertrophy.data.entity.SkillProgressEntity
import com.forge.hypertrophy.data.entity.SkillStepEntity
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface SkillRepository {
    fun observeActive(): Flow<List<SkillEntity>>

    suspend fun get(id: Long): SkillEntity?

    suspend fun insert(skill: SkillEntity): Long

    suspend fun update(skill: SkillEntity)

    suspend fun archive(id: Long)

    suspend fun delete(id: Long)

    fun observeSteps(skillId: Long): Flow<List<SkillStepEntity>>

    suspend fun insertStep(step: SkillStepEntity): Long

    suspend fun updateStep(step: SkillStepEntity)

    suspend fun deleteStep(id: Long)

    suspend fun getProgress(skillId: Long): SkillProgressEntity?

    suspend fun upsertProgress(progress: SkillProgressEntity): Long
}

@Singleton
class RoomSkillRepository @Inject constructor(
    private val skillDao: SkillDao,
    private val clock: Clock,
) : SkillRepository {
    override fun observeActive(): Flow<List<SkillEntity>> = skillDao.observeActive()

    override suspend fun get(id: Long): SkillEntity? = skillDao.get(id)

    override suspend fun insert(skill: SkillEntity): Long = skillDao.insert(skill)

    override suspend fun update(skill: SkillEntity) = skillDao.update(skill)

    override suspend fun archive(id: Long) {
        skillDao.archive(id, clock.instant())
    }

    override suspend fun delete(id: Long) = skillDao.delete(id)

    override fun observeSteps(skillId: Long): Flow<List<SkillStepEntity>> = skillDao.observeSteps(skillId)

    override suspend fun insertStep(step: SkillStepEntity): Long = skillDao.insertStep(step)

    override suspend fun updateStep(step: SkillStepEntity) = skillDao.updateStep(step)

    override suspend fun deleteStep(id: Long) = skillDao.deleteStep(id)

    override suspend fun getProgress(skillId: Long): SkillProgressEntity? = skillDao.getProgress(skillId)

    override suspend fun upsertProgress(progress: SkillProgressEntity): Long = skillDao.upsertProgress(progress)
}
