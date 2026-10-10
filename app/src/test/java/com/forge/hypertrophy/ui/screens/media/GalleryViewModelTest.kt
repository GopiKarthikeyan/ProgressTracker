package com.forge.hypertrophy.ui.screens.media

import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.dao.CompletedSessionDay
import com.forge.hypertrophy.data.dao.CompletedSetRow
import com.forge.hypertrophy.data.entity.CardioPlanEntity
import com.forge.hypertrophy.data.entity.ChecklistItemEntity
import com.forge.hypertrophy.data.entity.ExerciseEntity
import com.forge.hypertrophy.data.entity.MediaItemEntity
import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SessionSlotEntity
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.entity.SkillEntity
import com.forge.hypertrophy.data.entity.SkillProgressEntity
import com.forge.hypertrophy.data.entity.SkillStageEventEntity
import com.forge.hypertrophy.data.entity.SkillStepEntity
import com.forge.hypertrophy.data.entity.SlotAlternativeEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import android.net.Uri
import com.forge.hypertrophy.data.media.MediaFiles
import com.forge.hypertrophy.data.media.MediaImporter
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.MediaRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.ScheduleCursorRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.schedule.ScheduleLoader
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MediaType
import com.forge.hypertrophy.domain.model.Pose
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.ui.screens.routine.awaitUntil
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC)
    private val mediaRepo = FakeMediaRepository()
    private val exerciseRepo = FakeExerciseRepository(clock)
    private val sessionRepo = FakeSessionRepository()
    private val programRepo = FakeProgramRepository()
    private val preferences = FakePreferences()
    private val scheduleLoader = ScheduleLoader(
        programRepo,
        FakeRoutineRepository(),
        sessionRepo,
        preferences,
        FakeScheduleCursorRepository(),
    )
    private val activeViewModels = mutableListOf<GalleryViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        activeViewModels.forEach { it.viewModelScope.cancel() }
        activeViewModels.clear()
        Dispatchers.resetMain()
    }

    private fun files(): MediaFiles = MediaFiles(folder.root).also { it.directory.mkdirs() }

    private fun gallery(files: MediaFiles): GalleryViewModel {
        mediaRepo.files = files
        val vm = GalleryViewModel(
            media = mediaRepo,
            exercises = exerciseRepo,
            sessions = sessionRepo,
            programs = programRepo,
            preferences = preferences,
            scheduleLoader = scheduleLoader,
            importer = FakeMediaImporter(),
            clock = clock,
        )
        activeViewModels.add(vm)
        return vm
    }

    private suspend fun clip(files: MediaFiles, exerciseId: Long, name: String, label: String, at: Instant): Long {
        val file = File(files.directory, name).apply { writeText(name) }
        return mediaRepo.insert(
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
        return mediaRepo.insert(
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
        val deadlift = exerciseRepo.insert(ExerciseEntity(name = "deadlift", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
        val press = exerciseRepo.insert(ExerciseEntity(name = "press", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
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
        // Fixed clock is 2026-10-01 → 30 days after 1 Sep.
        assertTrue(clips.last().dateLine!!.contains("1 month ago"))
        assertFalse(clips.last().dateLine!!.startsWith("2026-09-01"))
        assertTrue(clips.first().dateLine!!.contains("2 weeks ago"))

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
        val exerciseId = exerciseRepo.insert(ExerciseEntity(name = "squat", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
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
        assertTrue(state.milestones[0].dueLine.contains("ago") || state.milestones[0].dueLine.contains("Today"))
        assertFalse(state.milestones[0].dueLine.contains("2026-"))
        assertNotNull(state.milestones[0].photoLine)
        assertFalse(state.milestones[0].photoLine!!.contains("2026-"))

        viewModel.onEvent(GalleryEvent.SelectPose(Pose.SIDE))
        val side = viewModel.uiState.value
        assertEquals(start, side.before!!.capturedOn)
        assertEquals(start, side.after!!.capturedOn)

        viewModel.onEvent(GalleryEvent.SelectPose(Pose.BACK))
        assertNull(viewModel.uiState.value.before)
        assertNull(viewModel.uiState.value.after)
    }

    private class FakeMediaRepository : MediaRepository {
        val items = mutableMapOf<Long, MediaItemEntity>()
        private val flow = MutableStateFlow(emptyList<MediaItemEntity>())
        var files: MediaFiles? = null

        override fun observeForExercise(exerciseId: Long): Flow<List<MediaItemEntity>> =
            flow.map { it.filter { i -> i.exerciseId == exerciseId }.sortedByDescending { i -> i.capturedAt } }

        override fun observeForSet(setEntryId: Long): Flow<List<MediaItemEntity>> =
            flow.map { it.filter { i -> i.setEntryId == setEntryId } }

        override fun observeAll(): Flow<List<MediaItemEntity>> = flow
        override fun observePhotos(): Flow<List<MediaItemEntity>> =
            flow.map { it.filter { i -> i.type == MediaType.PHOTO } }

        override suspend fun get(id: Long): MediaItemEntity? = items[id]
        override suspend fun insert(item: MediaItemEntity): Long {
            val id = (items.keys.maxOrNull() ?: 0L) + 1
            items[id] = item.copy(id = id)
            flow.value = items.values.toList()
            return id
        }

        override suspend fun update(item: MediaItemEntity) {
            items[item.id] = item
            flow.value = items.values.toList()
        }

        override suspend fun delete(id: Long) {
            val item = items.remove(id) ?: return
            flow.value = items.values.toList()
            files?.delete(item.uri)
        }

        override fun file(item: MediaItemEntity): File = files!!.resolve(item.uri)
        override suspend fun reconcile(): Int = 0
    }

    private class FakeExerciseRepository(private val clock: Clock) : ExerciseRepository {
        val exercises = mutableMapOf<Long, ExerciseEntity>()
        private val flow = MutableStateFlow(emptyList<ExerciseEntity>())

        override fun observeActive(): Flow<List<ExerciseEntity>> = flow.map { it.filter { e -> e.archivedAt == null } }
        override suspend fun get(id: Long): ExerciseEntity? = exercises[id]
        override suspend fun all(): List<ExerciseEntity> = exercises.values.toList()
        override suspend fun insert(exercise: ExerciseEntity): Long {
            val id = (exercises.keys.maxOrNull() ?: 0L) + 1
            exercises[id] = exercise.copy(id = id)
            flow.value = exercises.values.toList()
            return id
        }

        override suspend fun update(exercise: ExerciseEntity) {
            exercises[exercise.id] = exercise
            flow.value = exercises.values.toList()
        }

        override suspend fun archive(id: Long) {
            exercises[id] = exercises[id]!!.copy(archivedAt = clock.instant())
            flow.value = exercises.values.toList()
        }

        override suspend fun delete(id: Long) {
            exercises.remove(id)
            flow.value = exercises.values.toList()
        }

        override suspend fun referencedIds(): Set<Long> = emptySet()
    }

    private class FakeSessionRepository : SessionRepository {
        override fun observe(id: Long): Flow<WorkoutSessionEntity?> = MutableStateFlow(null)
        override fun observeInProgress(): Flow<List<WorkoutSessionEntity>> = MutableStateFlow(emptyList())
        override suspend fun get(id: Long): WorkoutSessionEntity? = null
        override suspend fun insert(session: WorkoutSessionEntity): Long = 0L
        override suspend fun update(session: WorkoutSessionEntity) {}
        override suspend fun delete(id: Long) {}
        override fun observeSlots(sessionId: Long): Flow<List<SessionSlotEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertSlot(slot: SessionSlotEntity): Long = 0L
        override suspend fun updateSlot(slot: SessionSlotEntity) {}
        override fun observeSets(sessionSlotId: Long): Flow<List<SetEntryEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertSet(entry: SetEntryEntity): Long = 0L
        override suspend fun updateSet(entry: SetEntryEntity) {}
        override suspend fun deleteSet(id: Long) {}
        override suspend fun allSlots(): List<SessionSlotEntity> = emptyList()
        override suspend fun sets(sessionSlotId: Long): List<SetEntryEntity> = emptyList()
        override suspend fun getSlot(id: Long): SessionSlotEntity? = null
        override suspend fun completedDays(): List<CompletedSessionDay> = emptyList()
        override suspend fun completedSets(): List<CompletedSetRow> = emptyList()
        override suspend fun getSet(id: Long): SetEntryEntity? = null
        override suspend fun recentSetsForExercise(exerciseId: Long, limit: Int): List<SetEntryEntity> = emptyList()
        override suspend fun earliestCompletedDate(): LocalDate? = null
        override suspend fun history(): List<WorkoutSessionEntity> = emptyList()
    }

    private class FakeProgramRepository : ProgramRepository {
        override fun observe(): Flow<ProgramEntity?> = MutableStateFlow(null)
        override fun observeAll(): Flow<List<ProgramEntity>> = MutableStateFlow(emptyList())
        override fun observeActive(): Flow<ProgramEntity?> = MutableStateFlow(null)
        override suspend fun get(): ProgramEntity? = null
        override suspend fun getById(id: Long): ProgramEntity? = null
        override suspend fun insert(program: ProgramEntity): Long = 0L
        override suspend fun update(program: ProgramEntity) {}
        override suspend fun setActive(id: Long) {}
        override suspend fun delete(id: Long) {}
    }

    private class FakePreferences : TrainingPreferencesRepository {
        private val rest = MutableStateFlow(120)
        override val lastReconciledDate = MutableStateFlow<LocalDate?>(null)
        override val plateInventoryKg = MutableStateFlow(emptyList<Double>())
        override val transitionRestSeconds = rest
        override val activeTimerEndElapsedRealtime = MutableStateFlow<Long?>(null)
        override val defaultRestSeconds = MutableStateFlow(90)
        override suspend fun setLastReconciledDate(date: LocalDate?) {}
        override suspend fun setPlateInventoryKg(platesKg: List<Double>) {}
        override suspend fun setTransitionRestSeconds(seconds: Int) {
            rest.value = seconds
        }
        override suspend fun setActiveTimerEndElapsedRealtime(elapsedRealtime: Long?) {}
        override suspend fun setDefaultRestSeconds(seconds: Int) {}
    }

    private class FakeScheduleCursorRepository : ScheduleCursorRepository {
        override val fixedSwaps = MutableStateFlow(emptyMap<LocalDate, Long>())
        override val rollingDayByDate = MutableStateFlow(emptyMap<LocalDate, Long>())
        override val autoCompletedRests = MutableStateFlow(emptySet<LocalDate>())
        override suspend fun save(
            fixedSwaps: Map<LocalDate, Long>,
            rollingDayByDate: Map<LocalDate, Long>,
            autoCompletedRests: Set<LocalDate>,
        ) {}
    }

    private class FakeRoutineRepository : RoutineRepository {
        override fun observeDays(programId: Long) = MutableStateFlow(emptyList<RoutineDayEntity>())
        override suspend fun getDay(id: Long): RoutineDayEntity? = null
        override suspend fun insertDay(day: RoutineDayEntity): Long = 0L
        override suspend fun updateDay(day: RoutineDayEntity) {}
        override suspend fun deleteDay(id: Long) {}
        override suspend fun reorderDays(programId: Long, orderedDayIds: List<Long>) {}
        override suspend fun days(programId: Long): List<RoutineDayEntity> = emptyList()
        override suspend fun slotsForDays(dayIds: List<Long>): List<RoutineSlotEntity> = emptyList()
        override fun observeChecklist(dayId: Long) = MutableStateFlow(emptyList<ChecklistItemEntity>())
        override suspend fun insertChecklist(item: ChecklistItemEntity): Long = 0L
        override suspend fun updateChecklist(item: ChecklistItemEntity) {}
        override suspend fun deleteChecklist(id: Long) {}
        override fun observeSlots(dayId: Long) = MutableStateFlow(emptyList<RoutineSlotEntity>())
        override suspend fun getSlot(id: Long): RoutineSlotEntity? = null
        override suspend fun insertSlot(slot: RoutineSlotEntity): Long = 0L
        override suspend fun updateSlot(slot: RoutineSlotEntity) {}
        override suspend fun deleteSlot(id: Long) {}
        override suspend fun reorderSlots(dayId: Long, orderedSlotIds: List<Long>) {}
        override fun observeAlternatives(slotId: Long) = MutableStateFlow(emptyList<SlotAlternativeEntity>())
        override suspend fun insertAlternative(alternative: SlotAlternativeEntity): Long = 0L
        override suspend fun deleteAlternative(id: Long) {}
        override fun observeCardioPlan(dayId: Long) = MutableStateFlow<CardioPlanEntity?>(null)
        override suspend fun upsertCardioPlan(plan: CardioPlanEntity): Long = 0L
    }

    private class FakeMediaImporter : MediaImporter {
        override suspend fun importPhoto(uri: Uri, pose: Pose): Boolean = false
        override suspend fun importVideo(
            uri: Uri,
            exerciseId: Long,
            setEntryId: Long?,
            onProgress: (Float) -> Unit,
        ): Boolean = false
    }
}
