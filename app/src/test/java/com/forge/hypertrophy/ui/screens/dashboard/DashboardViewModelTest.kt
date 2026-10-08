package com.forge.hypertrophy.ui.screens.dashboard

import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.dao.CompletedSessionDay
import com.forge.hypertrophy.data.dao.CompletedSetRow
import com.forge.hypertrophy.data.entity.BiometricsEntity
import com.forge.hypertrophy.data.entity.CardioPlanEntity
import com.forge.hypertrophy.data.entity.ChecklistItemEntity
import com.forge.hypertrophy.data.entity.ExerciseEntity
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
import com.forge.hypertrophy.data.entity.SlotBaselineEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.BaselineRepository
import com.forge.hypertrophy.data.repository.BiometricsRepository
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.ScheduleCursorRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.data.schedule.ScheduleReconciler
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.ui.screens.routine.awaitUntil
import com.forge.hypertrophy.widget.TodayWidgetRefresher
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val programRepo = FakeProgramRepository()
    private val routineRepo = FakeRoutineRepository()
    private val sessionRepo = FakeSessionRepository()
    private val exerciseRepo = FakeExerciseRepository()
    private val skillRepo = FakeSkillRepository()
    private val biometricsRepo = FakeBiometricsRepository()
    private val preferences = FakePreferences()
    private val cursor = MemoryCursor()
    private val baselines = FakeBaselineRepository()
    private val widget = CountingRefresher()
    private val activeViewModels = mutableListOf<DashboardViewModel>()

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
    fun freshInstallHasEmptyCards() = runBlocking {
        val viewModel = dashboard()
        awaitUntil { viewModel.uiState.value.heatmap.isNotEmpty() }
        assertNull(viewModel.uiState.value.today)
        assertEquals(0, viewModel.uiState.value.currentStreak)
        assertTrue(viewModel.uiState.value.heatmap.none { it.kind != null })
        assertTrue(viewModel.uiState.value.records.isEmpty())
        assertTrue(viewModel.uiState.value.skills.isEmpty())
        assertTrue(viewModel.uiState.value.weight.samples.isEmpty())
        assertTrue(viewModel.uiState.value.bodyFat.samples.isEmpty())
        assertTrue(viewModel.uiState.value.volume.isEmpty())
        assertTrue(viewModel.uiState.value.stalls.isEmpty())
        assertNull(viewModel.uiState.value.deload)
    }

    @Test
    fun skipToNextRefreshesTheTodayCard() = runBlocking {
        val programId = rollingProgram()
        routineRepo.insertDay(day(programId, "push", 0))
        routineRepo.insertDay(day(programId, "pull", 1))
        programRepo.setActive(programId)
        val viewModel = dashboard()
        awaitUntil { viewModel.uiState.value.today?.label == "push" }

        viewModel.onEvent(DashboardEvent.SkipToNext)

        awaitUntil { viewModel.uiState.value.today?.label == "pull" }
        assertEquals(1, programRepo.getById(programId)!!.rollingSequence)
    }

    @Test
    fun swapWithTomorrowRefreshesTheTodayCard() = runBlocking {
        val programId = programRepo.insert(
            ProgramEntity(
                name = "fixed",
                scheduleMode = ScheduleMode.FIXED,
                rollingSequence = 0,
                deloadActive = false,
                deloadStartedOn = null,
            ),
        )
        routineRepo.insertDay(day(programId, "push", 0, weekday = 1))
        routineRepo.insertDay(day(programId, "pull", 1, weekday = 2))
        programRepo.setActive(programId)
        val viewModel = dashboard()
        awaitUntil { viewModel.uiState.value.today?.label == "push" }

        viewModel.onEvent(DashboardEvent.SwapWithTomorrow)

        awaitUntil { viewModel.uiState.value.today?.label == "pull" }
    }

    @Test
    fun takeRestNowRefreshesTheTodayCard() = runBlocking {
        val programId = rollingProgram()
        routineRepo.insertDay(day(programId, "push", 0))
        routineRepo.insertDay(day(programId, "rest", 1, rest = true))
        routineRepo.insertDay(day(programId, "pull", 2))
        programRepo.setActive(programId)
        val viewModel = dashboard()
        awaitUntil { viewModel.uiState.value.today?.label == "push" }

        viewModel.onEvent(DashboardEvent.TakeRestNow)

        awaitUntil { viewModel.uiState.value.today?.label == "rest" }
        val logged = sessionRepo.completedDays()
        assertEquals(listOf(SessionKind.REST), logged.map { it.kind })
    }

    @Test
    fun weighInKeepsAGapOutOfTheAverage() = runBlocking {
        val early = LocalDate.of(2026, 9, 25)
        biometricsRepo.upsert(BiometricsEntity(date = early, bodyWeightKg = 70.0, bodyFatPercent = null))
        val viewModel = dashboard()
        awaitUntil { viewModel.uiState.value.weight.samples.size == 1 }

        viewModel.onEvent(DashboardEvent.WeightDraft("80"))
        viewModel.onEvent(DashboardEvent.BodyFatDraft("15"))
        viewModel.onEvent(DashboardEvent.SaveWeighIn)

        awaitUntil { viewModel.uiState.value.weight.samples.size == 2 }
        val state = viewModel.uiState.value
        assertEquals(80.0, state.weight.average.last().value, 0.001)
        assertTrue(state.weight.average.none { it.value == 0.0 })
        assertEquals(15.0, state.bodyFat.samples.single().value, 0.001)
        assertEquals(80.0, biometricsRepo.get(LocalDate.of(2026, 10, 5))!!.bodyWeightKg!!, 0.001)
    }

    @Test
    fun scheduleCatchUpRefreshesTheWidget() = runBlocking {
        val programId = rollingProgram()
        routineRepo.insertDay(day(programId, "push", 0))
        programRepo.setActive(programId)
        val viewModel = dashboard()
        awaitUntil { viewModel.uiState.value.today?.label == "push" }
        awaitUntil { widget.count > 0 }
        val before = widget.count

        viewModel.onEvent(DashboardEvent.SkipToNext)

        awaitUntil { widget.count > before }
    }

    @Test
    fun daysAwayShowsTheDayThatWasSaved() = runBlocking {
        val programId = rollingProgram()
        routineRepo.insertDay(day(programId, "push", 0))
        routineRepo.insertDay(day(programId, "rest", 1, rest = true))
        routineRepo.insertDay(day(programId, "pull", 2))
        programRepo.setActive(programId)
        preferences.setLastReconciledDate(LocalDate.of(2026, 10, 2))

        val viewModel = dashboard()
        awaitUntil { viewModel.uiState.value.today != null }

        val savedIndex = programRepo.getById(programId)!!.rollingSequence
        val savedDay = routineRepo.days(programId).sortedBy { it.sequenceIndex }[savedIndex]
        val today = viewModel.uiState.value.today!!
        assertEquals(savedDay.label, today.label)
        assertEquals(savedDay.isRest, today.isRest)
        assertEquals(2, savedIndex)
        assertEquals("pull", today.label)
        assertEquals(LocalDate.of(2026, 10, 4), preferences.lastReconciledDate.first())
        assertEquals(setOf(LocalDate.of(2026, 10, 4)), cursor.autoCompletedRests.first())
    }

    private fun rollingProgram(): Long = runBlocking {
        programRepo.insert(
            ProgramEntity(
                name = "rolling",
                scheduleMode = ScheduleMode.ROLLING,
                rollingSequence = 0,
                deloadActive = false,
                deloadStartedOn = null,
            ),
        )
    }

    private fun day(
        programId: Long,
        label: String,
        sequence: Int,
        weekday: Int? = null,
        rest: Boolean = false,
    ) = RoutineDayEntity(
        programId = programId,
        label = label,
        dayOfWeek = weekday,
        sequenceIndex = sequence,
        isRest = rest,
    )

    private fun dashboard(): DashboardViewModel {
        val vm = DashboardViewModel(
            programs = programRepo,
            routines = routineRepo,
            sessions = sessionRepo,
            exercises = exerciseRepo,
            skills = skillRepo,
            biometrics = biometricsRepo,
            preferences = preferences,
            cursor = cursor,
            clock = clock,
            widget = widget,
            baselines = baselines,
            reconciler = ScheduleReconciler(programRepo, routineRepo, sessionRepo, preferences, cursor, clock),
        )
        activeViewModels.add(vm)
        return vm
    }

    private class CountingRefresher : TodayWidgetRefresher {
        var count = 0
        override suspend fun refresh() {
            count += 1
        }
    }

    private class MemoryCursor : ScheduleCursorRepository {
        private val swaps = MutableStateFlow<Map<LocalDate, Long>>(emptyMap())
        private val rolling = MutableStateFlow<Map<LocalDate, Long>>(emptyMap())
        private val rests = MutableStateFlow<Set<LocalDate>>(emptySet())

        override val fixedSwaps = swaps
        override val rollingDayByDate = rolling
        override val autoCompletedRests = rests

        override suspend fun save(
            fixedSwaps: Map<LocalDate, Long>,
            rollingDayByDate: Map<LocalDate, Long>,
            autoCompletedRests: Set<LocalDate>,
        ) {
            swaps.value = fixedSwaps
            rolling.value = rollingDayByDate
            rests.value = autoCompletedRests
        }
    }

    private class FakePreferences : TrainingPreferencesRepository {
        private val reconciled = MutableStateFlow<LocalDate?>(null)
        private val plates = MutableStateFlow<List<Double>>(emptyList())
        private val rest = MutableStateFlow(120)
        private val end = MutableStateFlow<Long?>(null)
        private val defaultRest = MutableStateFlow(90)

        override val lastReconciledDate = reconciled
        override val plateInventoryKg = plates
        override val transitionRestSeconds = rest
        override val activeTimerEndElapsedRealtime: Flow<Long?> = end
        override val defaultRestSeconds: Flow<Int> = defaultRest

        override suspend fun setLastReconciledDate(date: LocalDate?) {
            reconciled.value = date
        }

        override suspend fun setPlateInventoryKg(platesKg: List<Double>) {
            plates.value = platesKg
        }

        override suspend fun setTransitionRestSeconds(seconds: Int) {
            rest.value = seconds
        }

        override suspend fun setActiveTimerEndElapsedRealtime(elapsedRealtime: Long?) {
            end.value = elapsedRealtime
        }

        override suspend fun setDefaultRestSeconds(seconds: Int) {
            defaultRest.value = seconds
        }
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

        override fun observeDays(programId: Long): Flow<List<RoutineDayEntity>> = daysFlow.map { it.filter { d -> d.programId == programId } }
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
        override suspend fun reorderDays(programId: Long, orderedDayIds: List<Long>) {}
        override suspend fun days(programId: Long): List<RoutineDayEntity> = days.values.filter { it.programId == programId }
        override suspend fun slotsForDays(dayIds: List<Long>): List<RoutineSlotEntity> = slots.values.filter { it.dayId in dayIds }
        override fun observeChecklist(dayId: Long): Flow<List<ChecklistItemEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertChecklist(item: ChecklistItemEntity): Long = 0L
        override suspend fun updateChecklist(item: ChecklistItemEntity) {}
        override suspend fun deleteChecklist(id: Long) {}
        override fun observeSlots(dayId: Long): Flow<List<RoutineSlotEntity>> = slotsFlow.map { it.filter { s -> s.dayId == dayId } }
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
        override suspend fun reorderSlots(dayId: Long, orderedSlotIds: List<Long>) {}
        override fun observeAlternatives(slotId: Long): Flow<List<SlotAlternativeEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertAlternative(alternative: SlotAlternativeEntity): Long = 0L
        override suspend fun deleteAlternative(id: Long) {}
        override fun observeCardioPlan(dayId: Long): Flow<CardioPlanEntity?> = MutableStateFlow(null)
        override suspend fun upsertCardioPlan(plan: CardioPlanEntity): Long = 0L
    }

    private class FakeSessionRepository : SessionRepository {
        val sessions = mutableMapOf<Long, WorkoutSessionEntity>()
        val slots = mutableMapOf<Long, SessionSlotEntity>()
        val sets = mutableMapOf<Long, SetEntryEntity>()
        private val sessionsFlow = MutableStateFlow(emptyList<WorkoutSessionEntity>())

        override fun observe(id: Long): Flow<WorkoutSessionEntity?> = sessionsFlow.map { it.find { s -> s.id == id } }
        override fun observeInProgress(): Flow<List<WorkoutSessionEntity>> = sessionsFlow.map { it.filter { s -> s.status == SessionStatus.IN_PROGRESS } }
        override suspend fun get(id: Long): WorkoutSessionEntity? = sessions[id]
        override suspend fun insert(session: WorkoutSessionEntity): Long {
            val id = (sessions.keys.maxOrNull() ?: 0L) + 1
            val saved = session.copy(id = id)
            sessions[id] = saved
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
        override fun observeSlots(sessionId: Long): Flow<List<SessionSlotEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertSlot(slot: SessionSlotEntity): Long {
            val id = (slots.keys.maxOrNull() ?: 0L) + 1
            slots[id] = slot.copy(id = id)
            return id
        }
        override suspend fun updateSlot(slot: SessionSlotEntity) {}
        override fun observeSets(sessionSlotId: Long): Flow<List<SetEntryEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertSet(entry: SetEntryEntity): Long = 0L
        override suspend fun updateSet(entry: SetEntryEntity) {}
        override suspend fun deleteSet(id: Long) {}
        override suspend fun allSlots(): List<SessionSlotEntity> = slots.values.toList()
        override suspend fun sets(sessionSlotId: Long): List<SetEntryEntity> = emptyList()
        override suspend fun completedDays(): List<CompletedSessionDay> = sessions.values.filter { it.status == SessionStatus.COMPLETED }.map { CompletedSessionDay(it.date, it.kind) }
        override suspend fun completedSets(): List<CompletedSetRow> = emptyList()
        override suspend fun getSet(id: Long): SetEntryEntity? = null
        override suspend fun getSlot(id: Long): SessionSlotEntity? = null
        override suspend fun recentSetsForExercise(exerciseId: Long, limit: Int): List<SetEntryEntity> = emptyList()
        override suspend fun earliestCompletedDate(): LocalDate? = sessions.values.filter { it.status == SessionStatus.COMPLETED }.minOfOrNull { it.date }
        override suspend fun history(): List<WorkoutSessionEntity> = sessions.values.toList()
    }

    private class FakeExerciseRepository : ExerciseRepository {
        override fun observeActive(): Flow<List<ExerciseEntity>> = MutableStateFlow(emptyList())
        override suspend fun get(id: Long): ExerciseEntity? = null
        override suspend fun all(): List<ExerciseEntity> = emptyList()
        override suspend fun insert(exercise: ExerciseEntity): Long = 0L
        override suspend fun update(exercise: ExerciseEntity) {}
        override suspend fun archive(id: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun referencedIds(): Set<Long> = emptySet()
    }

    private class FakeSkillRepository : SkillRepository {
        override fun observeActive(): Flow<List<SkillEntity>> = MutableStateFlow(emptyList())
        override suspend fun get(id: Long): SkillEntity? = null
        override suspend fun insert(skill: SkillEntity): Long = 0L
        override suspend fun update(skill: SkillEntity) {}
        override suspend fun archive(id: Long) {}
        override suspend fun delete(id: Long) {}
        override fun observeSteps(skillId: Long): Flow<List<SkillStepEntity>> = MutableStateFlow(emptyList())
        override suspend fun getSteps(skillId: Long): List<SkillStepEntity> = emptyList()
        override suspend fun insertStep(step: SkillStepEntity): Long = 0L
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

    private class FakeBiometricsRepository : BiometricsRepository {
        val samples = mutableMapOf<LocalDate, BiometricsEntity>()
        private val flow = MutableStateFlow(emptyList<BiometricsEntity>())

        override fun observeAll(): Flow<List<BiometricsEntity>> = flow
        override suspend fun get(date: LocalDate): BiometricsEntity? = samples[date]
        override suspend fun upsert(biometrics: BiometricsEntity): Long {
            samples[biometrics.date] = biometrics
            flow.value = samples.values.toList()
            return 0L
        }
    }

    private class FakeBaselineRepository : BaselineRepository {
        override suspend fun forSlot(slotId: Long): SlotBaselineEntity? = null
        override suspend fun forSlots(slotIds: List<Long>): List<SlotBaselineEntity> = emptyList()
        override suspend fun all(): List<SlotBaselineEntity> = emptyList()
        override suspend fun save(entity: SlotBaselineEntity) {}
    }
}
