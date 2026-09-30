package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.ProgramDao
import com.forge.hypertrophy.data.entity.ProgramEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface ProgramRepository {
    fun observe(): Flow<ProgramEntity?>

    suspend fun get(): ProgramEntity?

    suspend fun insert(program: ProgramEntity): Long

    suspend fun update(program: ProgramEntity)
}

@Singleton
class RoomProgramRepository @Inject constructor(
    private val programDao: ProgramDao,
) : ProgramRepository {
    override fun observe(): Flow<ProgramEntity?> = programDao.observe()

    override suspend fun get(): ProgramEntity? = programDao.get()

    override suspend fun insert(program: ProgramEntity): Long = programDao.insert(program)

    override suspend fun update(program: ProgramEntity) = programDao.update(program)
}
