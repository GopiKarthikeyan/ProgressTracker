package com.forge.hypertrophy.media

import com.forge.hypertrophy.data.entity.MediaItemEntity
import com.forge.hypertrophy.data.media.MediaFiles
import com.forge.hypertrophy.data.media.SetLabelResolver
import com.forge.hypertrophy.data.repository.MediaPreferencesRepository
import com.forge.hypertrophy.data.repository.MediaRepository
import com.forge.hypertrophy.domain.media.trimWindow
import com.forge.hypertrophy.domain.model.MediaType
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Turns a raw recording into a stored clip: trim with the configured lead
 * and tail, burn the set label in, then insert the row. The raw file is
 * removed afterwards.
 */
class ClipPipeline @Inject constructor(
    private val files: MediaFiles,
    private val media: MediaRepository,
    private val preferences: MediaPreferencesRepository,
    private val labels: SetLabelResolver,
    private val processor: ClipProcessor,
    private val durations: ClipDurations,
    private val clock: Clock,
) {
    suspend fun finish(
        raw: File,
        exerciseId: Long,
        setEntryId: Long?,
        onProgress: (Float) -> Unit,
    ): Long {
        val now = clock.instant()
        val label = setEntryId?.let { labels.forSet(it) } ?: labels.forExercise(exerciseId) ?: ""
        val duration = durations.durationMs(raw)
        val window = trimWindow(duration, preferences.leadTrimMs.first(), preferences.tailTrimMs.first())
        val output = files.newVideoFile(now)
        try {
            processor.process(raw, output, window, label, onProgress)
        } finally {
            raw.delete()
        }
        return media.insert(
            MediaItemEntity(
                type = MediaType.VIDEO,
                pose = null,
                exerciseId = exerciseId,
                setEntryId = setEntryId,
                uri = files.relative(output),
                trimStartMs = window.startMs,
                trimEndMs = window.endMs,
                capturedAt = now,
                label = label,
            ),
        )
    }
}
