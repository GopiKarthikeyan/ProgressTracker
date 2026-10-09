package com.forge.hypertrophy.ui.screens.today

import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.dao.CompletedSessionDay
import com.forge.hypertrophy.data.dao.CompletedSetRow
import com.forge.hypertrophy.data.entity.CardioPlanEntity
import com.forge.hypertrophy.data.entity.ChecklistItemEntity
import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SessionSlotEntity
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.entity.SlotAlternativeEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.ScheduleCursorRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.schedule.ScheduleReconciler
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.usecase.StartWorkoutUseCase
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.ui.screens.routine.awaitUntil
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val clock = MutableClock(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val programRepo = FakeProgramRepository()
    private val routineRepo = FakeRoutineRepository()
    private val sessionRepo = FakeSessionRepository()
    private val preferences = FakePreferences()
    private val cursor = MemoryCursor()
    private val activeViewModels = mutableListOf<TodayViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        clock.instant = Instant.parse("2026-10-05T12:00:00Z")
        programRepo.reset()
        routineRepo.reset()
        sessionRepo.reset()
        preferences.reset()
        cursor.reset()
    }

    @After
    fun tearDown() {
        activeViewModels.forEach { it.viewModelScope.cancel() }
        activeViewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun noProgramReturnsNullDayLabel() = runBlocking {
        val viewModel = today()
        awaitUntil { viewModel.uiState.value.dayLabel == null }
        assertNull(viewModel.uiState.value.dayLabel)
        assertFalse(viewModel.uiState.value.isInProgress)
    }

    @Test
    fun activeProgramReturnsCurrentDayLabel() = runBlocking {
        val programId = programRepo.insert(
            ProgramEntity(
                name = "rolling",
                scheduleMode = ScheduleMode.ROLLING,
                rollingSequence = 0,
                deloadActive = false,
                deloadStartedOn = null,
            ),
        )
        val dayId = routineRepo.insertDay(
            RoutineDayEntity(
                programId = programId,
                label = "Push",
                sequenceIndex = 0,
                dayOfWeek = null,
                isRest = false,
            )
        )
        programRepo.setActive(programId)

        val viewModel = today()
        awaitUntil { viewModel.uiState.value.dayLabel == "Push" }
        assertEquals("Push", viewModel.uiState.value.dayLabel)
        assertEquals(dayId, viewModel.uiState.value.scheduledDayId)
        assertFalse(viewModel.uiState.value.isRestDay)
    }

    @Test
    fun startWorkoutCreatesASessionAndRequestsNavigation() = runBlocking {
        val programId = programRepo.insert(
            ProgramEntity(
                name = "rolling",
                scheduleMode = ScheduleMode.ROLLING,
                rollingSequence = 0,
                deloadActive = false,
                deloadStartedOn = null,
            ),
        )
        val dayId = routineRepo.insertDay(
            RoutineDayEntity(
                programId = programId,
                label = "Push",
                sequenceIndex = 0,
                dayOfWeek = null,
                isRest = false,
            ),
        )
        programRepo.setActive(programId)

        val viewModel = today()
        awaitUntil { viewModel.uiState.value.scheduledDayId == dayId }
        viewModel.onEvent(TodayEvent.StartWorkout)
        awaitUntil { viewModel.uiState.value.sessionToOpen != null }
        val sessionId = viewModel.uiState.value.sessionToOpen
        val session = sessionRepo.sessions.getValue(sessionId!!)
        assertEquals(dayId, session.dayId)
        assertEquals(SessionStatus.PLANNED, session.status)

        viewModel.onEvent(TodayEvent.OpenedSession)
        assertNull(viewModel.uiState.value.sessionToOpen)
    }

    @Test
    fun restDayDoesNotStartASession() = runBlocking {
        val programId = programRepo.insert(
            ProgramEntity(
                name = "rolling",
                scheduleMode = ScheduleMode.ROLLING,
                rollingSequence = 0,
                deloadActive = false,
                deloadStartedOn = null,
            ),
        )
        routineRepo.insertDay(
            RoutineDayEntity(
                programId = programId,
                label = "Rest",
                sequenceIndex = 0,
                dayOfWeek = null,
                isRest = true,
            ),
        )
        programRepo.setActive(programId)

        val viewModel = today()
        awaitUntil { viewModel.uiState.value.isRestDay }
        viewModel.onEvent(TodayEvent.StartWorkout)
        assertNull(viewModel.uiState.value.sessionToOpen)
        assertTrue(sessionRepo.sessions.isEmpty())
    }

    @Test
    fun inProgressSessionReflectedInState() = runBlocking {
        val sessionId = sessionRepo.insert(
            WorkoutSessionEntity(
                date = LocalDate.of(2026, 10, 5),
                dayId = 1L,
                kind = SessionKind.GYM,
                status = SessionStatus.IN_PROGRESS,
                isDeload = false,
                isShortOnTime = false,
                readinessSleep = null,
                readinessSoreness = null,
                readinessEnergy = null,
                startedAt = clock.instant(),
                completedAt = null,
            )
        )

        val viewModel = today()
        awaitUntil { viewModel.uiState.value.isInProgress }
        assertTrue(viewModel.uiState.value.isInProgress)
        assertEquals(sessionId, viewModel.uiState.value.activeSessionId)
        assertFalse(viewModel.uiState.value.completedToday)
    }

    @Test
    fun completedGymSessionAllowsRestart() = runBlocking {
        clock.instant = Instant.parse("2026-10-09T12:00:00Z")
        val programId = programRepo.insert(
            ProgramEntity(
                name = "fixed",
                scheduleMode = ScheduleMode.FIXED,
                rollingSequence = 0,
                deloadActive = false,
                deloadStartedOn = null,
            ),
        )
        val fridayId = routineRepo.insertDay(
            RoutineDayEntity(
                programId = programId,
                label = "Legs, Shoulders & Heavy Hinge",
                sequenceIndex = 4,
                dayOfWeek = 5,
                isRest = false,
            ),
        )
        programRepo.setActive(programId)
        sessionRepo.insert(
            WorkoutSessionEntity(
                date = LocalDate.of(2026, 10, 9),
                dayId = fridayId,
                kind = SessionKind.GYM,
                status = SessionStatus.COMPLETED,
                isDeload = false,
                isShortOnTime = false,
                readinessSleep = 3,
                readinessSoreness = 3,
                readinessEnergy = 3,
                startedAt = clock.instant().minusSeconds(3_600),
                completedAt = clock.instant(),
            ),
        )

        val viewModel = today()
        awaitUntil { viewModel.uiState.value.completedToday }
        assertTrue(viewModel.uiState.value.completedToday)
        assertFalse(viewModel.uiState.value.isInProgress)
        assertEquals(fridayId, viewModel.uiState.value.scheduledDayId)
        viewModel.onEvent(TodayEvent.StartWorkout)
        awaitUntil { viewModel.uiState.value.sessionToOpen != null }
        assertEquals(fridayId, sessionRepo.sessions.getValue(viewModel.uiState.value.sessionToOpen!!).dayId)
    }

    @Test
    fun thursdayInProgressDoesNotHideFridaysDay() = runBlocking {
        clock.instant = Instant.parse("2026-10-09T12:00:00Z")
        val programId = programRepo.insert(
            ProgramEntity(
                name = "fixed",
                scheduleMode = ScheduleMode.FIXED,
                rollingSequence = 0,
                deloadActive = false,
                deloadStartedOn = null,
            ),
        )
        routineRepo.insertDay(
            RoutineDayEntity(
                programId = programId,
                label = "Pull (Horizontal) & OAP Maintenance",
                sequenceIndex = 3,
                dayOfWeek = 4,
                isRest = false,
            ),
        )
        val fridayId = routineRepo.insertDay(
            RoutineDayEntity(
                programId = programId,
                label = "Legs, Shoulders & Heavy Hinge",
                sequenceIndex = 4,
                dayOfWeek = 5,
                isRest = false,
            ),
        )
        programRepo.setActive(programId)
        sessionRepo.insert(
            WorkoutSessionEntity(
                date = LocalDate.of(2026, 10, 8),
                dayId = 1L,
                kind = SessionKind.GYM,
                status = SessionStatus.IN_PROGRESS,
                isDeload = false,
                isShortOnTime = false,
                readinessSleep = null,
                readinessSoreness = null,
                readinessEnergy = null,
                startedAt = clock.instant(),
                completedAt = null,
            ),
        )

        val viewModel = today()
        awaitUntil { viewModel.uiState.value.dayLabel == "Legs, Shoulders & Heavy Hinge" }
        assertEquals("Legs, Shoulders & Heavy Hinge", viewModel.uiState.value.dayLabel)
        assertEquals(fridayId, viewModel.uiState.value.scheduledDayId)
        assertFalse(viewModel.uiState.value.isInProgress)
        assertNull(viewModel.uiState.value.activeSessionId)
    }

    @Test
    fun refreshMovesFromThursdayToFriday() = runBlocking {
        clock.instant = Instant.parse("2026-10-08T12:00:00Z")
        val programId = programRepo.insert(
            ProgramEntity(
                name = "fixed",
                scheduleMode = ScheduleMode.FIXED,
                rollingSequence = 0,
                deloadActive = false,
                deloadStartedOn = null,
            ),
        )
        routineRepo.insertDay(
            RoutineDayEntity(
                programId = programId,
                label = "Pull (Horizontal) & OAP Maintenance",
                sequenceIndex = 3,
                dayOfWeek = 4,
                isRest = false,
            ),
        )
        routineRepo.insertDay(
            RoutineDayEntity(
                programId = programId,
                label = "Legs, Shoulders & Heavy Hinge",
                sequenceIndex = 4,
                dayOfWeek = 5,
                isRest = false,
            ),
        )
        programRepo.setActive(programId)

        val viewModel = today()
        awaitUntil { viewModel.uiState.value.dayLabel == "Pull (Horizontal) & OAP Maintenance" }
        clock.instant = Instant.parse("2026-10-09T12:00:00Z")
        viewModel.onEvent(TodayEvent.Refresh)
        awaitUntil { viewModel.uiState.value.dayLabel == "Legs, Shoulders & Heavy Hinge" }
        assertEquals("Legs, Shoulders & Heavy Hinge", viewModel.uiState.value.dayLabel)
    }

    private fun today(): TodayViewModel {
        val vm = TodayViewModel(
            programs = programRepo,
            routines = routineRepo,
            sessions = sessionRepo,
            preferences = preferences,
            cursor = cursor,
            clock = clock,
            startWorkout = StartWorkoutUseCase(sessionRepo, routineRepo, programRepo, clock),
            reconciler = ScheduleReconciler(programRepo, routineRepo, sessionRepo, preferences, cursor, clock),
        )
        activeViewModels.add(vm)
        return vm
    }

    private class MutableClock(
        @Volatile var instant: Instant,
        private val zone: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant
    }

    private class MemoryCursor : ScheduleCursorRepository {
        private val swaps = MutableStateFlow<Map<LocalDate, Long>>(emptyMap())
        private val rolling = MutableStateFlow<Map<LocalDate, Long>>(emptyMap())
        private val rests = MutableStateFlow<Set<LocalDate>>(emptySet())

        override val fixedSwaps = swaps
        override val rollingDayByDate = rolling
        override val autoCompletedRests = rests

        fun reset() {
            swaps.value = emptyMap()
            rolling.value = emptyMap()
            rests.value = emptySet()
        }

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

        fun reset() {
            reconciled.value = null
            plates.value = emptyList()
            rest.value = 120
            end.value = null
            defaultRest.value = 90
        }

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

        fun reset() {
            programs.clear()
            flow.value = emptyList()
        }

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

        fun reset() {
            days.clear()
            slots.clear()
            daysFlow.value = emptyList()
            slotsFlow.value = emptyList()
        }

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
        private val sessionsFlow = MutableStateFlow(emptyList<WorkoutSessionEntity>())

        fun reset() {
            sessions.clear()
            sessionsFlow.value = emptyList()
        }

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
        override fun observeSlots(sessionId: Long): Flow<List<SessionSlotEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertSlot(slot: SessionSlotEntity): Long = 0L
        override suspend fun updateSlot(slot: SessionSlotEntity) {}
        override fun observeSets(sessionSlotId: Long): Flow<List<SetEntryEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertSet(entry: SetEntryEntity): Long = 0L
        override suspend fun updateSet(entry: SetEntryEntity) {}
        override suspend fun deleteSet(id: Long) {}
        override suspend fun allSlots(): List<SessionSlotEntity> = emptyList()
        override suspend fun sets(sessionSlotId: Long): List<SetEntryEntity> = emptyList()
        override suspend fun completedDays(): List<CompletedSessionDay> =
            sessions.values
                .filter { it.status == SessionStatus.COMPLETED }
                .map { CompletedSessionDay(date = it.date, kind = it.kind) }
        override suspend fun completedSets(): List<CompletedSetRow> = emptyList()
        override suspend fun getSet(id: Long): SetEntryEntity? = null
        override suspend fun getSlot(id: Long): SessionSlotEntity? = null
        override suspend fun recentSetsForExercise(exerciseId: Long, limit: Int): List<SetEntryEntity> = emptyList()
        override suspend fun earliestCompletedDate(): LocalDate? = null
        override suspend fun history(): List<WorkoutSessionEntity> = emptyList()
    }
}
