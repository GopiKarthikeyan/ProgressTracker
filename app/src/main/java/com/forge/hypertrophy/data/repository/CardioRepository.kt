package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.CardioDao
import com.forge.hypertrophy.data.dao.GearMileage
import com.forge.hypertrophy.data.entity.CardioLogEntity
import com.forge.hypertrophy.data.entity.TrackPointEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface CardioRepository {
    fun observeLog(sessionId: Long): Flow<CardioLogEntity?>

    suspend fun insert(log: CardioLogEntity): Long

    suspend fun update(log: CardioLogEntity)

    suspend fun delete(id: Long)

    fun observeTrackPoints(cardioLogId: Long): Flow<List<TrackPointEntity>>

    suspend fun insertTrackPoints(points: List<TrackPointEntity>): List<Long>

    fun observeAll(): Flow<List<CardioLogEntity>>

    fun observeMileage(): Flow<List<GearMileage>>
}

@Singleton
class RoomCardioRepository @Inject constructor(
    private val cardioDao: CardioDao,
) : CardioRepository {
    override fun observeLog(sessionId: Long): Flow<CardioLogEntity?> = cardioDao.observeLog(sessionId)

    override suspend fun insert(log: CardioLogEntity): Long = cardioDao.insert(log)

    override suspend fun update(log: CardioLogEntity) = cardioDao.update(log)

    override suspend fun delete(id: Long) = cardioDao.delete(id)

    override fun observeTrackPoints(cardioLogId: Long): Flow<List<TrackPointEntity>> =
        cardioDao.observeTrackPoints(cardioLogId)

    override suspend fun insertTrackPoints(points: List<TrackPointEntity>): List<Long> =
        cardioDao.insertTrackPoints(points)

    override fun observeAll(): Flow<List<CardioLogEntity>> = cardioDao.observeAll()

    override fun observeMileage(): Flow<List<GearMileage>> = cardioDao.observeMileage()
}
