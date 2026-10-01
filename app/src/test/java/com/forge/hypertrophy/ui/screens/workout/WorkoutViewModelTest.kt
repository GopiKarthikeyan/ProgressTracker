package com.forge.hypertrophy.ui.screens.workout

import androidx.lifecycle.SavedStateHandle
import com.forge.hypertrophy.data.dao.DaoFixture
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SlotAlternativeEntity
import com.forge.hypertrophy.data.repository.RoomExerciseRepository
import com.forge.hypertrophy.data.repository.RoomProgramRepository
import com.forge.hypertrophy.data.repository.RoomRoutineRepository
import com.forge.hypertrophy.data.repository.RoomSessionRepository
import com.forge.hypertrophy.data.repository.RoomSkillRepository
import com.forge.hypertrophy.data.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.model.ChecklistPhase
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.engine.ReadinessAdvice
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.workout.ElapsedRealtimeClock
import com.forge.hypertrophy.domain.workout.TimerCommand
import com.forge.hypertrophy.domain.workout.TimerSnapshot
import com.forge.hypertrophy.domain.workout.TimerSpec
import com.forge.hypertrophy.domain.workout.WorkoutPosition
import com.forge.hypertrophy.domain.workout.WorkoutTimer
import com.forge.hypertrophy.domain.workout.projectTimer
import com.forge.hypertrophy.ui.screens.routine.ViewModelDaoTest
import com.forge.hypertrophy.ui.screens.routine.awaitUntil
import com.forge.hypertrophy.data.diagnostics.Breadcrumbs
import com.forge.hypertrophy.data.repository.RoomBaselineRepository
import com.forge.hypertrophy.widget.TodayWidgetRefresher
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutViewModelTest : ViewModelDaoTest() {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun everySetIsWrittenImmediatelyAndReadinessFeedsTheAdvisor() = runBlocking {
        val dayId = seedDay(optional = false)
        val preferences = FakePreferences()
        val elapsed = FakeElapsed()
        val viewModel = viewModel(preferences, FakeWorkoutTimer(), elapsed)
        viewModel.onEvent(WorkoutEvent.Start(dayId))
        awaitUntil { viewModel.uiState.value.sessionId != null }
        val sessionId = viewModel.uiState.value.sessionId!!
        assertEquals(SessionStatus.PLANNED, db.sessionDao().get(sessionId)!!.status)

        viewModel.onEvent(WorkoutEvent.SubmitReadiness(1, 1, 1))
        awaitUntil { viewModel.uiState.value.advice == ReadinessAdvice.SHORT_ON_TIME_HOLD_WEIGHTS }
        viewModel.onEvent(WorkoutEvent.CheckOff(db.routineDao().checklistForDays(listOf(dayId)).single().id))
        awaitUntil { viewModel.uiState.value.position is WorkoutPosition.WorkingSet }

        viewModel.onEvent(WorkoutEvent.Primary(EntryMethod.SCREEN))
        val slotId = db.sessionDao().allSlots().single().id
        awaitUntil { sets(slotId).size == 1 && viewModel.uiState.value.position is WorkoutPosition.Resting }
        assertEquals(5, sets(slotId).single().reps)
        assertTrue(viewModel.uiState.value.position is WorkoutPosition.Resting)

        viewModel.onEvent(WorkoutEvent.CompleteWorkout)
        awaitUntil { viewModel.uiState.value.position is WorkoutPosition.Summary }
        viewModel.onEvent(WorkoutEvent.Tick(elapsed.now))
        awaitUntil { viewModel.uiState.value.summary != null }
        assertTrue(viewModel.uiState.value.position is WorkoutPosition.Summary)
        assertEquals(SessionStatus.COMPLETED, db.sessionDao().get(sessionId)!!.status)
        assertEquals(1, widget.count)
    }

    @Test
    fun restoreResumesALiveRestAndSkipsAFinishedOne() = runBlocking {
        val dayId = seedDay(optional = false)
        val preferences = FakePreferences()
        val elapsed = FakeElapsed()
        val firstTimer = FakeWorkoutTimer()
        val first = viewModel(preferences, firstTimer, elapsed)
        first.onEvent(WorkoutEvent.Start(dayId))
        awaitUntil { first.uiState.value.sessionId != null }
        first.onEvent(WorkoutEvent.SkipReadiness)
        awaitUntil { first.uiState.value.position is WorkoutPosition.Prep }
        val itemId = db.routineDao().checklistForDays(listOf(dayId)).single().id
        first.onEvent(WorkoutEvent.CheckOff(itemId))
        awaitUntil { first.uiState.value.position is WorkoutPosition.WorkingSet }
        first.onEvent(WorkoutEvent.Primary(EntryMethod.SCREEN))
        awaitUntil { first.uiState.value.position is WorkoutPosition.Resting }
        val sessionId = first.uiState.value.sessionId!!
        val end = firstTimer.spec!!.persistedEnd()!!
        preferences.end.value = end

        elapsed.now = end - 10_000
        val live = viewModel(preferences, FakeWorkoutTimer(), elapsed, sessionId)
        awaitUntil { live.uiState.value.position is WorkoutPosition.Resting }
        assertEquals(end, live.let { preferences.end.value })

        elapsed.now = firstTimer.spec!!.overtimeEndElapsedRealtime!! + 1
        val done = viewModel(preferences, FakeWorkoutTimer(), elapsed, sessionId)
        awaitUntil { done.uiState.value.position is WorkoutPosition.Summary }
    }

    @Test
    fun hardwareTriggerDebouncesAndUndoDeletesTheSet() = runBlocking {
        val dayId = seedDay(optional = false, sets = 2)
        val elapsed = FakeElapsed()
        val viewModel = viewModel(FakePreferences(), FakeWorkoutTimer(), elapsed)
        viewModel.onEvent(WorkoutEvent.Start(dayId))
        awaitUntil { viewModel.uiState.value.sessionId != null }
        viewModel.onEvent(WorkoutEvent.SkipReadiness)
        awaitUntil { viewModel.uiState.value.position is WorkoutPosition.Prep }
        val itemId = db.routineDao().checklistForDays(listOf(dayId)).single().id
        viewModel.onEvent(WorkoutEvent.CheckOff(itemId))
        awaitUntil { viewModel.uiState.value.position is WorkoutPosition.WorkingSet }

        viewModel.onEvent(WorkoutEvent.Primary(EntryMethod.HARDWARE_KEY))
        awaitUntil { viewModel.uiState.value.position is WorkoutPosition.Resting }
        val slotId = db.sessionDao().allSlots().single().id
        viewModel.onEvent(WorkoutEvent.Primary(EntryMethod.HARDWARE_KEY))
        awaitUntil { viewModel.uiState.value.undoUntilElapsedRealtime != null }
        assertEquals(1, sets(slotId).size)
        assertTrue(viewModel.uiState.value.position is WorkoutPosition.Resting)

        viewModel.onEvent(WorkoutEvent.Undo)
        awaitUntil { sets(slotId).isEmpty() }
        assertTrue(viewModel.uiState.value.position is WorkoutPosition.WorkingSet)

        elapsed.now += 5_001
        viewModel.onEvent(WorkoutEvent.Primary(EntryMethod.HARDWARE_KEY))
        awaitUntil { sets(slotId).size == 1 }
        elapsed.now += 5_001
        viewModel.onEvent(WorkoutEvent.Undo)
        awaitUntil { viewModel.uiState.value.undoUntilElapsedRealtime == null }
        assertEquals(1, sets(slotId).size)
    }

    @Test
    fun shortOnTimeSkipsTheOptionalSlotAndReorderDoesNotTouchTheRoutine() = runBlocking {
        val dayId = seedDay(optional = true)
        val routineOrders = db.routineDao().slots(dayId).map { it.id to it.sortOrder }
        val viewModel = viewModel(FakePreferences(), FakeWorkoutTimer(), FakeElapsed())
        viewModel.onEvent(WorkoutEvent.Start(dayId))
        awaitUntil { viewModel.uiState.value.sessionId != null }
        viewModel.onEvent(WorkoutEvent.ToggleShortOnTime)
        awaitUntil {
            db.sessionDao().allSlots().any { it.skipped && it.skipReason == SHORT_ON_TIME_REASON }
        }
        assertEquals(routineOrders, db.routineDao().slots(dayId).map { it.id to it.sortOrder })

        val before = db.sessionDao().allSlots().sortedBy { it.prescriptionSnapshot.sortOrder }.map { it.id }
        viewModel.onEvent(WorkoutEvent.MoveSlot(0, 1))
        awaitUntil {
            db.sessionDao().allSlots().sortedBy { it.prescriptionSnapshot.sortOrder }.map { it.id } ==
                listOf(before[1], before[0])
        }
        val after = db.sessionDao().allSlots().sortedBy { it.prescriptionSnapshot.sortOrder }
        assertEquals(listOf(0, 1), after.map { it.prescriptionSnapshot.sortOrder })
        assertEquals(routineOrders, db.routineDao().slots(dayId).map { it.id to it.sortOrder })
    }

    @Test
    fun choosingAnAlternativeDoesNotRewriteTheSnapshotExercise() = runBlocking {
        val dayId = seedDay(optional = false)
        val other = DaoFixture(db).exercise("other")
        val routineSlot = db.routineDao().slots(dayId).single()
        db.routineDao().insertAlternative(SlotAlternativeEntity(slotId = routineSlot.id, exerciseId = other))
        val viewModel = viewModel(FakePreferences(), FakeWorkoutTimer(), FakeElapsed())
        viewModel.onEvent(WorkoutEvent.Start(dayId))
        awaitUntil { viewModel.uiState.value.sessionId != null }
        viewModel.onEvent(WorkoutEvent.SkipReadiness)
        awaitUntil { viewModel.uiState.value.position is WorkoutPosition.Prep }
        val itemId = db.routineDao().checklistForDays(listOf(dayId)).single().id
        viewModel.onEvent(WorkoutEvent.CheckOff(itemId))
        awaitUntil { viewModel.uiState.value.position is WorkoutPosition.WorkingSet }

        viewModel.onEvent(WorkoutEvent.ChooseAlternative(other))
        awaitUntil { db.sessionDao().allSlots().single().chosenAlternativeExerciseId == other }
        val stored = db.sessionDao().allSlots().single()
        assertEquals(routineSlot.exerciseId, stored.prescriptionSnapshot.exerciseId)
        assertNotEquals(other, stored.prescriptionSnapshot.exerciseId)
        assertEquals("other", (viewModel.uiState.value.position as WorkoutPosition.WorkingSet).slot.exerciseName)
    }

    private suspend fun sets(slotId: Long) = RoomSessionRepository(db.sessionDao()).sets(slotId)

    private suspend fun seedDay(optional: Boolean, sets: Int = 1): Long {
        val fixture = DaoFixture(db)
        val programId = fixture.program()
        val dayId = fixture.day(programId)
        val exerciseId = fixture.exercise("press")
        db.routineDao().insertChecklist(
            com.forge.hypertrophy.data.entity.ChecklistItemEntity(
                dayId = dayId,
                phase = ChecklistPhase.PREP,
                text = "row",
                reps = null,
                seconds = null,
            ),
        )
        insertRoutineSlot(dayId, exerciseId, sort = 0, sets = sets, optional = false)
        if (optional) insertRoutineSlot(dayId, exerciseId, sort = 1, sets = 1, optional = true)
        return dayId
    }

    private suspend fun insertRoutineSlot(dayId: Long, exerciseId: Long, sort: Int, sets: Int, optional: Boolean) {
        db.routineDao().insertSlot(
            RoutineSlotEntity(
                dayId = dayId,
                exerciseId = exerciseId,
                category = SlotCategory.COMPOUND,
                sortOrder = sort,
                supersetGroup = null,
                metricType = MetricType.WEIGHT_REPS,
                setsMin = sets,
                setsMax = sets,
                repsLow = 5,
                repsHigh = 8,
                isAmrap = false,
                holdTargetSec = null,
                blockDurationSec = null,
                restMinSec = 60,
                restMaxSec = 90,
                restAsNeeded = false,
                isOptional = optional,
                skipReasonLabel = null,
                targetSkillStepId = null,
                progressionRule = ProgressionRule.DOUBLE,
                incrementOverrideKg = null,
            ),
        )
    }

    private fun viewModel(
        preferences: FakePreferences,
        timer: WorkoutTimer,
        elapsed: FakeElapsed,
        sessionId: Long = 0L,
    ) = track(
        WorkoutViewModel(
            SavedStateHandle(mapOf("sessionId" to sessionId)),
            RoomSessionRepository(db.sessionDao()),
            RoomRoutineRepository(db.routineDao()),
            RoomExerciseRepository(db.exerciseDao(), db.routineDao(), db.sessionDao(), db.mediaDao(), clock),
            RoomProgramRepository(db.programDao()),
            RoomSkillRepository(db.skillDao(), db.exerciseDao(), db.routineDao(), db.sessionDao(), clock),
            preferences,
            timer,
            clock,
            elapsed,
            widget,
            RoomBaselineRepository(db.baselineDao()),
            Breadcrumbs(),
        ),
    )

    private val widget = CountingRefresher()
}

