package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.ExerciseDao
import com.forge.hypertrophy.data.entity.ExerciseEntity
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun observeActive(): Flow<List<ExerciseEntity>>

    suspend fun get(id: Long): ExerciseEntity?

    suspend fun insert(exercise: ExerciseEntity): Long

    suspend fun update(exercise: ExerciseEntity)

    suspend fun archive(id: Long)

    suspend fun delete(id: Long)
}

@Singleton
class RoomExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val clock: Clock,
) : ExerciseRepository {
    override fun observeActive(): Flow<List<ExerciseEntity>> = exerciseDao.observeActive()

    override suspend fun get(id: Long): ExerciseEntity? = exerciseDao.get(id)

    override suspend fun insert(exercise: ExerciseEntity): Long = exerciseDao.insert(exercise)

    override suspend fun update(exercise: ExerciseEntity) = exerciseDao.update(exercise)

    override suspend fun archive(id: Long) {
        exerciseDao.archive(id, clock.instant())
    }

    override suspend fun delete(id: Long) = exerciseDao.delete(id)
}
