package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.BiometricsDao
import com.forge.hypertrophy.data.entity.BiometricsEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface BiometricsRepository {
    fun observeAll(): Flow<List<BiometricsEntity>>

    suspend fun get(date: LocalDate): BiometricsEntity?

    suspend fun upsert(biometrics: BiometricsEntity): Long
}

@Singleton
class RoomBiometricsRepository @Inject constructor(
    private val biometricsDao: BiometricsDao,
) : BiometricsRepository {
    override fun observeAll(): Flow<List<BiometricsEntity>> = biometricsDao.observeAll()

    override suspend fun get(date: LocalDate): BiometricsEntity? = biometricsDao.get(date)

    override suspend fun upsert(biometrics: BiometricsEntity): Long = biometricsDao.upsert(biometrics)
}
