package com.forge.hypertrophy.ui.screens.transfer

import androidx.lifecycle.SavedStateHandle
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
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.data.storage.ProgramDocumentStore
import com.forge.hypertrophy.data.transfer.ImportMode
import com.forge.hypertrophy.data.transfer.ProgramImportPreferences
import com.forge.hypertrophy.data.transfer.ProgramImporter
import com.forge.hypertrophy.data.transfer.ProgramJson
import com.forge.hypertrophy.data.transfer.SampleProgramProvider
import com.forge.hypertrophy.data.transfer.programDocument
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MediaType
import com.forge.hypertrophy.domain.model.Pose
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.ui.screens.routine.awaitUntil
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportPreviewViewModelTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC)
    private val programRepo = FakeProgramRepository()
    private val routineRepo = FakeRoutineRepository()
    private val exerciseRepo = FakeExerciseRepository(clock)
    private val skillRepo = FakeSkillRepository(clock)
    private val sessionRepo = FakeSessionRepository()
    private val activeViewModels = mutableListOf<ImportPreviewViewModel>()

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

    @Test
    fun previewIsPopulatedFromAValidDocument() = runBlocking {
        val viewModel = preview(programDocument(), RecordingPreferences())
        awaitUntil { !viewModel.uiState.value.loading }

        val state = viewModel.uiState.value
        assertEquals("Protocol", state.programName)
        assertEquals(1, state.dayCount)
        assertEquals(1, state.slotCount)
        assertEquals(1, state.newExerciseCount)
        assertEquals(0, state.newSkillCount)
        assertTrue(state.errors.isEmpty())
        assertTrue(state.canConfirm)
        assertEquals(ImportMode.REPLACE_ROUTINE_MERGE_LIBRARY, state.mode)
        assertTrue(state.applyDefaults)
    }

    @Test
    fun aDocumentWithErrorsCannotBeConfirmed() = runBlocking {
        val preferences = RecordingPreferences()
        val viewModel = preview(programDocument(schemaVersion = 2), preferences)
        awaitUntil { !viewModel.uiState.value.loading }
        assertFalse(viewModel.uiState.value.canConfirm)
        assertTrue(viewModel.uiState.value.errors.isNotEmpty())

        viewModel.onEvent(ImportPreviewEvent.Confirm)

        assertFalse(viewModel.uiState.value.imported)
        assertTrue(programRepo.programs.isEmpty())
        assertEquals(0, preferences.calls)
    }

    @Test
    fun addProgramModeReachesTheImporter() = runBlocking {
        seedExistingLibrary()
        val viewModel = preview(programDocument(), RecordingPreferences())
        awaitUntil { !viewModel.uiState.value.loading }

        viewModel.onEvent(ImportPreviewEvent.Mode(ImportMode.ADD_PROGRAM))
        awaitUntil { viewModel.uiState.value.mode == ImportMode.ADD_PROGRAM && !viewModel.uiState.value.loading }
        viewModel.onEvent(ImportPreviewEvent.Confirm)
        awaitUntil { viewModel.uiState.value.imported }

        assertEquals(listOf("old", "Protocol"), programRepo.programs.values.map { it.name })
        assertNotNull(exerciseRepo.exercises.values.firstOrNull { it.name == "unrelated" })
    }

    @Test
    fun replaceRoutineModeReachesTheImporter() = runBlocking {
        seedExistingLibrary()
        val viewModel = preview(programDocument(), RecordingPreferences())
        awaitUntil { !viewModel.uiState.value.loading }

        viewModel.onEvent(ImportPreviewEvent.Confirm)
        awaitUntil { viewModel.uiState.value.imported }

        assertEquals(listOf("Protocol"), programRepo.programs.values.map { it.name })
        val unrelated = exerciseRepo.exercises.values.firstOrNull { it.name == "unrelated" }
        assertNotNull(unrelated)
        assertNull(unrelated!!.archivedAt)
    }

    @Test
    fun replaceEverythingModeReachesTheImporter() = runBlocking {
        seedExistingLibrary()
        val viewModel = preview(programDocument(), RecordingPreferences())
        awaitUntil { !viewModel.uiState.value.loading }

        viewModel.onEvent(ImportPreviewEvent.Mode(ImportMode.REPLACE_EVERYTHING))
        awaitUntil { viewModel.uiState.value.mode == ImportMode.REPLACE_EVERYTHING }
        viewModel.onEvent(ImportPreviewEvent.Confirm)
        awaitUntil { viewModel.uiState.value.imported }

        assertEquals(listOf("Protocol"), programRepo.programs.values.map { it.name })
        assertNull(exerciseRepo.exercises.values.firstOrNull { it.name == "unrelated" })
    }

    @Test
    fun preferencesOptOutSuppressesTheDefaultsWrite() = runBlocking {
        val preferences = RecordingPreferences()
        val viewModel = preview(programDocument(), preferences)
        awaitUntil { !viewModel.uiState.value.loading }

        viewModel.onEvent(ImportPreviewEvent.ApplyDefaults(false))
        viewModel.onEvent(ImportPreviewEvent.Confirm)
        awaitUntil { viewModel.uiState.value.imported }

        assertEquals(0, preferences.calls)
        assertEquals(1, programRepo.programs.size)
    }

    @Test
    fun preferencesOptInWritesTheDefaultsAfterImport() = runBlocking {
        val preferences = RecordingPreferences()
        val viewModel = preview(programDocument(), preferences)
        awaitUntil { !viewModel.uiState.value.loading }

        viewModel.onEvent(ImportPreviewEvent.Confirm)
        awaitUntil { viewModel.uiState.value.imported }

        assertEquals(1, preferences.calls)
        assertEquals(120, preferences.transitionRest)
        assertEquals(listOf(20.0, 10.0), preferences.plates)
    }

    private suspend fun seedExistingLibrary() {
        programRepo.setActive(programRepo.insert(ProgramEntity(name = "old", scheduleMode = ScheduleMode.FIXED, rollingSequence = 0, deloadActive = false, deloadStartedOn = null)))
        exerciseRepo.insert(ExerciseEntity(name = "unrelated", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
    }

    private fun preview(document: ProgramJson, preferences: ProgramImportPreferences) =
        ImportPreviewViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "uri" to "content://picked/program.json",
                    "sample" to false,
                ),
            ),
            documents = FakeDocumentStore(document),
            sampleProgram = UnavailableSample,
            importer = ProgramImporter(
                StubDatabase(programRepo, routineRepo, exerciseRepo, skillRepo, sessionRepo),
                clock,
                preferences
            ),
            programs = programRepo,
        ).also { activeViewModels.add(it) }

    @Test
    fun sampleRouteLoadsBundledProgram() = runBlocking {
        val sample = object : SampleProgramProvider {
            override val available: Boolean = true
            override suspend fun program(): ProgramJson = programDocument()
        }
        val viewModel = ImportPreviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("uri" to null, "sample" to true)),
            documents = FakeDocumentStore(programDocument()),
            sampleProgram = sample,
            importer = ProgramImporter(
                StubDatabase(programRepo, routineRepo, exerciseRepo, skillRepo, sessionRepo),
                clock,
                RecordingPreferences(),
            ),
            programs = programRepo,
        ).also { activeViewModels.add(it) }
        awaitUntil { !viewModel.uiState.value.loading }
        assertEquals("Protocol", viewModel.uiState.value.programName)
        assertTrue(viewModel.uiState.value.canConfirm)
        assertFalse(viewModel.uiState.value.unreadable)
    }

    private class FakeDocumentStore(private val document: ProgramJson) : ProgramDocumentStore {
        override suspend fun read(uri: String): ProgramJson = document

        override suspend fun write(uri: String, document: ProgramJson) = error("not used")
    }

    private object UnavailableSample : SampleProgramProvider {
        override val available: Boolean = false

        override suspend fun program(): ProgramJson = error("unavailable")
    }

    private class RecordingPreferences : ProgramImportPreferences {
        var calls = 0
        var transitionRest: Int? = null
        var plates: List<Double>? = null

        override suspend fun applyDefaults(transitionRestSeconds: Int, plateInventoryKg: List<Double>) {
            calls += 1
            transitionRest = transitionRestSeconds
            plates = plateInventoryKg
        }
    }

    private class StubDatabase(
        val programs: ProgramRepository,
        val routines: RoutineRepository,
        val exercises: ExerciseRepository,
        val skills: SkillRepository,
        val sessions: SessionRepository
    ) : com.forge.hypertrophy.data.db.AppDatabase() {
        override fun programDao() = object : com.forge.hypertrophy.data.dao.ProgramDao {
            override fun observe(): Flow<ProgramEntity?> = programs.observe()
            override suspend fun get(): ProgramEntity? = programs.get()
            override fun observeAll(): Flow<List<ProgramEntity>> = programs.observeAll()
            override suspend fun all(): List<ProgramEntity> = programs.observeAll().first()
            override fun observeActive(): Flow<ProgramEntity?> = programs.observeActive()
            override suspend fun getById(id: Long): ProgramEntity? = programs.getById(id)
            override suspend fun insert(program: ProgramEntity): Long = programs.insert(program)
            override suspend fun update(program: ProgramEntity) = programs.update(program)
            override suspend fun clearActive() {
                (programs as FakeProgramRepository).programs.forEach { (k, v) -> (programs as FakeProgramRepository).programs[k] = v.copy(isActive = false) }
            }
            override suspend fun markActive(id: Long) {
                (programs as FakeProgramRepository).programs[id] = (programs as FakeProgramRepository).programs[id]!!.copy(isActive = true)
            }
            override suspend fun delete(id: Long) = programs.delete(id)
            override suspend fun setActive(id: Long) = programs.setActive(id)
            override suspend fun deleteById(id: Long) = programs.delete(id)
            override suspend fun nextIdAfter(id: Long): Long? = null
            override suspend fun lowestOtherId(id: Long): Long? = null
        }

        override fun routineDao() = object : com.forge.hypertrophy.data.dao.RoutineDao {
            override fun observeDays(programId: Long): Flow<List<RoutineDayEntity>> = routines.observeDays(programId)
            override suspend fun getDay(id: Long): RoutineDayEntity? = routines.getDay(id)
            override suspend fun insertDay(day: RoutineDayEntity): Long = routines.insertDay(day)
            override suspend fun updateDay(day: RoutineDayEntity) = routines.updateDay(day)
            override suspend fun deleteDay(id: Long) = routines.deleteDay(id)
            override suspend fun reorderDays(programId: Long, orderedDayIds: List<Long>) = routines.reorderDays(programId, orderedDayIds)
            override suspend fun setDaySequence(id: Long, programId: Long, index: Int) {
                (routines as FakeRoutineRepository).days[id] = (routines as FakeRoutineRepository).days[id]!!.copy(sequenceIndex = index)
            }
            override suspend fun days(programId: Long): List<RoutineDayEntity> = routines.days(programId)
            override suspend fun slotsForDays(dayIds: List<Long>): List<RoutineSlotEntity> = routines.slotsForDays(dayIds)
            override fun observeChecklist(dayId: Long): Flow<List<ChecklistItemEntity>> = routines.observeChecklist(dayId)
            override suspend fun checklistForDays(dayIds: List<Long>): List<ChecklistItemEntity> = emptyList()
            override suspend fun insertChecklist(item: ChecklistItemEntity): Long = routines.insertChecklist(item)
            override suspend fun updateChecklist(item: ChecklistItemEntity) = routines.updateChecklist(item)
            override suspend fun deleteChecklist(id: Long) = routines.deleteChecklist(id)
            override fun observeSlots(dayId: Long): Flow<List<RoutineSlotEntity>> = routines.observeSlots(dayId)
            override suspend fun slots(dayId: Long): List<RoutineSlotEntity> = (routines as FakeRoutineRepository).slots.values.filter { it.dayId == dayId }
            override suspend fun getSlot(id: Long): RoutineSlotEntity? = routines.getSlot(id)
            override suspend fun insertSlot(slot: RoutineSlotEntity): Long = routines.insertSlot(slot)
            override suspend fun updateSlot(slot: RoutineSlotEntity) = routines.updateSlot(slot)
            override suspend fun deleteSlot(id: Long) = routines.deleteSlot(id)
            override suspend fun reorderSlots(dayId: Long, orderedSlotIds: List<Long>) = routines.reorderSlots(dayId, orderedSlotIds)
            override suspend fun setSlotOrder(id: Long, dayId: Long, index: Int) {
                (routines as FakeRoutineRepository).slots[id] = (routines as FakeRoutineRepository).slots[id]!!.copy(sortOrder = index)
            }
            override fun observeAlternatives(slotId: Long): Flow<List<SlotAlternativeEntity>> = routines.observeAlternatives(slotId)
            override suspend fun insertAlternative(alternative: SlotAlternativeEntity): Long = routines.insertAlternative(alternative)
            override suspend fun deleteAlternative(id: Long) = routines.deleteAlternative(id)
            override fun observeCardioPlan(dayId: Long): Flow<CardioPlanEntity?> = routines.observeCardioPlan(dayId)
            override suspend fun upsertCardioPlan(plan: CardioPlanEntity): Long = routines.upsertCardioPlan(plan)
            override suspend fun allSlots(): List<RoutineSlotEntity> = (routines as FakeRoutineRepository).slots.values.toList()
            override suspend fun allAlternatives(): List<SlotAlternativeEntity> = emptyList()
            override suspend fun targetedStepIds(): List<Long> = emptyList()
            override suspend fun cardioForDays(dayIds: List<Long>): List<CardioPlanEntity> = emptyList()
            override suspend fun alternativesForSlots(slotIds: List<Long>): List<SlotAlternativeEntity> = emptyList()
        }

        override fun exerciseDao() = object : com.forge.hypertrophy.data.dao.ExerciseDao {
            override fun observeActive(): Flow<List<ExerciseEntity>> = exercises.observeActive()
            override suspend fun get(id: Long): ExerciseEntity? = exercises.get(id)
            override suspend fun all(): List<ExerciseEntity> = exercises.all()
            override suspend fun getAll(ids: List<Long>): List<ExerciseEntity> = exercises.all().filter { it.id in ids }
            override suspend fun insert(exercise: ExerciseEntity): Long = exercises.insert(exercise)
            override suspend fun update(exercise: ExerciseEntity) = exercises.update(exercise)
            override suspend fun archive(id: Long, at: Instant) = exercises.archive(id)
            override suspend fun delete(id: Long) = exercises.delete(id)
        }

        override fun skillDao() = object : com.forge.hypertrophy.data.dao.SkillDao {
            override fun observeActive(): Flow<List<SkillEntity>> = skills.observeActive()
            override suspend fun get(id: Long): SkillEntity? = skills.get(id)
            override suspend fun all(): List<SkillEntity> = (skills as FakeSkillRepository).skills.values.toList()
            override suspend fun insert(skill: SkillEntity): Long = skills.insert(skill)
            override suspend fun update(skill: SkillEntity) = skills.update(skill)
            override suspend fun archive(id: Long, at: Instant) = skills.archive(id)
            override suspend fun delete(id: Long) = skills.delete(id)
            override fun observeSteps(skillId: Long): Flow<List<SkillStepEntity>> = skills.observeSteps(skillId)
            override suspend fun getSteps(skillId: Long): List<SkillStepEntity> = skills.getSteps(skillId)
            override suspend fun stepsByIds(ids: List<Long>): List<SkillStepEntity> = (skills as FakeSkillRepository).steps.values.filter { it.id in ids }
            override suspend fun insertStep(step: SkillStepEntity): Long = skills.insertStep(step)
            override suspend fun updateStep(step: SkillStepEntity) = skills.updateStep(step)
            override suspend fun deleteStep(id: Long) = skills.deleteStep(id)
            override suspend fun getProgress(skillId: Long): SkillProgressEntity? = skills.getProgress(skillId)
            override suspend fun allSteps(): List<SkillStepEntity> = skills.allSteps()
            override suspend fun allProgress(): List<SkillProgressEntity> = skills.allProgress()
            override suspend fun upsertProgress(progress: SkillProgressEntity): Long = skills.upsertProgress(progress)
            override suspend fun insertStageEvent(event: SkillStageEventEntity): Long = skills.recordStageEvent(event)
            override suspend fun stageEventsBetween(from: LocalDate, to: LocalDate): List<SkillStageEventEntity> = skills.stageEventsBetween(from, to)
        }

        override fun sessionDao() = object : com.forge.hypertrophy.data.dao.SessionDao {
            override fun observe(id: Long): Flow<WorkoutSessionEntity?> = sessions.observe(id)
            override fun observeInProgress(): Flow<List<WorkoutSessionEntity>> = sessions.observeInProgress()
            override suspend fun get(id: Long): WorkoutSessionEntity? = sessions.get(id)
            override suspend fun insert(session: WorkoutSessionEntity): Long = sessions.insert(session)
            override suspend fun update(session: WorkoutSessionEntity) = sessions.update(session)
            override suspend fun delete(id: Long) = sessions.delete(id)
            override fun observeSlots(sessionId: Long): Flow<List<SessionSlotEntity>> = sessions.observeSlots(sessionId)
            override suspend fun insertSlot(slot: SessionSlotEntity): Long = sessions.insertSlot(slot)
            override suspend fun updateSlot(slot: SessionSlotEntity) = sessions.updateSlot(slot)
            override fun observeSets(sessionSlotId: Long): Flow<List<SetEntryEntity>> = sessions.observeSets(sessionSlotId)
            override suspend fun insertSet(entry: SetEntryEntity): Long = sessions.insertSet(entry)
            override suspend fun updateSet(entry: SetEntryEntity) = sessions.updateSet(entry)
            override suspend fun deleteSet(id: Long) = sessions.deleteSet(id)
            override suspend fun allSlots(): List<SessionSlotEntity> = sessions.allSlots()
            override suspend fun getSlot(id: Long): SessionSlotEntity? = sessions.getSlot(id)
            override suspend fun completedDays(): List<CompletedSessionDay> = sessions.completedDays()
            override suspend fun completedSets(): List<CompletedSetRow> = sessions.completedSets()
            override suspend fun getSet(id: Long): SetEntryEntity? = sessions.getSet(id)
            override suspend fun earliestCompletedDate(): LocalDate? = sessions.earliestCompletedDate()
            override suspend fun history(): List<WorkoutSessionEntity> = sessions.history()
            override suspend fun recentSetsForExercise(exerciseId: Long, limit: Int): List<SetEntryEntity> = sessions.recentSetsForExercise(exerciseId, limit)
        }
        
        override fun mediaDao() = object : com.forge.hypertrophy.data.dao.MediaDao {
            override fun observeForExercise(exerciseId: Long): Flow<List<MediaItemEntity>> = MutableStateFlow(emptyList())
            override fun observeForSet(setEntryId: Long): Flow<List<MediaItemEntity>> = MutableStateFlow(emptyList())
            override fun observeAll(): Flow<List<MediaItemEntity>> = MutableStateFlow(emptyList())
            override fun observePhotos(): Flow<List<MediaItemEntity>> = MutableStateFlow(emptyList())
            override suspend fun get(id: Long): MediaItemEntity? = null
            override suspend fun insert(item: MediaItemEntity): Long = 0L
            override suspend fun update(item: MediaItemEntity) {}
            override suspend fun delete(id: Long) {}
            override suspend fun all(): List<MediaItemEntity> = emptyList()
            override suspend fun referencedExerciseIds(): List<Long> = emptyList()
        }

        override fun cardioDao() = object : com.forge.hypertrophy.data.dao.CardioDao {
            override fun observeLog(sessionId: Long) = MutableStateFlow(null)
            override suspend fun insert(log: com.forge.hypertrophy.data.entity.CardioLogEntity) = 0L
            override suspend fun update(log: com.forge.hypertrophy.data.entity.CardioLogEntity) {}
            override suspend fun delete(id: Long) {}
            override fun observeTrackPoints(cardioLogId: Long) = MutableStateFlow(emptyList<com.forge.hypertrophy.data.entity.TrackPointEntity>())
            override suspend fun insertTrackPoints(points: List<com.forge.hypertrophy.data.entity.TrackPointEntity>) = emptyList<Long>()
            override fun observeAll() = MutableStateFlow(emptyList<com.forge.hypertrophy.data.entity.CardioLogEntity>())
            override fun observeMileage() = MutableStateFlow(emptyList<com.forge.hypertrophy.data.dao.GearMileage>())
        }

        override fun biometricsDao() = object : com.forge.hypertrophy.data.dao.BiometricsDao {
            override fun observeAll() = MutableStateFlow(emptyList<com.forge.hypertrophy.data.entity.BiometricsEntity>())
            override suspend fun get(date: LocalDate) = null
            override suspend fun upsert(biometrics: com.forge.hypertrophy.data.entity.BiometricsEntity) = 0L
        }

        override fun gearDao() = object : com.forge.hypertrophy.data.dao.GearDao {
            override fun observeActive() = MutableStateFlow(emptyList<com.forge.hypertrophy.data.entity.GearEntity>())
            override suspend fun get(id: Long) = null
            override suspend fun insert(gear: com.forge.hypertrophy.data.entity.GearEntity) = 0L
            override suspend fun update(gear: com.forge.hypertrophy.data.entity.GearEntity) {}
            override suspend fun archive(id: Long, archivedAt: Instant) {}
            override suspend fun delete(id: Long) {}
        }

        override fun baselineDao() = object : com.forge.hypertrophy.data.dao.BaselineDao {
            override suspend fun forSlot(slotId: Long) = null
            override suspend fun forSlots(slotIds: List<Long>) = emptyList<com.forge.hypertrophy.data.entity.SlotBaselineEntity>()
            override suspend fun all() = emptyList<com.forge.hypertrophy.data.entity.SlotBaselineEntity>()
            override suspend fun upsert(entity: com.forge.hypertrophy.data.entity.SlotBaselineEntity) = 0L
        }

        override suspend fun <R> runTransaction(block: suspend () -> R): R = block()

        override fun createInvalidationTracker(): androidx.room.InvalidationTracker = error("stub")
        override fun clearAllTables() {}
    }

    private class FakeProgramRepository : ProgramRepository {
        val programs = mutableMapOf<Long, ProgramEntity>()
        private val flow = MutableStateFlow(emptyList<ProgramEntity>())

        override fun observe(): Flow<ProgramEntity?> = flow.map { it.find { p -> p.isActive } }
        override fun observeAll(): Flow<List<ProgramEntity>> = flow
        override fun observeActive(): Flow<ProgramEntity?> = flow.map { it.find { p -> p.isActive } }
        override suspend fun get(): ProgramEntity? = programs.values.find { it.isActive }
        override suspend fun getById(id: Long): ProgramEntity? = programs[id]
        override suspend fun insert(program: ProgramEntity): Long {
            val id = (programs.keys.maxOrNull() ?: 0L) + 1
            programs[id] = program.copy(id = id)
            flow.value = programs.values.toList()
            return id
        }
        override suspend fun update(program: ProgramEntity) {
            programs[program.id] = program
            flow.value = programs.values.toList()
        }
        override suspend fun setActive(id: Long) {
            programs.forEach { (k, v) -> programs[k] = v.copy(isActive = k == id) }
            flow.value = programs.values.toList()
        }
        override suspend fun delete(id: Long) {
            programs.remove(id)
            flow.value = programs.values.toList()
        }
    }

    private class FakeRoutineRepository : RoutineRepository {
        val days = mutableMapOf<Long, RoutineDayEntity>()
        val slots = mutableMapOf<Long, RoutineSlotEntity>()
        private val daysFlow = MutableStateFlow(emptyList<RoutineDayEntity>())
        private val slotsFlow = MutableStateFlow(emptyList<RoutineSlotEntity>())

        override fun observeDays(programId: Long): Flow<List<RoutineDayEntity>> =
            daysFlow.map { it.filter { d -> d.programId == programId }.sortedBy { it.sequenceIndex } }

        override suspend fun getDay(id: Long): RoutineDayEntity? = days[id]
        override suspend fun insertDay(day: RoutineDayEntity): Long {
            val id = (days.keys.maxOrNull() ?: 0L) + 1
            days[id] = day.copy(id = id)
            daysFlow.value = days.values.toList()
            return id
        }
        override suspend fun updateDay(day: RoutineDayEntity) {
            days[day.id] = day
            daysFlow.value = days.values.toList()
        }
        override suspend fun deleteDay(id: Long) {
            days.remove(id)
            daysFlow.value = days.values.toList()
        }
        override suspend fun reorderDays(programId: Long, orderedDayIds: List<Long>) {
            orderedDayIds.forEachIndexed { index, id ->
                days[id] = days[id]!!.copy(sequenceIndex = index)
            }
            daysFlow.value = days.values.toList()
        }
        override suspend fun days(programId: Long): List<RoutineDayEntity> =
            days.values.filter { it.programId == programId }.sortedBy { it.sequenceIndex }

        override suspend fun slotsForDays(dayIds: List<Long>): List<RoutineSlotEntity> = slots.values.filter { it.dayId in dayIds }
        override fun observeChecklist(dayId: Long): Flow<List<ChecklistItemEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertChecklist(item: ChecklistItemEntity): Long = 0L
        override suspend fun updateChecklist(item: ChecklistItemEntity) {}
        override suspend fun deleteChecklist(id: Long) {}
        override fun observeSlots(dayId: Long): Flow<List<RoutineSlotEntity>> =
            slotsFlow.map { it.filter { s -> s.dayId == dayId }.sortedBy { it.sortOrder } }

        override suspend fun getSlot(id: Long): RoutineSlotEntity? = slots[id]
        override suspend fun insertSlot(slot: RoutineSlotEntity): Long {
            val id = (slots.keys.maxOrNull() ?: 0L) + 1
            slots[id] = slot.copy(id = id)
            slotsFlow.value = slots.values.toList()
            return id
        }
        override suspend fun updateSlot(slot: RoutineSlotEntity) {
            slots[slot.id] = slot
            slotsFlow.value = slots.values.toList()
        }
        override suspend fun deleteSlot(id: Long) {
            slots.remove(id)
            slotsFlow.value = slots.values.toList()
        }
        override suspend fun reorderSlots(dayId: Long, orderedSlotIds: List<Long>) {
            orderedSlotIds.forEachIndexed { index, id ->
                slots[id] = slots[id]!!.copy(sortOrder = index)
            }
            slotsFlow.value = slots.values.toList()
        }
        override fun observeAlternatives(slotId: Long): Flow<List<SlotAlternativeEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertAlternative(alternative: SlotAlternativeEntity): Long = 0L
        override suspend fun deleteAlternative(id: Long) {}
        override fun observeCardioPlan(dayId: Long): Flow<CardioPlanEntity?> = MutableStateFlow(null)
        override suspend fun upsertCardioPlan(plan: CardioPlanEntity): Long = 0L
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

    private class FakeSkillRepository(private val clock: Clock) : SkillRepository {
        val skills = mutableMapOf<Long, SkillEntity>()
        val steps = mutableMapOf<Long, SkillStepEntity>()
        private val flow = MutableStateFlow(emptyList<SkillEntity>())

        override fun observeActive(): Flow<List<SkillEntity>> = flow.map { it.filter { s -> s.archivedAt == null } }
        override suspend fun get(id: Long): SkillEntity? = skills[id]
        override suspend fun insert(skill: SkillEntity): Long {
            val id = (skills.keys.maxOrNull() ?: 0L) + 1
            skills[id] = skill.copy(id = id)
            flow.value = skills.values.toList()
            return id
        }
        override suspend fun update(skill: SkillEntity) {
            skills[skill.id] = skill
            flow.value = skills.values.toList()
        }
        override suspend fun archive(id: Long) {
            skills[id] = skills[id]!!.copy(archivedAt = clock.instant())
            flow.value = skills.values.toList()
        }
        override suspend fun delete(id: Long) {
            skills.remove(id)
            flow.value = skills.values.toList()
        }
        override fun observeSteps(skillId: Long): Flow<List<SkillStepEntity>> = MutableStateFlow(steps.values.filter { it.skillId == skillId })
        override suspend fun getSteps(skillId: Long): List<SkillStepEntity> = steps.values.filter { it.skillId == skillId }
        override suspend fun insertStep(step: SkillStepEntity): Long {
            val id = (steps.keys.maxOrNull() ?: 0L) + 1
            steps[id] = step.copy(id = id)
            return id
        }
        override suspend fun updateStep(step: SkillStepEntity) {}
        override suspend fun deleteStep(id: Long) {}
        override suspend fun getProgress(skillId: Long): SkillProgressEntity? = null
        override suspend fun allSteps(): List<SkillStepEntity> = emptyList()
        override suspend fun allProgress(): List<SkillProgressEntity> = emptyList()
        override suspend fun upsertProgress(progress: SkillProgressEntity): Long = 0L
        override suspend fun recordStageEvent(event: SkillStageEventEntity): Long = 0L
        override suspend fun stageEventsBetween(from: LocalDate, to: LocalDate): List<SkillStageEventEntity> = emptyList()
        override suspend fun referencedIds(): Set<Long> = emptySet()
    }

    private class FakeSessionRepository : SessionRepository {
        val sessions = mutableMapOf<Long, WorkoutSessionEntity>()
        val slots = mutableMapOf<Long, SessionSlotEntity>()
        
        private val sessionsFlow = MutableStateFlow(emptyList<WorkoutSessionEntity>())
        private val slotsFlow = MutableStateFlow(emptyList<SessionSlotEntity>())

        override fun observe(id: Long): Flow<WorkoutSessionEntity?> = sessionsFlow.map { it.find { s -> s.id == id } }
        override fun observeInProgress(): Flow<List<WorkoutSessionEntity>> = sessionsFlow.map { it.filter { s -> s.status == SessionStatus.IN_PROGRESS } }
        override suspend fun get(id: Long): WorkoutSessionEntity? = sessions[id]
        override suspend fun insert(session: WorkoutSessionEntity): Long {
            val id = (sessions.keys.maxOrNull() ?: 0L) + 1
            sessions[id] = session.copy(id = id)
            sessionsFlow.value = sessions.values.toList()
            return id
        }
        override suspend fun update(session: WorkoutSessionEntity) {
            sessions[session.id] = session
            sessionsFlow.value = sessions.values.toList()
        }
        override suspend fun delete(id: Long) {
            sessions.remove(id)
            sessionsFlow.value = sessions.values.toList()
        }
        override fun observeSlots(sessionId: Long): Flow<List<SessionSlotEntity>> = slotsFlow.map { it.filter { s -> s.sessionId == sessionId } }
        override suspend fun insertSlot(slot: SessionSlotEntity): Long {
            val id = (slots.keys.maxOrNull() ?: 0L) + 1
            slots[id] = slot.copy(id = id)
            slotsFlow.value = slots.values.toList()
            return id
        }
        override suspend fun updateSlot(slot: SessionSlotEntity) {
            slots[slot.id] = slot
            slotsFlow.value = slots.values.toList()
        }
        override fun observeSets(sessionSlotId: Long): Flow<List<SetEntryEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertSet(entry: SetEntryEntity): Long = 0L
        override suspend fun updateSet(entry: SetEntryEntity) {}
        override suspend fun deleteSet(id: Long) {}
        override suspend fun allSlots(): List<SessionSlotEntity> = slots.values.toList()
        override suspend fun sets(sessionSlotId: Long): List<SetEntryEntity> = emptyList()
        override suspend fun getSlot(id: Long): SessionSlotEntity? = slots[id]
        override suspend fun completedDays(): List<CompletedSessionDay> = emptyList()
        override suspend fun completedSets(): List<CompletedSetRow> = emptyList()
        override suspend fun getSet(id: Long): SetEntryEntity? = null
        override suspend fun recentSetsForExercise(exerciseId: Long, limit: Int): List<SetEntryEntity> = emptyList()
        override suspend fun earliestCompletedDate(): LocalDate? = null
        override suspend fun history(): List<WorkoutSessionEntity> = emptyList()
    }
}