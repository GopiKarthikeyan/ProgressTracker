package com.forge.hypertrophy.ui.screens.workout

import androidx.lifecycle.SavedStateHandle
import com.forge.hypertrophy.data.dao.CompletedSessionDay
import com.forge.hypertrophy.data.dao.CompletedSetRow
import com.forge.hypertrophy.data.diagnostics.Breadcrumbs
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
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.domain.engine.ReadinessAdvice
import com.forge.hypertrophy.domain.model.ChecklistPhase
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.usecase.CompleteWorkoutUseCase
import com.forge.hypertrophy.domain.usecase.LoadWorkoutUseCase
import com.forge.hypertrophy.domain.usecase.LogSetUseCase
import com.forge.hypertrophy.domain.usecase.StartWorkoutUseCase
import com.forge.hypertrophy.domain.usecase.WorkoutInteractors
import com.forge.hypertrophy.domain.repository.ExerciseDetails
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.repository.WorkoutRepository
import com.forge.hypertrophy.domain.repository.WorkoutSessionState
import com.forge.hypertrophy.domain.usecase.ToggleShortOnTimeUseCase
import com.forge.hypertrophy.domain.workout.ElapsedRealtimeClock
import com.forge.hypertrophy.domain.workout.RecordedSet
import com.forge.hypertrophy.domain.workout.WorkoutSlot
import com.forge.hypertrophy.domain.workout.TimerCommand
import com.forge.hypertrophy.domain.workout.TimerSnapshot
import com.forge.hypertrophy.domain.workout.TimerSpec
import com.forge.hypertrophy.domain.workout.WorkoutPosition
import com.forge.hypertrophy.domain.workout.WorkoutTimer
import com.forge.hypertrophy.domain.workout.projectTimer
import com.forge.hypertrophy.widget.TodayWidgetRefresher
import app.cash.turbine.test
import kotlin.time.Duration.Companion.seconds
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC)
    private val sessionRepo = FakeSessionRepository()
    private val exerciseRepo = FakeExerciseRepository()
    private val routineRepo = FakeRoutineRepository()
    private val programRepo = FakeProgramRepository()
    private val baselineRepo = FakeBaselineRepository()
    private val skillRepo = FakeSkillRepository()
    private val widget = CountingRefresher()

    @Test
    fun everySetIsWrittenImmediatelyAndReadinessFeedsTheAdvisor() = workoutTest {
        workoutRobot {
            startSession()
            assertSessionStatus(SessionStatus.PLANNED)
            submitReadiness(1, 1, 1)
            checkOffPrep()
            logSet()
            assertSetLogged(reps = 5)
            assertState<WorkoutPosition.Resting>()
            completeWorkout()
            tick()
            assertState<WorkoutPosition.Summary>()
            assertSessionStatus(SessionStatus.COMPLETED)
            assertWidgetRefreshed()
        }
    }

    @Test
    fun restoreResumesALiveRestAndSkipsAFinishedOne() = workoutTest {
        workoutRobot {
            startSession()
            skipReadiness()
            checkOffPrep()
            logSet()
            val end = captureRestEnd()
            restore(millisBeforeEnd = 10_000)
            assertRestEnd(end)
            restoreAfterOvertime()
        }
    }

    @Test
    fun hardwareTriggerDebouncesAndUndoDeletesTheSet() = workoutTest {
        workoutRobot(sets = 2) {
            startSession()
            skipReadiness()
            checkOffPrep()
            logSet(EntryMethod.HARDWARE_KEY)
            logSet(EntryMethod.HARDWARE_KEY)
            assertSetLogged(count = 1)
            assertState<WorkoutPosition.Resting>()
            undo()
            assertState<WorkoutPosition.WorkingSet>()
            advanceRest(6)
            logSet(EntryMethod.HARDWARE_KEY)
            advanceRest(6)
            undo()
            assertSetLogged(count = 1)
        }
    }

    @Test
    fun shortOnTimeSkipsTheOptionalSlotAndReorderDoesNotTouchTheRoutine() = workoutTest {
        workoutRobot(optional = true) {
            val routineOrders = routineOrders()
            startSession()
            toggleShortOnTime()
            assertRoutineUntouched(routineOrders)
            moveFirstSlotDown()
            assertSlotsReordered()
            assertRoutineUntouched(routineOrders)
        }
    }

    @Test
    fun shortSetRestsForRestMaxAndSurvivesRestore() = workoutTest {
        workoutRobot(sets = 2) {
            startSession()
            skipReadiness()
            checkOffPrep()
            adjustReps(-1)
            logSet()
            assertSetLogged(reps = 4)
            val end = captureRestEnd()
            restore(millisBeforeEnd = 10_000)
            assertRestEnd(end)
            assertRestoredCountdownTargets(90)
        }
    }

    @Test
    fun choosingAnAlternativeDoesNotRewriteTheSnapshotExercise() = workoutTest {
        workoutRobot {
            val other = addAlternative("other")
            startSession()
            skipReadiness()
            checkOffPrep()
            chooseAlternative(other)
            assertSnapshotPreserved(other, "other")
        }
    }

    private fun workoutTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun TestScope.workoutRobot(
        optional: Boolean = false,
        sets: Int = 1,
        block: suspend WorkoutRobot.() -> Unit,
    ) {
        WorkoutRobot(seedDay(optional, sets), this).block()
    }

    private inner class WorkoutRobot(
        private val dayId: Long,
        private val scope: TestScope,
    ) {
        private val preferences = FakePreferences()
        private val elapsed = SchedulerElapsedClock(scope)
        private var timer = FakeWorkoutTimer()
        private var viewModel = newViewModel()
        private var sessionId: Long = 0
        private var routineExerciseId: Long = 0
        private var restEnd: Long = 0
        private var restStartedElapsed: Long = 0
        private var overtimeEnd: Long = 0

        suspend fun startSession() {
            val state = after(WorkoutEvent.Start(dayId)) { it.sessionId != null }
            sessionId = state.sessionId!!
        }

        suspend fun submitReadiness(sleep: Int, soreness: Int, energy: Int) {
            after(WorkoutEvent.SubmitReadiness(sleep, soreness, energy)) {
                it.advice == ReadinessAdvice.SHORT_ON_TIME_HOLD_WEIGHTS
            }
        }

        suspend fun skipReadiness() {
            after(WorkoutEvent.SkipReadiness) { it.position is WorkoutPosition.Prep }
        }

        suspend fun adjustReps(delta: Int) {
            after(WorkoutEvent.Adjust(repDelta = delta)) { state ->
                val working = state.position as? WorkoutPosition.WorkingSet
                working != null && working.suggestion.reps == 5 + delta
            }
        }

        suspend fun checkOffPrep() {
            val itemId = routineRepo.checklistItems.values.first().id
            after(WorkoutEvent.CheckOff(itemId)) { it.position is WorkoutPosition.WorkingSet }
        }

        suspend fun logSet(method: EntryMethod = EntryMethod.SCREEN) {
            val before = setsForCurrentSlot().size
            after(WorkoutEvent.Primary(method)) { state ->
                val count = setsForCurrentSlot().size
                when {
                    count > before && state.position is WorkoutPosition.Resting -> true
                    method == EntryMethod.HARDWARE_KEY &&
                        count == before &&
                        state.position is WorkoutPosition.Resting &&
                        state.undoUntilElapsedRealtime != null -> true
                    else -> false
                }
            }
        }

        /** Moves virtual time forward. Six seconds is past the 5s undo window. */
        fun advanceRest(seconds: Long) {
            scope.advanceTimeBy(seconds * 1_000)
            scope.runCurrent()
        }

        suspend fun undo() {
            after(WorkoutEvent.Undo) { state ->
                setsForCurrentSlot().isEmpty() || state.undoUntilElapsedRealtime == null
            }
        }

        suspend fun completeWorkout() {
            after(WorkoutEvent.CompleteWorkout) { it.position is WorkoutPosition.Summary }
        }

        suspend fun tick() {
            after(WorkoutEvent.Tick(elapsed.elapsedRealtime())) { it.summary != null }
        }

        suspend fun toggleShortOnTime() {
            after(WorkoutEvent.ToggleShortOnTime) {
                sessionRepo.slots.values.any { it.skipped && it.skipReason == "short on time" }
            }
        }

        suspend fun moveFirstSlotDown() {
            val before = sessionRepo.slots.values.sortedBy { it.prescriptionSnapshot.sortOrder }.map { it.id }
            after(WorkoutEvent.MoveSlot(0, 1)) {
                sessionRepo.slots.values.sortedBy { it.prescriptionSnapshot.sortOrder }.map { it.id } ==
                    listOf(before[1], before[0])
            }
        }

        suspend fun addAlternative(name: String): Long {
            val other = exerciseRepo.insert(
                ExerciseEntity(
                    name = name,
                    equipment = Equipment.BARBELL,
                    barWeightKg = 20.0,
                    loadIncrementKg = 2.5,
                    isUnilateral = false,
                    skillId = null,
                    primaryMuscleGroups = emptyList(),
                    secondaryMuscleGroups = emptyList(),
                    setupNotes = "",
                    archivedAt = null,
                ),
            )
            val routineSlot = routineRepo.slots.values.first { it.dayId == dayId }
            routineExerciseId = routineSlot.exerciseId
            routineRepo.insertAlternative(SlotAlternativeEntity(slotId = routineSlot.id, exerciseId = other))
            return other
        }

        suspend fun chooseAlternative(exerciseId: Long) {
            after(WorkoutEvent.ChooseAlternative(exerciseId)) {
                sessionRepo.slots.values.first().chosenAlternativeExerciseId == exerciseId
            }
        }

        fun captureRestEnd(): Long {
            restStartedElapsed = elapsed.elapsedRealtime()
            val end = timer.spec!!.persistedEnd()!!
            preferences.end.value = end
            restEnd = end
            overtimeEnd = timer.spec!!.overtimeEndElapsedRealtime!!
            return end
        }

        suspend fun restore(millisBeforeEnd: Long) {
            seekElapsed(restEnd - millisBeforeEnd)
            reopen()
            scope.runCurrent()
            awaitCollected { it.position is WorkoutPosition.Resting }
        }

        suspend fun restoreAfterOvertime() {
            seekElapsed(overtimeEnd + 1)
            reopen()
            scope.runCurrent()
            awaitCollected { it.position is WorkoutPosition.Summary }
        }

        fun routineOrders(): List<Pair<Long, Int>> =
            routineRepo.slots.values.filter { it.dayId == dayId }.map { it.id to it.sortOrder }

        suspend fun assertSessionStatus(status: SessionStatus) {
            assertEquals(status, sessionRepo.get(sessionId)!!.status)
        }

        fun assertWidgetRefreshed() {
            assertEquals(1, widget.count)
        }

        fun assertRestEnd(end: Long) {
            assertEquals(end, preferences.end.value)
        }

        fun assertRestoredCountdownTargets(seconds: Int) {
            val spec = timer.spec!!
            assertEquals(restEnd, spec.anchorElapsedRealtime)
            assertEquals(spec.anchorElapsedRealtime, spec.overtimeEndElapsedRealtime)
            assertEquals(seconds * 1_000L, restEnd - restStartedElapsed)
        }

        fun assertRoutineUntouched(expected: List<Pair<Long, Int>>) {
            assertEquals(expected, routineOrders())
        }

        fun assertSlotsReordered() {
            val ordered = sessionRepo.slots.values.sortedBy { it.prescriptionSnapshot.sortOrder }
            assertEquals(listOf(0, 1), ordered.map { it.prescriptionSnapshot.sortOrder })
        }

        fun assertSetLogged(reps: Int? = null, count: Int? = null) {
            val sets = setsForCurrentSlot()
            if (count != null) assertEquals(count, sets.size)
            if (reps != null) assertEquals(reps, sets.single().reps)
        }

        suspend fun assertSnapshotPreserved(otherId: Long, name: String) {
            val stored = sessionRepo.slots.values.first()
            assertEquals(routineExerciseId, stored.prescriptionSnapshot.exerciseId)
            assertNotEquals(otherId, stored.prescriptionSnapshot.exerciseId)
            val state = awaitCollected { it.position is WorkoutPosition.WorkingSet }
            assertEquals(name, (state.position as WorkoutPosition.WorkingSet).slot.exerciseName)
        }

        suspend inline fun <reified T : WorkoutPosition> assertState() {
            val state = awaitCollected { it.position is T }
            assertTrue(state.position is T)
        }

        private fun setsForCurrentSlot(): List<SetEntryEntity> {
            val slotId = sessionRepo.slots.values.first().id
            return sessionRepo.sets.values.filter { it.sessionSlotId == slotId }
        }

        private fun reopen() {
            timer = FakeWorkoutTimer()
            viewModel = newViewModel(sessionId)
        }

        private fun newViewModel(existingSessionId: Long = 0L) = this@WorkoutViewModelTest.createViewModel(
            preferences,
            timer,
            elapsed,
            existingSessionId,
        )

        private fun seekElapsed(target: Long) {
            val delta = target - elapsed.elapsedRealtime()
            check(delta >= 0L) { "virtual time cannot move backwards" }
            if (delta > 0L) scope.advanceTimeBy(delta)
            scope.runCurrent()
        }

        private suspend fun after(
            event: WorkoutEvent,
            predicate: (WorkoutUiState) -> Boolean,
        ): WorkoutUiState {
            viewModel.onEvent(event)
            scope.runCurrent()
            return awaitCollected(predicate)
        }

        private suspend fun awaitCollected(predicate: (WorkoutUiState) -> Boolean): WorkoutUiState {
            lateinit var matched: WorkoutUiState
            viewModel.uiState.test(timeout = 5.seconds) {
                var latest = awaitItem()
                while (!predicate(latest)) {
                    latest = awaitItem()
                }
                matched = latest
                cancelAndIgnoreRemainingEvents()
            }
            return matched
        }
    }

    private suspend fun seedDay(optional: Boolean, sets: Int = 1): Long {
        val programId = programRepo.insert(ProgramEntity(name = "program", isActive = true, scheduleMode = ScheduleMode.ROLLING, rollingSequence = 0, deloadActive = false, deloadStartedOn = null))
        val dayId = routineRepo.insertDay(RoutineDayEntity(programId = programId, label = "day", sequenceIndex = 0, dayOfWeek = null, isRest = false))
        val exerciseId = exerciseRepo.insert(ExerciseEntity(name = "press", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
        routineRepo.insertChecklist(
            ChecklistItemEntity(
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
        routineRepo.insertSlot(
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

    private fun createViewModel(
        preferences: TrainingPreferencesRepository,
        timer: WorkoutTimer,
        elapsed: ElapsedRealtimeClock,
        sessionId: Long = 0L,
    ) = WorkoutViewModel(
        SavedStateHandle(mapOf("sessionId" to sessionId)),
        FakeWorkoutRepository(sessionRepo, exerciseRepo),
        preferences,
        timer,
        clock,
        elapsed,
        widget,
        Breadcrumbs(),
        LoadWorkoutUseCase(
            sessionRepo,
            routineRepo,
            exerciseRepo,
            preferences,
            baselineRepo
        ),
        WorkoutInteractors(
            start = StartWorkoutUseCase(
                sessionRepo,
                routineRepo,
                programRepo,
                clock
            ),
            logSet = LogSetUseCase(
                sessionRepo,
                clock
            ),
            complete = CompleteWorkoutUseCase(
                sessionRepo,
                exerciseRepo,
                skillRepo,
                baselineRepo,
                clock
            ),
        ),
        ToggleShortOnTimeUseCase(
            sessionRepo
        )
    )
}

private class CountingRefresher : TodayWidgetRefresher {
    var count = 0
    override suspend fun refresh() {
        count += 1
    }
}

private class SchedulerElapsedClock(
    private val scope: TestScope,
) : ElapsedRealtimeClock {
    override fun elapsedRealtime(): Long = BASE_ELAPSED_REALTIME + scope.testScheduler.currentTime

    private companion object {
        const val BASE_ELAPSED_REALTIME = 1_000_000L
    }
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

private class FakeWorkoutRepository(
    private val sessions: FakeSessionRepository,
    private val exercises: FakeExerciseRepository,
) : WorkoutRepository {
    override suspend fun findSession(id: Long): WorkoutSessionState? {
        val session = sessions.get(id) ?: return null
        return WorkoutSessionState(status = session.status, isShortOnTime = session.isShortOnTime)
    }

    override suspend fun beginSession(
        id: Long,
        sleep: Int?,
        soreness: Int?,
        energy: Int?,
        startedAt: Instant,
    ): Boolean {
        val session = sessions.get(id) ?: return false
        sessions.update(
            session.copy(
                status = SessionStatus.IN_PROGRESS,
                readinessSleep = sleep,
                readinessSoreness = soreness,
                readinessEnergy = energy,
                startedAt = startedAt,
            ),
        )
        return true
    }

    override suspend fun updateRecordedSet(sessionSlotId: Long, set: RecordedSet, loggedAt: Instant) {
        sessions.updateSet(
            SetEntryEntity(
                id = set.id,
                sessionSlotId = sessionSlotId,
                setNumber = set.setNumber,
                side = set.side,
                setType = SetType.WORKING,
                weightKg = set.weightKg,
                reps = set.reps,
                holdSec = set.holdSec,
                rpe = set.rpe,
                jointFlags = set.jointFlags,
                entryMethod = set.entryMethod,
                loggedAt = loggedAt,
            ),
        )
    }

    override suspend fun deleteSet(id: Long) {
        sessions.deleteSet(id)
    }

    override suspend fun persistSlot(sessionId: Long, slot: WorkoutSlot) {
        val entity = sessions.observeSlots(sessionId).first().firstOrNull { it.id == slot.sessionSlotId } ?: return
        sessions.updateSlot(
            entity.copy(
                prescriptionSnapshot = entity.prescriptionSnapshot.copy(
                    sortOrder = slot.sortOrder,
                    setsMin = slot.prescription.setsMin,
                    setsMax = slot.prescription.setsMax,
                ),
                chosenAlternativeExerciseId = slot.chosenAlternativeExerciseId,
                skipped = slot.skipped,
                skipReason = slot.skipReason,
                formConfirmed = slot.formConfirmed,
            ),
        )
    }

    override suspend fun findExercise(id: Long): ExerciseDetails? {
        val exercise = exercises.get(id) ?: return null
        return ExerciseDetails(
            name = exercise.name,
            setupNotes = exercise.setupNotes,
            isUnilateral = exercise.isUnilateral,
        )
    }

    override suspend fun updateSetupNotes(exerciseId: Long, notes: String) {
        val exercise = exercises.get(exerciseId) ?: return
        exercises.update(exercise.copy(setupNotes = notes))
    }
}

private class FakePreferences : TrainingPreferencesRepository {
    val end = MutableStateFlow<Long?>(null)
    private val plates = MutableStateFlow<List<Double>>(listOf(20.0, 10.0))
    private val rest = MutableStateFlow(120)
    private val reconciled = MutableStateFlow<LocalDate?>(null)
    private val defaultRest = MutableStateFlow(90)

    override val lastReconciledDate = reconciled
    override val plateInventoryKg = plates
    override val transitionRestSeconds: StateFlow<Int> = rest
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

private class FakeSessionRepository : SessionRepository {
    val sessions = mutableMapOf<Long, WorkoutSessionEntity>()
    val slots = mutableMapOf<Long, SessionSlotEntity>()
    val sets = mutableMapOf<Long, SetEntryEntity>()

    private val sessionsFlow = MutableStateFlow(emptyList<WorkoutSessionEntity>())
    private val slotsFlow = MutableStateFlow(emptyList<SessionSlotEntity>())
    private val setsFlow = MutableStateFlow(emptyList<SetEntryEntity>())

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
    override fun observeSlots(sessionId: Long): Flow<List<SessionSlotEntity>> = slotsFlow.map { it.filter { s -> s.sessionId == sessionId } }
    override suspend fun insertSlot(slot: SessionSlotEntity): Long {
        val id = (slots.keys.maxOrNull() ?: 0L) + 1
        val saved = slot.copy(id = id)
        slots[id] = saved
        slotsFlow.value = slots.values.toList()
        return id
    }
    override suspend fun updateSlot(slot: SessionSlotEntity) {
        slots[slot.id] = slot
        slotsFlow.value = slots.values.toList()
    }
    override fun observeSets(sessionSlotId: Long): Flow<List<SetEntryEntity>> = setsFlow.map { it.filter { s -> s.sessionSlotId == sessionSlotId } }
    override suspend fun insertSet(entry: SetEntryEntity): Long {
        val id = (sets.keys.maxOrNull() ?: 0L) + 1
        val saved = entry.copy(id = id)
        sets[id] = saved
        setsFlow.value = sets.values.toList()
        return id
    }
    override suspend fun updateSet(entry: SetEntryEntity) {
        sets[entry.id] = entry
        setsFlow.value = sets.values.toList()
    }
    override suspend fun deleteSet(id: Long) {
        sets.remove(id)
        setsFlow.value = sets.values.toList()
    }
    override suspend fun allSlots(): List<SessionSlotEntity> = slots.values.toList()
    override suspend fun sets(sessionSlotId: Long): List<SetEntryEntity> = sets.values.filter { it.sessionSlotId == sessionSlotId }
    override suspend fun completedDays(): List<CompletedSessionDay> = sessions.values.filter { it.status == SessionStatus.COMPLETED }.map { CompletedSessionDay(it.date, it.kind) }
    override suspend fun completedSets(): List<CompletedSetRow> = sets.values.mapNotNull { set ->
        val slot = slots[set.sessionSlotId] ?: return@mapNotNull null
        val session = sessions[slot.sessionId] ?: return@mapNotNull null
        CompletedSetRow(
            sessionDate = session.date,
            isDeload = session.isDeload,
            sessionId = session.id,
            completedAt = session.completedAt,
            slotId = slot.slotId,
            prescriptionSnapshot = slot.prescriptionSnapshot,
            chosenAlternativeExerciseId = slot.chosenAlternativeExerciseId,
            setType = set.setType,
            weightKg = set.weightKg,
            reps = set.reps,
            holdSec = set.holdSec
        )
    }
    override suspend fun getSet(id: Long): SetEntryEntity? = sets[id]
    override suspend fun getSlot(id: Long): SessionSlotEntity? = slots[id]
    override suspend fun recentSetsForExercise(exerciseId: Long, limit: Int): List<SetEntryEntity> {
        return sets.values.filter { set ->
            val slot = slots[set.sessionSlotId]
            slot?.prescriptionSnapshot?.exerciseId == exerciseId || slot?.chosenAlternativeExerciseId == exerciseId
        }.sortedByDescending { it.loggedAt }.take(limit)
    }
    override suspend fun earliestCompletedDate(): LocalDate? = sessions.values.filter { it.status == SessionStatus.COMPLETED }.minOfOrNull { it.date }
    override suspend fun history(): List<WorkoutSessionEntity> = sessions.values.toList()
}

private class FakeExerciseRepository : ExerciseRepository {
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
        val e = exercises[id] ?: return
        exercises[id] = e.copy(archivedAt = Instant.now())
        flow.value = exercises.values.toList()
    }
    override suspend fun delete(id: Long) {
        exercises.remove(id)
        flow.value = exercises.values.toList()
    }
    override suspend fun referencedIds(): Set<Long> = emptySet()
}

private class FakeRoutineRepository : RoutineRepository {
    val days = mutableMapOf<Long, RoutineDayEntity>()
    val slots = mutableMapOf<Long, RoutineSlotEntity>()
    val checklistItems = mutableMapOf<Long, ChecklistItemEntity>()
    val alternatives = mutableMapOf<Long, SlotAlternativeEntity>()

    private val daysFlow = MutableStateFlow(emptyList<RoutineDayEntity>())
    private val slotsFlow = MutableStateFlow(emptyList<RoutineSlotEntity>())
    private val checklistFlow = MutableStateFlow(emptyList<ChecklistItemEntity>())

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
    override fun observeChecklist(dayId: Long): Flow<List<ChecklistItemEntity>> = checklistFlow.map { it.filter { c -> c.dayId == dayId } }
    override suspend fun insertChecklist(item: ChecklistItemEntity): Long {
        val id = (checklistItems.keys.maxOrNull() ?: 0L) + 1
        checklistItems[id] = item.copy(id = id)
        checklistFlow.value = checklistItems.values.toList()
        return id
    }
    override suspend fun updateChecklist(item: ChecklistItemEntity) {
        checklistItems[item.id] = item
        checklistFlow.value = checklistItems.values.toList()
    }
    override suspend fun deleteChecklist(id: Long) {
        checklistItems.remove(id)
        checklistFlow.value = checklistItems.values.toList()
    }
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
    override fun observeAlternatives(slotId: Long): Flow<List<SlotAlternativeEntity>> = MutableStateFlow(alternatives.values.filter { it.slotId == slotId })
    override suspend fun insertAlternative(alternative: SlotAlternativeEntity): Long {
        val id = (alternatives.keys.maxOrNull() ?: 0L) + 1
        alternatives[id] = alternative.copy(id = id)
        return id
    }
    override suspend fun deleteAlternative(id: Long) {
        alternatives.remove(id)
    }
    override fun observeCardioPlan(dayId: Long): Flow<CardioPlanEntity?> = MutableStateFlow(null)
    override suspend fun upsertCardioPlan(plan: CardioPlanEntity): Long = 0L
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

private class FakeBaselineRepository : BaselineRepository {
    val baselines = mutableMapOf<Long, SlotBaselineEntity>()
    override suspend fun forSlot(slotId: Long): SlotBaselineEntity? = baselines.values.find { it.slotId == slotId }
    override suspend fun forSlots(slotIds: List<Long>): List<SlotBaselineEntity> = baselines.values.filter { it.slotId in slotIds }
    override suspend fun all(): List<SlotBaselineEntity> = baselines.values.toList()
    override suspend fun save(entity: SlotBaselineEntity) {
        baselines[entity.id] = entity
    }
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
