package com.forge.hypertrophy.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.dao.CompletedSessionDay
import com.forge.hypertrophy.data.dao.CompletedSetRow
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
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyReviewViewModelTest {
    // Thursday 2026-10-01; the review week is Mon 28 Sep – Sun 4 Oct.
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC)
    private val monday = LocalDate.of(2026, 9, 28)
    private val sessionRepo = FakeSessionRepository()
    private val exerciseRepo = FakeExerciseRepository()
    private val skillRepo = FakeSkillRepository()
    private val activeViewModels = mutableListOf<ViewModel>()

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

    private fun review() = track(
        WeeklyReviewViewModel(
            sessions = sessionRepo,
            exercises = exerciseRepo,
            skills = skillRepo,
            clock = clock,
        ),
    )

    private fun <T : ViewModel> track(model: T): T {
        activeViewModels.add(model)
        return model
    }

    private suspend fun session(date: LocalDate, dayId: Long, kind: SessionKind = SessionKind.GYM): Long = sessionRepo.insert(
        WorkoutSessionEntity(
            date = date,
            dayId = dayId,
            kind = kind,
            status = SessionStatus.COMPLETED,
            isDeload = false,
            isShortOnTime = false,
            readinessSleep = null,
            readinessSoreness = null,
            readinessEnergy = null,
            startedAt = null,
            completedAt = clock.instant(),
        ),
    )

    private suspend fun set(sessionSlotId: Long, weight: Double, reps: Int, type: SetType = SetType.WORKING) {
        sessionRepo.insertSet(
            SetEntryEntity(
                sessionSlotId = sessionSlotId,
                setNumber = 1,
                side = SetSide.BOTH,
                setType = type,
                weightKg = weight,
                reps = reps,
                holdSec = null,
                rpe = null,
                jointFlags = emptyList(),
                entryMethod = EntryMethod.SCREEN,
                loggedAt = clock.instant(),
            ),
        )
    }

    @Test
    fun reviewCountsSessionsPrsVolumeAndStageMoves() = runBlocking {
        val exerciseId = exerciseRepo.insert(
            ExerciseEntity(
                name = "deadlift",
                equipment = Equipment.BARBELL,
                barWeightKg = 20.0,
                loadIncrementKg = 2.5,
                isUnilateral = false,
                skillId = null,
                primaryMuscleGroups = listOf("back"),
                secondaryMuscleGroups = emptyList(),
                setupNotes = "",
                archivedAt = null,
            )
        )
        val dayId = 1L
        val slotId = 1L
        val prescription = SlotPrescription(
            exerciseId = exerciseId,
            category = SlotCategory.COMPOUND,
            sortOrder = 0,
            supersetGroup = null,
            metricType = MetricType.WEIGHT_REPS,
            setsMin = 3,
            setsMax = 3,
            repsLow = 5,
            repsHigh = 5,
            isAmrap = false,
            holdTargetSec = null,
            blockDurationSec = null,
            restMinSec = 60,
            restMaxSec = 90,
            restAsNeeded = false,
            isOptional = false,
            skipReasonLabel = null,
            targetSkillStepId = null,
            progressionRule = ProgressionRule.DOUBLE,
            incrementOverrideKg = null,
            holdTargetMaxSec = null,
            notes = null,
        )

        // Last week: one session, two hard sets at 100 × 5.
        val lastWeek = session(monday.minusDays(3), dayId)
        val lastSlot = sessionRepo.insertSlot(SessionSlotEntity(sessionId = lastWeek, slotId = slotId, prescriptionSnapshot = prescription, chosenAlternativeExerciseId = null, skipped = false, skipReason = null, formConfirmed = null))
        set(lastSlot, 100.0, 5)
        set(lastSlot, 100.0, 5)
        // This week: two sessions, one heavier set that beats the old estimate plus a warm-up.
        val first = session(monday, dayId)
        val firstSlot = sessionRepo.insertSlot(SessionSlotEntity(sessionId = first, slotId = slotId, prescriptionSnapshot = prescription, chosenAlternativeExerciseId = null, skipped = false, skipReason = null, formConfirmed = null))
        set(firstSlot, 60.0, 5, SetType.WARMUP)
        set(firstSlot, 110.0, 5)
        val second = session(monday.plusDays(2), dayId)
        sessionRepo.insertSlot(SessionSlotEntity(sessionId = second, slotId = slotId, prescriptionSnapshot = prescription, chosenAlternativeExerciseId = null, skipped = false, skipReason = null, formConfirmed = null))
        session(monday.plusDays(3), dayId, SessionKind.REST)

        val skillId = skillRepo.insert(SkillEntity(name = "planche", archivedAt = null))
        skillRepo.insertStep(SkillStepEntity(skillId = skillId, sortOrder = 0, name = "tuck"))
        skillRepo.insertStep(SkillStepEntity(skillId = skillId, sortOrder = 1, name = "advanced tuck"))
        skillRepo.recordStageEvent(
            SkillStageEventEntity(
                skillId = skillId,
                date = monday.plusDays(1),
                fromTier = 0,
                fromStage = 3,
                toTier = 1,
                toStage = 1,
                recordedAt = clock.instant(),
            ),
        )

        val viewModel = review()
        awaitUntil { viewModel.uiState.value.loaded }
        val state = viewModel.uiState.value

        assertEquals(monday, state.weekStart)
        assertEquals(monday.plusDays(6), state.weekEnd)
        assertTrue(state.isCurrentWeek)
        assertEquals(2, state.sessionsCompleted)
        assertEquals(1, state.previousSessionsCompleted)

        val pr = state.prs.single()
        assertEquals("deadlift", pr.exerciseName)
        assertEquals(110.0, pr.weightKg, 0.0)
        assertEquals(5, pr.reps)
        assertEquals(128.33, pr.e1rmKg, 0.001)

        val move = state.advancements.single()
        assertEquals("planche", move.skillName)
        assertEquals("tuck", move.fromTierName)
        assertEquals(3, move.fromStage)
        assertEquals("advanced tuck", move.toTierName)
        assertEquals(1, move.toStage)

        val back = state.volume.single()
        assertEquals("back", back.muscle)
        assertEquals(1.0, back.thisWeek, 0.0)
        assertEquals(2.0, back.lastWeek, 0.0)
    }

    @Test
    fun weekNavigationStopsAtTheCurrentWeek() = runBlocking {
        val viewModel = review()
        awaitUntil { viewModel.uiState.value.loaded }

        viewModel.onEvent(WeeklyReviewEvent.PreviousWeek)
        awaitUntil { viewModel.uiState.value.weekStart == monday.minusWeeks(1) }
        assertFalse(viewModel.uiState.value.isCurrentWeek)

        viewModel.onEvent(WeeklyReviewEvent.NextWeek)
        awaitUntil { viewModel.uiState.value.weekStart == monday }
        assertTrue(viewModel.uiState.value.isCurrentWeek)

        viewModel.onEvent(WeeklyReviewEvent.NextWeek)
        viewModel.onEvent(WeeklyReviewEvent.Refresh)
        awaitUntil { viewModel.uiState.value.loaded }
        assertEquals(monday, viewModel.uiState.value.weekStart)
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
        override fun observeSlots(sessionId: Long): Flow<List<SessionSlotEntity>> = MutableStateFlow(slots.values.filter { it.sessionId == sessionId })
        override suspend fun insertSlot(slot: SessionSlotEntity): Long {
            val id = (slots.keys.maxOrNull() ?: 0L) + 1
            slots[id] = slot.copy(id = id)
            return id
        }
        override suspend fun updateSlot(slot: SessionSlotEntity) {}
        override fun observeSets(sessionSlotId: Long): Flow<List<SetEntryEntity>> = MutableStateFlow(sets.values.filter { it.sessionSlotId == sessionSlotId })
        override suspend fun insertSet(entry: SetEntryEntity): Long {
            val id = (sets.keys.maxOrNull() ?: 0L) + 1
            sets[id] = entry.copy(id = id)
            return id
        }
        override suspend fun updateSet(entry: SetEntryEntity) {}
        override suspend fun deleteSet(id: Long) {}
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
        override suspend fun recentSetsForExercise(exerciseId: Long, limit: Int): List<SetEntryEntity> = emptyList()
        override suspend fun earliestCompletedDate(): LocalDate? = sessions.values.filter { it.status == SessionStatus.COMPLETED }.minOfOrNull { it.date }
        override suspend fun history(): List<WorkoutSessionEntity> = sessions.values.toList()
    }

    private class FakeExerciseRepository : ExerciseRepository {
        val exercises = mutableMapOf<Long, ExerciseEntity>()
        override fun observeActive(): Flow<List<ExerciseEntity>> = MutableStateFlow(exercises.values.toList())
        override suspend fun get(id: Long): ExerciseEntity? = exercises[id]
        override suspend fun all(): List<ExerciseEntity> = exercises.values.toList()
        override suspend fun insert(exercise: ExerciseEntity): Long {
            val id = (exercises.keys.maxOrNull() ?: 0L) + 1
            exercises[id] = exercise.copy(id = id)
            return id
        }
        override suspend fun update(exercise: ExerciseEntity) {}
        override suspend fun archive(id: Long) {}
        override suspend fun delete(id: Long) {}
        override suspend fun referencedIds(): Set<Long> = emptySet()
    }

    private class FakeSkillRepository : SkillRepository {
        val skills = mutableMapOf<Long, SkillEntity>()
        val steps = mutableMapOf<Long, SkillStepEntity>()
        val events = mutableListOf<SkillStageEventEntity>()

        override fun observeActive(): Flow<List<SkillEntity>> = MutableStateFlow(skills.values.toList())
        override suspend fun get(id: Long): SkillEntity? = skills[id]
        override suspend fun insert(skill: SkillEntity): Long {
            val id = (skills.keys.maxOrNull() ?: 0L) + 1
            skills[id] = skill.copy(id = id)
            return id
        }
        override suspend fun update(skill: SkillEntity) {}
        override suspend fun archive(id: Long) {}
        override suspend fun delete(id: Long) {}
        override fun observeSteps(skillId: Long): Flow<List<SkillStepEntity>> = MutableStateFlow(steps.values.filter { it.skillId == skillId })
        override suspend fun getSteps(skillId: Long): List<SkillStepEntity> = steps.values.filter { it.skillId == skillId }.sortedBy { it.sortOrder }
        override suspend fun insertStep(step: SkillStepEntity): Long {
            val id = (steps.keys.maxOrNull() ?: 0L) + 1
            steps[id] = step.copy(id = id)
            return id
        }
        override suspend fun updateStep(step: SkillStepEntity) {}
        override suspend fun deleteStep(id: Long) {}
        override suspend fun getProgress(skillId: Long): SkillProgressEntity? = null
        override suspend fun allSteps(): List<SkillStepEntity> = steps.values.toList()
        override suspend fun allProgress(): List<SkillProgressEntity> = emptyList()
        override suspend fun upsertProgress(progress: SkillProgressEntity): Long = 0L
        override suspend fun recordStageEvent(event: SkillStageEventEntity): Long {
            events.add(event)
            return 0L
        }
        override suspend fun stageEventsBetween(from: LocalDate, to: LocalDate): List<SkillStageEventEntity> = events.filter { it.date in from..to }
        override suspend fun referencedIds(): Set<Long> = emptySet()
    }
}
