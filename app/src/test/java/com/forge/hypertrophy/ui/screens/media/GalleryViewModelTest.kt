package com.forge.hypertrophy.ui.screens.media

import com.forge.hypertrophy.data.dao.DaoFixture
import com.forge.hypertrophy.data.entity.MediaItemEntity
import com.forge.hypertrophy.data.media.MediaFiles
import com.forge.hypertrophy.data.repository.RoomExerciseRepository
import com.forge.hypertrophy.data.repository.RoomMediaRepository
import com.forge.hypertrophy.data.repository.RoomSessionRepository
import com.forge.hypertrophy.domain.model.MediaType
import com.forge.hypertrophy.domain.model.Pose
import com.forge.hypertrophy.ui.screens.routine.ViewModelDaoTest
import com.forge.hypertrophy.ui.screens.routine.awaitUntil
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GalleryViewModelTest : ViewModelDaoTest() {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC)

    private fun files(): MediaFiles = MediaFiles(folder.root).also { it.directory.mkdirs() }

    private fun gallery(files: MediaFiles): GalleryViewModel = track(
        GalleryViewModel(
            media = RoomMediaRepository(db.mediaDao(), files),
            exercises = RoomExerciseRepository(db.exerciseDao(), db.routineDao(), db.sessionDao(), db.mediaDao(), clock),
            sessions = RoomSessionRepository(db.sessionDao()),
            clock = clock,
        ),
    )

    private suspend fun clip(files: MediaFiles, exerciseId: Long, name: String, label: String, at: Instant): Long {
        val file = File(files.directory, name).apply { writeText(name) }
        return db.mediaDao().insert(
            MediaItemEntity(
                type = MediaType.VIDEO,
                pose = null,
                exerciseId = exerciseId,
                setEntryId = null,
                uri = files.relative(file),
                trimStartMs = 0,
                trimEndMs = 5_000,
                capturedAt = at,
                label = label,
            ),
        )
    }

    private suspend fun photo(files: MediaFiles, pose: Pose, on: LocalDate): Long {
        val file = File(files.directory, "photo-${pose.name}-${on.toEpochDay()}.jpg").apply { writeText(on.toString()) }
        return db.mediaDao().insert(
            MediaItemEntity(
                type = MediaType.PHOTO,
                pose = pose,
                exerciseId = null,
                setEntryId = null,
                uri = files.relative(file),
                trimStartMs = null,
                trimEndMs = null,
                capturedAt = on.atStartOfDay(ZoneOffset.UTC).toInstant(),
                label = null,
            ),
        )
    }

    @Test
    fun clipsAreFilteredByExerciseAndCompareHoldsTwo() = runBlocking {
        val files = files()
        val fixture = DaoFixture(db)
        val deadlift = fixture.exercise("deadlift")
        val press = fixture.exercise("press")
        val first = clip(files, deadlift, "clip-1.mp4", "deadlift 140kg × 5", Instant.parse("2026-09-01T10:00:00Z"))
        val second = clip(files, deadlift, "clip-2.mp4", "deadlift 150kg × 3", Instant.parse("2026-09-08T10:00:00Z"))
        val third = clip(files, deadlift, "clip-3.mp4", "deadlift 155kg × 2", Instant.parse("2026-09-15T10:00:00Z"))
        clip(files, press, "clip-4.mp4", "press 60kg × 8", Instant.parse("2026-09-02T10:00:00Z"))

        val viewModel = gallery(files)
        awaitUntil { viewModel.uiState.value.exercises.size == 2 }
        assertEquals(emptyList<GalleryClip>(), viewModel.uiState.value.clips)

        viewModel.onEvent(GalleryEvent.SelectExercise(deadlift))
        awaitUntil { viewModel.uiState.value.clips.size == 3 }
        val clips = viewModel.uiState.value.clips
        assertEquals(listOf(third, second, first), clips.map { it.id })
        assertEquals("deadlift 140kg × 5", clips.last().label)
        assertEquals(LocalDate.of(2026, 9, 1), clips.last().capturedOn)
        assertEquals(File(files.directory, "clip-1.mp4").path, clips.last().path)

        viewModel.onEvent(GalleryEvent.ToggleCompare(first))
        viewModel.onEvent(GalleryEvent.ToggleCompare(second))
        assertEquals(listOf(first, second), viewModel.uiState.value.compareIds)
        viewModel.onEvent(GalleryEvent.ToggleCompare(third))
        assertEquals(listOf(second, third), viewModel.uiState.value.compareIds)
        viewModel.onEvent(GalleryEvent.ToggleCompare(third))
        assertEquals(listOf(second), viewModel.uiState.value.compareIds)

        viewModel.onEvent(GalleryEvent.SelectExercise(press))
        awaitUntil { viewModel.uiState.value.clips.size == 1 }
        assertEquals("press 60kg × 8", viewModel.uiState.value.clips.single().label)
        assertEquals(emptyList<Long>(), viewModel.uiState.value.compareIds)
    }

    @Test
    fun deleteRemovesTheClipAndItsFile() = runBlocking {
        val files = files()
        val exerciseId = DaoFixture(db).exercise("squat")
        val id = clip(files, exerciseId, "clip-9.mp4", "squat 100kg × 5", Instant.parse("2026-09-01T10:00:00Z"))
        val viewModel = gallery(files)
        viewModel.onEvent(GalleryEvent.SelectExercise(exerciseId))
        awaitUntil { viewModel.uiState.value.clips.size == 1 }

        viewModel.onEvent(GalleryEvent.Delete(id))

        awaitUntil { viewModel.uiState.value.clips.isEmpty() }
        assertFalse(File(files.directory, "clip-9.mp4").exists())
    }

    @Test
    fun physiqueSliderAndMilestonesFollowThePose() = runBlocking {
        val files = files()
        val start = LocalDate.of(2026, 6, 1)
        photo(files, Pose.FRONT, start)
        photo(files, Pose.FRONT, start.plusWeeks(4).plusDays(1))
        photo(files, Pose.SIDE, start)

        val viewModel = gallery(files)
        awaitUntil { viewModel.uiState.value.photos.size == 3 }
        val state = viewModel.uiState.value
        assertEquals(Pose.FRONT, state.sliderPose)
        assertEquals(start, state.before!!.capturedOn)
        assertEquals(start.plusWeeks(4).plusDays(1), state.after!!.capturedOn)

        // 4-week and 12-week check-ins are due by 1 October; the first one has a photo one day late.
        assertEquals(listOf(start.plusWeeks(4), start.plusWeeks(12)), state.milestones.map { it.dueOn })
        assertEquals(start.plusWeeks(4).plusDays(1), state.milestones[0].photoOn)
        assertNotNull(state.milestones[1].photoOn)

        viewModel.onEvent(GalleryEvent.SelectPose(Pose.SIDE))
        val side = viewModel.uiState.value
        assertEquals(start, side.before!!.capturedOn)
        assertEquals(start, side.after!!.capturedOn)

        viewModel.onEvent(GalleryEvent.SelectPose(Pose.BACK))
        assertNull(viewModel.uiState.value.before)
        assertNull(viewModel.uiState.value.after)
    }
}
