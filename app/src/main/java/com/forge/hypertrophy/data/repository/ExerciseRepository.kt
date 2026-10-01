package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.ExerciseDao
import com.forge.hypertrophy.data.dao.MediaDao
import com.forge.hypertrophy.data.dao.RoutineDao
import com.forge.hypertrophy.data.dao.SessionDao
import com.forge.hypertrophy.data.entity.ExerciseEntity
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun observeActive(): Flow<List<ExerciseEntity>>

    suspend fun get(id: Long): ExerciseEntity?

    suspend fun all(): List<ExerciseEntity>

    suspend fun insert(exercise: ExerciseEntity): Long

    suspend fun update(exercise: ExerciseEntity)

    suspend fun archive(id: Long)

    suspend fun delete(id: Long)

    /** Exercise ids still pointed at by a routine, an alternative, history, or media. */
    suspend fun referencedIds(): Set<Long>
}

@Singleton
class RoomExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val routineDao: RoutineDao,
    private val sessionDao: SessionDao,
    private val mediaDao: MediaDao,
    private val clock: Clock,
) : ExerciseRepository {
    override fun observeActive(): Flow<List<ExerciseEntity>> = exerciseDao.observeActive()

    override suspend fun get(id: Long): ExerciseEntity? = exerciseDao.get(id)

    override suspend fun all(): List<ExerciseEntity> = exerciseDao.all()

    override suspend fun insert(exercise: ExerciseEntity): Long = exerciseDao.insert(exercise)

    override suspend fun update(exercise: ExerciseEntity) = exerciseDao.update(exercise)

    override suspend fun archive(id: Long) {
        exerciseDao.archive(id, clock.instant())
    }

    override suspend fun delete(id: Long) = exerciseDao.delete(id)

    override suspend fun referencedIds(): Set<Long> {
        val ids = mutableSetOf<Long>()
        ids += routineDao.allSlots().map { it.exerciseId }
        ids += routineDao.allAlternatives().map { it.exerciseId }
        sessionDao.allSlots().forEach { slot ->
            ids += slot.prescriptionSnapshot.exerciseId
            slot.chosenAlternativeExerciseId?.let { ids += it }
        }
        ids += mediaDao.referencedExerciseIds()
        return ids
    }
}
