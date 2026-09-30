package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.SessionDao
import com.forge.hypertrophy.data.entity.SessionSlotEntity
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

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
}
