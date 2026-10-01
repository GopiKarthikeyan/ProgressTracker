package com.forge.hypertrophy.data.repository

import com.forge.hypertrophy.data.dao.DaoFixture
import com.forge.hypertrophy.data.dao.DaoTest
import com.forge.hypertrophy.data.entity.MediaItemEntity
import com.forge.hypertrophy.data.media.MediaFiles
import com.forge.hypertrophy.domain.model.MediaType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class MediaRepositoryTest : DaoTest() {
    @get:Rule
    val folder = TemporaryFolder()

    private fun repository(): Pair<RoomMediaRepository, MediaFiles> {
        val files = MediaFiles(folder.root)
        return RoomMediaRepository(db.mediaDao(), files) to files
    }

    private fun MediaFiles.clip(name: String): File {
        directory.mkdirs()
        return File(directory, name).apply { writeText(name) }
    }

    private suspend fun insertVideo(exerciseId: Long, uri: String): Long = db.mediaDao().insert(
        MediaItemEntity(
            type = MediaType.VIDEO,
            pose = null,
            exerciseId = exerciseId,
            setEntryId = null,
            uri = uri,
            trimStartMs = null,
            trimEndMs = null,
        ),
    )

    @org.junit.Test
    fun deletingTheRowDeletesTheFile() = runBlocking {
        val (repository, files) = repository()
        val exerciseId = DaoFixture(db).exercise("press")
        val clip = files.clip("clip-1.mp4")
        val id = insertVideo(exerciseId, files.relative(clip))

        repository.delete(id)

        assertNull(repository.get(id))
        assertFalse(clip.exists())
    }

    @org.junit.Test
    fun reconcileDropsRowsWithoutFilesAndFilesWithoutRows() = runBlocking {
        val (repository, files) = repository()
        val exerciseId = DaoFixture(db).exercise("press")
        val kept = files.clip("clip-kept.mp4")
        files.clip("orphan.mp4")
        val keptId = insertVideo(exerciseId, files.relative(kept))
        val missingId = insertVideo(exerciseId, "media/clip-missing.mp4")

        val removed = repository.reconcile()

        assertEquals(1, removed)
        assertNull(repository.get(missingId))
        assertEquals(kept, repository.file(repository.get(keptId)!!))
        assertEquals(listOf("clip-kept.mp4"), files.list().map { it.name })
    }

    @org.junit.Test
    fun reconcileRelinksRowsByFileName() = runBlocking {
        val (repository, files) = repository()
        val exerciseId = DaoFixture(db).exercise("press")
        val moved = files.clip("clip-moved.mp4")
        val id = insertVideo(exerciseId, "/data/user/0/old-install/files/media/clip-moved.mp4")

        repository.reconcile()

        val row = repository.get(id)!!
        assertEquals("media/clip-moved.mp4", row.uri)
        assertTrue(repository.file(row).isFile)
        assertEquals(moved, repository.file(row))
    }
}