private class CountingRefresher : TodayWidgetRefresher {
    var count = 0
    override suspend fun refresh() {
        count += 1
    }
}

private class FakeElapsed : ElapsedRealtimeClock {
    var now: Long = 1_000_000
    override fun elapsedRealtime(): Long = now
}

private class FakeWorkoutTimer : WorkoutTimer {
    private val _snapshot = MutableStateFlow(TimerSnapshot.Idle)
    override val snapshot: StateFlow<TimerSnapshot> = _snapshot.asStateFlow()
    private val _commands = MutableSharedFlow<TimerCommand>(extraBufferCapacity = 4)
    override val commands = _commands.asSharedFlow()
    var spec: TimerSpec? = null

    override suspend fun start(spec: TimerSpec) {
        this.spec = spec
        refresh(spec.anchorElapsedRealtime)
    }

    override suspend fun adjust(deltaMillis: Long) {
        spec = spec?.adjust(deltaMillis)
        refresh(spec?.anchorElapsedRealtime ?: 0)
    }

    override suspend fun stop() {
        spec = null
        _snapshot.value = TimerSnapshot.Idle
    }

    override fun refresh(nowElapsedRealtime: Long) {
        _snapshot.value = projectTimer(spec, nowElapsedRealtime)
    }
}

private class FakePreferences : TrainingPreferencesRepository {
    val end = MutableStateFlow<Long?>(null)
    private val plates = MutableStateFlow<List<Double>>(listOf(20.0, 10.0))
    private val rest = MutableStateFlow(120)
    private val reconciled = MutableStateFlow<LocalDate?>(null)

    override val lastReconciledDate = reconciled
    override val plateInventoryKg = plates
    override val transitionRestSeconds: StateFlow<Int> = rest
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
