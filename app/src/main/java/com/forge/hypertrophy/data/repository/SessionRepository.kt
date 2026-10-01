package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.CompletedSessionDay
import com.forge.hypertrophy.data.dao.CompletedSetRow
import com.forge.hypertrophy.data.dao.SessionDao
import com.forge.hypertrophy.data.entity.SessionSlotEntity
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface SessionRepository {
    fun observe(id: Long): Flow<WorkoutSessionEntity?>

    fun observeInProgress(): Flow<List<WorkoutSessionEntity>>

    suspend fun get(id: Long): WorkoutSessionEntity?

    suspend fun insert(session: WorkoutSessionEntity): Long

    suspend fun update(session: WorkoutSessionEntity)

    suspend fun delete(id: Long)

    fun observeSlots(sessionId: Long): Flow<List<SessionSlotEntity>>

    suspend fun insertSlot(slot: SessionSlotEntity): Long

    suspend fun updateSlot(slot: SessionSlotEntity)

    fun observeSets(sessionSlotId: Long): Flow<List<SetEntryEntity>>

    suspend fun insertSet(entry: SetEntryEntity): Long

    suspend fun updateSet(entry: SetEntryEntity)

    suspend fun deleteSet(id: Long)

    suspend fun allSlots(): List<SessionSlotEntity>

    suspend fun sets(sessionSlotId: Long): List<SetEntryEntity>

    suspend fun completedDays(): List<CompletedSessionDay>

    suspend fun completedSets(): List<CompletedSetRow>

    suspend fun getSet(id: Long): SetEntryEntity?

    suspend fun getSlot(id: Long): SessionSlotEntity?

    /** Latest sets logged for an exercise, newest first. */
    suspend fun recentSetsForExercise(exerciseId: Long, limit: Int): List<SetEntryEntity>

    suspend fun earliestCompletedDate(): LocalDate?

    suspend fun history(): List<WorkoutSessionEntity>
}

@Singleton
class RoomSessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
) : SessionRepository {
    override fun observe(id: Long): Flow<WorkoutSessionEntity?> = sessionDao.observe(id)

    override fun observeInProgress(): Flow<List<WorkoutSessionEntity>> = sessionDao.observeInProgress()

    override suspend fun get(id: Long): WorkoutSessionEntity? = sessionDao.get(id)

    override suspend fun insert(session: WorkoutSessionEntity): Long = sessionDao.insert(session)

    override suspend fun update(session: WorkoutSessionEntity) = sessionDao.update(session)

    override suspend fun delete(id: Long) = sessionDao.delete(id)

    override fun observeSlots(sessionId: Long): Flow<List<SessionSlotEntity>> = sessionDao.observeSlots(sessionId)

    override suspend fun insertSlot(slot: SessionSlotEntity): Long = sessionDao.insertSlot(slot)

    override suspend fun updateSlot(slot: SessionSlotEntity) = sessionDao.updateSlot(slot)

    override fun observeSets(sessionSlotId: Long): Flow<List<SetEntryEntity>> = sessionDao.observeSets(sessionSlotId)

    override suspend fun insertSet(entry: SetEntryEntity): Long = sessionDao.insertSet(entry)

    override suspend fun updateSet(entry: SetEntryEntity) = sessionDao.updateSet(entry)

    override suspend fun deleteSet(id: Long) = sessionDao.deleteSet(id)

    override suspend fun allSlots(): List<SessionSlotEntity> = sessionDao.allSlots()

    override suspend fun sets(sessionSlotId: Long): List<SetEntryEntity> = observeSets(sessionSlotId).first()

    override suspend fun completedDays(): List<CompletedSessionDay> = sessionDao.completedDays()

    override suspend fun completedSets(): List<CompletedSetRow> = sessionDao.completedSets()

    override suspend fun getSet(id: Long): SetEntryEntity? = sessionDao.getSet(id)

    override suspend fun getSlot(id: Long): SessionSlotEntity? = sessionDao.getSlot(id)

    override suspend fun recentSetsForExercise(exerciseId: Long, limit: Int): List<SetEntryEntity> =
        sessionDao.recentSetsForExercise(exerciseId, limit)

    override suspend fun earliestCompletedDate(): LocalDate? = sessionDao.earliestCompletedDate()

    override suspend fun history(): List<WorkoutSessionEntity> = sessionDao.history()
}
