package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.MediaDao
import com.forge.hypertrophy.data.entity.MediaItemEntity
import com.forge.hypertrophy.data.media.MediaFiles
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface MediaRepository {
    fun observeForExercise(exerciseId: Long): Flow<List<MediaItemEntity>>

    fun observeForSet(setEntryId: Long): Flow<List<MediaItemEntity>>

    fun observeAll(): Flow<List<MediaItemEntity>>

    fun observePhotos(): Flow<List<MediaItemEntity>>

    suspend fun get(id: Long): MediaItemEntity?

    suspend fun insert(item: MediaItemEntity): Long

    suspend fun update(item: MediaItemEntity)

    /** Removes the row and the file it points at. */
    suspend fun delete(id: Long)

    /** Resolves a stored uri to a file in app storage. */
    fun file(item: MediaItemEntity): File

    /**
     * Brings rows and files back in step: a row whose file is gone is removed,
     * a row whose file moved is re-linked by name, and a file without a row is
     * deleted. Returns the number of rows removed.
     */
    suspend fun reconcile(): Int
}

@Singleton
class RoomMediaRepository @Inject constructor(
    private val mediaDao: MediaDao,
    private val files: MediaFiles,
) : MediaRepository {
    override fun observeForExercise(exerciseId: Long): Flow<List<MediaItemEntity>> =
        mediaDao.observeForExercise(exerciseId)

    override fun observeForSet(setEntryId: Long): Flow<List<MediaItemEntity>> =
        mediaDao.observeForSet(setEntryId)

    override fun observeAll(): Flow<List<MediaItemEntity>> = mediaDao.observeAll()

    override fun observePhotos(): Flow<List<MediaItemEntity>> = mediaDao.observePhotos()

    override suspend fun get(id: Long): MediaItemEntity? = mediaDao.get(id)

    override suspend fun insert(item: MediaItemEntity): Long = mediaDao.insert(item)

    override suspend fun update(item: MediaItemEntity) = mediaDao.update(item)

    override suspend fun delete(id: Long) {
        val item = mediaDao.get(id) ?: return
        mediaDao.delete(id)
        withContext(Dispatchers.IO) { files.delete(item.uri) }
    }

    override fun file(item: MediaItemEntity): File = files.resolve(item.uri)

    override suspend fun reconcile(): Int = withContext(Dispatchers.IO) {
        val rows = mediaDao.all()
        val present = files.list().associateBy { it.name }
        val kept = mutableSetOf<String>()
        var removed = 0
        rows.forEach { row ->
            val resolved = files.resolve(row.uri)
            when {
                resolved.isFile && resolved.parentFile?.canonicalFile == files.directory.canonicalFile -> {
                    kept += resolved.name
                    val relative = files.relative(resolved)
                    if (row.uri != relative) mediaDao.update(row.copy(uri = relative))
                }
                present.containsKey(File(row.uri).name) -> {
                    val moved = present.getValue(File(row.uri).name)
                    kept += moved.name
                    mediaDao.update(row.copy(uri = files.relative(moved)))
                }
                else -> {
                    mediaDao.delete(row.id)
                    removed += 1
                }
            }
        }
        present.values.filter { it.name !in kept }.forEach { it.delete() }
        removed
    }
}
