package com.forge.hypertrophy.data.media

import android.content.Context
import android.net.Uri
import com.forge.hypertrophy.data.entity.MediaItemEntity
import com.forge.hypertrophy.data.repository.MediaRepository
import com.forge.hypertrophy.domain.model.MediaType
import com.forge.hypertrophy.domain.model.Pose
import com.forge.hypertrophy.media.ClipPipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class MediaImportBatchResult(val ok: Int, val failed: Int)

interface MediaImporter {
    suspend fun importPhoto(uri: Uri, pose: Pose): Boolean

    suspend fun importVideo(
        uri: Uri,
        exerciseId: Long,
        setEntryId: Long?,
        onProgress: (Float) -> Unit = {},
    ): Boolean

    suspend fun importPhotos(uris: List<Uri>, pose: Pose): MediaImportBatchResult {
        var ok = 0
        var failed = 0
        for (uri in uris) {
            if (importPhoto(uri, pose)) ok++ else failed++
        }
        return MediaImportBatchResult(ok, failed)
    }

    suspend fun importVideos(
        uris: List<Uri>,
        exerciseId: Long,
        setEntryId: Long?,
    ): MediaImportBatchResult {
        var ok = 0
        var failed = 0
        for (uri in uris) {
            if (importVideo(uri, exerciseId, setEntryId)) ok++ else failed++
        }
        return MediaImportBatchResult(ok, failed)
    }
}

/** Copies a Photo Picker / SAF URI into app media storage and inserts a row. */
@Singleton
class AppMediaImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: MediaFiles,
    private val media: MediaRepository,
    private val pipeline: ClipPipeline,
    private val clock: Clock,
) : MediaImporter {
    override suspend fun importPhoto(uri: Uri, pose: Pose): Boolean = withContext(Dispatchers.IO) {
        val now = clock.instant()
        val target = files.newPhotoFile(pose, now)
        if (!copyUri(uri, target)) {
            target.delete()
            return@withContext false
        }
        replaceLatestPhoto(pose)
        media.insert(
            MediaItemEntity(
                type = MediaType.PHOTO,
                pose = pose,
                exerciseId = null,
                setEntryId = null,
                uri = files.relative(target),
                trimStartMs = null,
                trimEndMs = null,
                capturedAt = now,
                label = null,
            ),
        )
        true
    }

    override suspend fun importVideo(
        uri: Uri,
        exerciseId: Long,
        setEntryId: Long?,
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val raw = files.newRawVideoFile(clock.instant())
        if (!copyUri(uri, raw)) {
            raw.delete()
            return@withContext false
        }
        runCatching {
            pipeline.finish(raw, exerciseId, setEntryId, onProgress)
        }.isSuccess
    }

    private suspend fun replaceLatestPhoto(pose: Pose) {
        val previous = media.observePhotos().first().lastOrNull { it.pose == pose } ?: return
        media.delete(previous.id)
    }

    private fun copyUri(uri: Uri, target: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null && target.isFile && target.length() > 0L
        } catch (_: Exception) {
            false
        }
    }
}
