package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.SavedStateHandle
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
import com.forge.hypertrophy.data.entity.SkillStepEntity
import com.forge.hypertrophy.data.entity.SlotAlternativeEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SlotEditorViewModelTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC)
    private val programRepo = FakeProgramRepository()
    private val routineRepo = FakeRoutineRepository()
    private val exerciseRepo = FakeExerciseRepository(clock)
    private val skillRepo = FakeSkillRepository(clock)
    private val sessionRepo = FakeSessionRepository()
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

    @Test
    fun setsMinAboveMaxIsRejected() = runBlocking {
        val slotId = seedSlot()
        val viewModel = editor(slotId)
        awaitUntil { viewModel.uiState.value.ready }
        val before = routineRepo.getSlot(slotId)

        viewModel.onEvent(SlotEditorEvent.SetsMin(9))
        viewModel.onEvent(SlotEditorEvent.Save)

        assertEquals(SlotValidationError.SETS, viewModel.uiState.value.validationError)
        assertEquals(before, routineRepo.getSlot(slotId))
    }

    @Test
    fun repsLowAboveHighIsRejected() = runBlocking {
        val slotId = seedSlot()
        val viewModel = editor(slotId)
        awaitUntil { viewModel.uiState.value.ready }
        val before = routineRepo.getSlot(slotId)

        viewModel.onEvent(SlotEditorEvent.RepsHigh(10))
        viewModel.onEvent(SlotEditorEvent.RepsLow(12))
        viewModel.onEvent(SlotEditorEvent.Save)

        assertEquals(SlotValidationError.REPS, viewModel.uiState.value.validationError)
        assertEquals(before, routineRepo.getSlot(slotId))
    }

    @Test
    fun restMinAboveMaxIsRejected() = runBlocking {
        val slotId = seedSlot()
        val viewModel = editor(slotId)
        awaitUntil { viewModel.uiState.value.ready }
        val before = routineRepo.getSlot(slotId)

        viewModel.onEvent(SlotEditorEvent.RestMin(200))
        viewModel.onEvent(SlotEditorEvent.Save)

        assertEquals(SlotValidationError.REST, viewModel.uiState.value.validationError)
        assertEquals(before, routineRepo.getSlot(slotId))
    }

    @Test
    fun holdTargetAboveMaxIsRejected() = runBlocking {
        val slotId = seedSlot()
        val viewModel = editor(slotId)
        awaitUntil { viewModel.uiState.value.ready }
        val before = routineRepo.getSlot(slotId)

        viewModel.onEvent(SlotEditorEvent.HoldTargetMax(10))
        viewModel.onEvent(SlotEditorEvent.HoldTarget(20))
        viewModel.onEvent(SlotEditorEvent.Save)

        assertEquals(SlotValidationError.HOLD, viewModel.uiState.value.validationError)
        assertEquals(before, routineRepo.getSlot(slotId))
    }

    @Test
    fun pairingWithTheNeighbourSharesAGroupAndUnpairingClearsIt() = runBlocking {
        val programId = programRepo.insert(ProgramEntity(name = "p", scheduleMode = ScheduleMode.ROLLING, rollingSequence = 0, deloadActive = false, deloadStartedOn = null))
        val dayId = routineRepo.insertDay(RoutineDayEntity(programId = programId, label = "d", sequenceIndex = 0, dayOfWeek = null, isRest = false))
        val exerciseA = exerciseRepo.insert(ExerciseEntity(name = "a", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
        val exerciseB = exerciseRepo.insert(ExerciseEntity(name = "b", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
        
        val currentId = routineRepo.insertSlot(RoutineSlotEntity(dayId = dayId, exerciseId = exerciseA, sortOrder = 0, category = SlotCategory.COMPOUND, metricType = MetricType.WEIGHT_REPS, setsMin = 3, setsMax = 3, repsLow = 8, repsHigh = 12, restMinSec = 60, restMaxSec = 90, progressionRule = ProgressionRule.DOUBLE, supersetGroup = null, isAmrap = false, holdTargetSec = null, blockDurationSec = null, restAsNeeded = false, isOptional = false, skipReasonLabel = null, targetSkillStepId = null, incrementOverrideKg = null))
        val neighbourId = routineRepo.insertSlot(RoutineSlotEntity(dayId = dayId, exerciseId = exerciseB, sortOrder = 1, category = SlotCategory.COMPOUND, metricType = MetricType.WEIGHT_REPS, setsMin = 3, setsMax = 3, repsLow = 8, repsHigh = 12, restMinSec = 60, restMaxSec = 90, progressionRule = ProgressionRule.DOUBLE, supersetGroup = null, isAmrap = false, holdTargetSec = null, blockDurationSec = null, restAsNeeded = false, isOptional = false, skipReasonLabel = null, targetSkillStepId = null, incrementOverrideKg = null))
        
        val viewModel = editor(currentId)
        awaitUntil { viewModel.uiState.value.neighbours.singleOrNull()?.id == neighbourId }

        viewModel.onEvent(SlotEditorEvent.PairWith(neighbourId))
        awaitUntil { viewModel.uiState.value.paired }

        val paired = routineRepo.slots(dayId)
        val group = paired.first().supersetGroup
        assertNotNull(group)
        assertEquals(listOf(group, group), paired.map { it.supersetGroup })
        assertTrue(viewModel.uiState.value.neighbours.single().sameGroup)

        viewModel.onEvent(SlotEditorEvent.PairWith(neighbourId))
        awaitUntil { viewModel.uiState.value.supersetGroup == null && !viewModel.uiState.value.paired }

        assertTrue(routineRepo.slots(dayId).all { it.supersetGroup == null })
    }

    @Test
    fun savingASlotLeavesTheSessionSnapshotAlone() = runBlocking {
        val programId = programRepo.insert(ProgramEntity(name = "p", scheduleMode = ScheduleMode.ROLLING, rollingSequence = 0, deloadActive = false, deloadStartedOn = null))
        val dayId = routineRepo.insertDay(RoutineDayEntity(programId = programId, label = "d", sequenceIndex = 0, dayOfWeek = null, isRest = false))
        val exerciseA = exerciseRepo.insert(ExerciseEntity(name = "a", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
        val slotId = routineRepo.insertSlot(RoutineSlotEntity(dayId = dayId, exerciseId = exerciseA, sortOrder = 0, category = SlotCategory.COMPOUND, metricType = MetricType.WEIGHT_REPS, setsMin = 3, setsMax = 3, repsLow = 8, repsHigh = 12, restMinSec = 60, restMaxSec = 90, progressionRule = ProgressionRule.DOUBLE, supersetGroup = null, isAmrap = false, holdTargetSec = null, blockDurationSec = null, restAsNeeded = false, isOptional = false, skipReasonLabel = null, targetSkillStepId = null, incrementOverrideKg = null))
        
        val slot = routineRepo.getSlot(slotId)!!
        val sessionId = sessionRepo.insert(WorkoutSessionEntity(date = LocalDate.now(), dayId = dayId, kind = SessionKind.GYM, status = SessionStatus.IN_PROGRESS, isDeload = false, isShortOnTime = false, readinessSleep = null, readinessSoreness = null, readinessEnergy = null, startedAt = clock.instant(), completedAt = null))
        
        sessionRepo.insertSlot(SessionSlotEntity(sessionId = sessionId, slotId = slotId, prescriptionSnapshot = slot.toPrescription(), chosenAlternativeExerciseId = null, skipped = false, skipReason = null, formConfirmed = null))
        
        val viewModel = editor(slotId)
        awaitUntil { viewModel.uiState.value.ready }

        viewModel.onEvent(SlotEditorEvent.SetsMax(slot.setsMax + 1))
        viewModel.onEvent(SlotEditorEvent.Save)

        awaitUntil { routineRepo.getSlot(slotId)!!.setsMax == slot.setsMax + 1 }
        assertEquals(slot.setsMax, sessionRepo.allSlots().single().prescriptionSnapshot.setsMax)
        assertNull(viewModel.uiState.value.validationError)
    }

    private suspend fun seedSlot(): Long {
        val programId = programRepo.insert(ProgramEntity(name = "p", scheduleMode = ScheduleMode.ROLLING, rollingSequence = 0, deloadActive = false, deloadStartedOn = null))
        val dayId = routineRepo.insertDay(RoutineDayEntity(programId = programId, label = "d", sequenceIndex = 0, dayOfWeek = null, isRest = false))
        val exerciseId = exerciseRepo.insert(ExerciseEntity(name = "a", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
        return routineRepo.insertSlot(RoutineSlotEntity(dayId = dayId, exerciseId = exerciseId, sortOrder = 0, category = SlotCategory.COMPOUND, metricType = MetricType.WEIGHT_REPS, setsMin = 3, setsMax = 3, repsLow = 8, repsHigh = 12, restMinSec = 60, restMaxSec = 90, progressionRule = ProgressionRule.DOUBLE, supersetGroup = null, isAmrap = false, holdTargetSec = null, blockDurationSec = null, restAsNeeded = false, isOptional = false, skipReasonLabel = null, targetSkillStepId = null, incrementOverrideKg = null))
    }

    private fun editor(slotId: Long) = track(
        SlotEditorViewModel(
            SavedStateHandle(mapOf("slotId" to slotId)),
            routineRepo,
            exerciseRepo,
            skillRepo,
        ),
    )

    private fun <T : ViewModel> track(model: T): T {
        activeViewModels.add(model)
        return model
    }

    private fun RoutineSlotEntity.toPrescription() = SlotPrescription(
        exerciseId = exerciseId,
        category = category,
        sortOrder = sortOrder,
        supersetGroup = supersetGroup,
        metricType = metricType,
        setsMin = setsMin,
        setsMax = setsMax,
        repsLow = repsLow,
        repsHigh = repsHigh,
        isAmrap = isAmrap,
        holdTargetSec = holdTargetSec,
        blockDurationSec = blockDurationSec,
        restMinSec = restMinSec,
        restMaxSec = restMaxSec,
        restAsNeeded = restAsNeeded,
        isOptional = isOptional,
        skipReasonLabel = skipReasonLabel,
        targetSkillStepId = targetSkillStepId,
        progressionRule = progressionRule,
        incrementOverrideKg = incrementOverrideKg,
        holdTargetMaxSec = holdTargetMaxSec,
        notes = notes,
    )

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
            var nextSupersetGroup = 1
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
        
        suspend fun slots(dayId: Long): List<RoutineSlotEntity> =
            slots.values.filter { it.dayId == dayId }.sortedBy { it.sortOrder }
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
        private val flow = MutableStateFlow(emptyList<SkillEntity>())

        override fun observeActive(): Flow<List<SkillEntity>> = flow.map { it.filter { s -> s.archivedAt == null } }
        override suspend fun get(id: Long): SkillEntity? = skills[id]
        override suspend fun all(): List<SkillEntity> = skills.values.toList()
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
        override fun observeSteps(skillId: Long): Flow<List<SkillStepEntity>> = MutableStateFlow(emptyList())
        override suspend fun getSteps(skillId: Long): List<SkillStepEntity> = emptyList()
        override suspend fun insertStep(step: SkillStepEntity): Long = 0L
        override suspend fun updateStep(step: SkillStepEntity) {}
        override suspend fun deleteStep(id: Long) {}
        override suspend fun getProgress(skillId: Long): SkillProgressEntity? = null
        override suspend fun allSteps(): List<SkillStepEntity> = emptyList()
        override suspend fun allProgress(): List<SkillProgressEntity> = emptyList()
        override suspend fun upsertProgress(progress: SkillProgressEntity): Long = 0L
        override suspend fun recordStageEvent(event: com.forge.hypertrophy.data.entity.SkillStageEventEntity): Long = 0L
        override suspend fun stageEventsBetween(from: java.time.LocalDate, to: java.time.LocalDate): List<com.forge.hypertrophy.data.entity.SkillStageEventEntity> = emptyList()
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
