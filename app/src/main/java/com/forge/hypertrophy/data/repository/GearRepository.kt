package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.GearDao
import com.forge.hypertrophy.data.entity.GearEntity
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface GearRepository {
    fun observeActive(): Flow<List<GearEntity>>

    suspend fun get(id: Long): GearEntity?

    suspend fun insert(gear: GearEntity): Long

    suspend fun update(gear: GearEntity)

    suspend fun archive(id: Long)

    suspend fun delete(id: Long)
}

@Singleton
class RoomGearRepository @Inject constructor(
    private val gearDao: GearDao,
    private val clock: Clock,
) : GearRepository {
    override fun observeActive(): Flow<List<GearEntity>> = gearDao.observeActive()

    override suspend fun get(id: Long): GearEntity? = gearDao.get(id)

    override suspend fun insert(gear: GearEntity): Long = gearDao.insert(gear)

    override suspend fun update(gear: GearEntity) = gearDao.update(gear)

    override suspend fun archive(id: Long) {
        gearDao.archive(id, clock.instant())
    }

    override suspend fun delete(id: Long) = gearDao.delete(id)
}
