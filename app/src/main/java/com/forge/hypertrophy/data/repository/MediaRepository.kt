package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.MediaDao
import com.forge.hypertrophy.data.entity.MediaItemEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeForExercise(exerciseId: Long): Flow<List<MediaItemEntity>>

    fun observeForSet(setEntryId: Long): Flow<List<MediaItemEntity>>

    suspend fun insert(item: MediaItemEntity): Long

    suspend fun update(item: MediaItemEntity)

    suspend fun delete(id: Long)
}

@Singleton
class RoomMediaRepository @Inject constructor(
    private val mediaDao: MediaDao,
) : MediaRepository {
    override fun observeForExercise(exerciseId: Long): Flow<List<MediaItemEntity>> =
        mediaDao.observeForExercise(exerciseId)

    override fun observeForSet(setEntryId: Long): Flow<List<MediaItemEntity>> =
        mediaDao.observeForSet(setEntryId)

    override suspend fun insert(item: MediaItemEntity): Long = mediaDao.insert(item)

    override suspend fun update(item: MediaItemEntity) = mediaDao.update(item)

    override suspend fun delete(id: Long) = mediaDao.delete(id)
}
