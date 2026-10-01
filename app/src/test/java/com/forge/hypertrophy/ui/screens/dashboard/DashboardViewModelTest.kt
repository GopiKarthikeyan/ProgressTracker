package com.forge.hypertrophy.ui.screens.dashboard

import com.forge.hypertrophy.data.entity.BiometricsEntity
import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.repository.RoomBaselineRepository
import com.forge.hypertrophy.data.repository.RoomBiometricsRepository
import com.forge.hypertrophy.data.repository.RoomExerciseRepository
import com.forge.hypertrophy.data.repository.RoomProgramRepository
import com.forge.hypertrophy.data.repository.RoomRoutineRepository
import com.forge.hypertrophy.data.repository.RoomSessionRepository
import com.forge.hypertrophy.data.repository.RoomSkillRepository
import com.forge.hypertrophy.data.repository.ScheduleCursorRepository
import com.forge.hypertrophy.data.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.ui.screens.routine.ViewModelDaoTest
import com.forge.hypertrophy.ui.screens.routine.awaitUntil
import com.forge.hypertrophy.widget.TodayWidgetRefresher
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelTest : ViewModelDaoTest() {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)

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
        db.routineDao().insertDay(day(programId, "push", 0))
        db.routineDao().insertDay(day(programId, "pull", 1))
        db.programDao().setActive(programId)
        val viewModel = dashboard()
        awaitUntil { viewModel.uiState.value.today?.label == "push" }

        viewModel.onEvent(DashboardEvent.SkipToNext)

        awaitUntil { viewModel.uiState.value.today?.label == "pull" }
        assertEquals(1, db.programDao().getById(programId)!!.rollingSequence)
    }

    @Test
    fun swapWithTomorrowRefreshesTheTodayCard() = runBlocking {
        val programId = db.programDao().insert(
            ProgramEntity(
                name = "fixed",
                scheduleMode = ScheduleMode.FIXED,
                rollingSequence = 0,
                deloadActive = false,
                deloadStartedOn = null,
            ),
        )
        db.routineDao().insertDay(day(programId, "push", 0, weekday = 1))
        db.routineDao().insertDay(day(programId, "pull", 1, weekday = 2))
        db.programDao().setActive(programId)
        val viewModel = dashboard()
        awaitUntil { viewModel.uiState.value.today?.label == "push" }

        viewModel.onEvent(DashboardEvent.SwapWithTomorrow)

        awaitUntil { viewModel.uiState.value.today?.label == "pull" }
    }

    @Test
    fun takeRestNowRefreshesTheTodayCard() = runBlocking {
        val programId = rollingProgram()
        db.routineDao().insertDay(day(programId, "push", 0))
        db.routineDao().insertDay(day(programId, "rest", 1, rest = true))
        db.routineDao().insertDay(day(programId, "pull", 2))
        db.programDao().setActive(programId)
        val viewModel = dashboard()
        awaitUntil { viewModel.uiState.value.today?.label == "push" }

        viewModel.onEvent(DashboardEvent.TakeRestNow)

        awaitUntil { viewModel.uiState.value.today?.label == "rest" }
        val logged = db.sessionDao().completedDays()
        assertEquals(listOf(SessionKind.REST), logged.map { it.kind })
    }

    @Test
    fun weighInKeepsAGapOutOfTheAverage() = runBlocking {
        val early = LocalDate.of(2026, 9, 25)
        db.biometricsDao().upsert(BiometricsEntity(date = early, bodyWeightKg = 70.0, bodyFatPercent = null))
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
        assertEquals(80.0, db.biometricsDao().get(LocalDate.of(2026, 10, 5))!!.bodyWeightKg!!, 0.001)
    }

    private fun rollingProgram(): Long = runBlocking {
        db.programDao().insert(
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
        val preferences = FakePreferences()
        return track(
            DashboardViewModel(
                programs = RoomProgramRepository(db.programDao()),
                routines = RoomRoutineRepository(db.routineDao()),
                sessions = RoomSessionRepository(db.sessionDao()),
                exercises = RoomExerciseRepository(
                    db.exerciseDao(),
                    db.routineDao(),
                    db.sessionDao(),
                    db.mediaDao(),
                    clock,
                ),
                skills = RoomSkillRepository(
                    db.skillDao(),
                    db.exerciseDao(),
                    db.routineDao(),
                    db.sessionDao(),
                    clock,
                ),
                biometrics = RoomBiometricsRepository(db.biometricsDao()),
                preferences = preferences,
                cursor = MemoryCursor(),
                clock = clock,
                widget = widget,
                baselines = RoomBaselineRepository(db.baselineDao()),
            ),
        )
    }

    private val widget = CountingRefresher()

    @Test
    fun scheduleCatchUpRefreshesTheWidget() = runBlocking {
        val programId = rollingProgram()
        db.routineDao().insertDay(day(programId, "push", 0))
        db.programDao().setActive(programId)
        val viewModel = dashboard()
        awaitUntil { viewModel.uiState.value.today?.label == "push" }
        awaitUntil { widget.count > 0 }
        val before = widget.count

        viewModel.onEvent(DashboardEvent.SkipToNext)

        awaitUntil { widget.count > before }
    }
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

    override val lastReconciledDate = reconciled
    override val plateInventoryKg = plates
    override val transitionRestSeconds = rest
    override val activeTimerEndElapsedRealtime: Flow<Long?> = end

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

    private val defaultRest = MutableStateFlow(90)
    override val defaultRestSeconds: Flow<Int> = defaultRest

    override suspend fun setDefaultRestSeconds(seconds: Int) {
        defaultRest.value = seconds
    }
}
