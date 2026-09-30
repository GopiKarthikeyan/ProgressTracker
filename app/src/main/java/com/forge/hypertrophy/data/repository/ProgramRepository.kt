package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.ProgramDao
import com.forge.hypertrophy.data.entity.ProgramEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface ProgramRepository {
    fun observe(): Flow<ProgramEntity?>

    fun observeAll(): Flow<List<ProgramEntity>>

    fun observeActive(): Flow<ProgramEntity?>

    suspend fun get(): ProgramEntity?

    suspend fun insert(program: ProgramEntity): Long

    suspend fun update(program: ProgramEntity)

    suspend fun setActive(id: Long)

    /**
     * Deletes a program.
     *
     * Deleting the active program promotes the remaining program with the lowest
     * id greater than the deleted id. When every remaining id is lower, the
     * lowest remaining id is promoted. Deleting the only program leaves
     * [observeActive] empty, which is the Today screen's empty state.
     *
     * [ProgramDao.delete] enforces the rule.
     */
    suspend fun delete(id: Long)
}

@Singleton
class RoomProgramRepository @Inject constructor(
    private val programDao: ProgramDao,
) : ProgramRepository {
    override fun observe(): Flow<ProgramEntity?> = programDao.observe()

    override fun observeAll(): Flow<List<ProgramEntity>> = programDao.observeAll()

    override fun observeActive(): Flow<ProgramEntity?> = programDao.observeActive()

    override suspend fun get(): ProgramEntity? = programDao.get()

    override suspend fun insert(program: ProgramEntity): Long = programDao.insert(program)

    override suspend fun update(program: ProgramEntity) = programDao.update(program)

    override suspend fun setActive(id: Long) = programDao.setActive(id)

    override suspend fun delete(id: Long) = programDao.delete(id)
}
