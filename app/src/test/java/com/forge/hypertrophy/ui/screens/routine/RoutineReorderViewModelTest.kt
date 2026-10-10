package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.CardioPlanEntity
import com.forge.hypertrophy.data.entity.ChecklistItemEntity
import com.forge.hypertrophy.data.entity.ExerciseEntity
import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SlotAlternativeEntity
import com.forge.hypertrophy.data.entity.SlotBaselineEntity
import com.forge.hypertrophy.data.repository.BaselineRepository
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SlotCategory
import java.time.Clock
import java.time.Instant
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineReorderViewModelTest {
    private val programRepo = FakeProgramRepository()
    private val routineRepo = FakeRoutineRepository()
    private val exerciseRepo = FakeExerciseRepository()
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
    fun movingADayWritesContiguousIndices() = runBlocking {
        val programId = programRepo.insert(ProgramEntity(name = "program", scheduleMode = ScheduleMode.ROLLING, rollingSequence = 0, deloadActive = false, deloadStartedOn = null))
        routineRepo.insertDay(RoutineDayEntity(programId = programId, sequenceIndex = 5, label = "c", dayOfWeek = null, isRest = false))
        routineRepo.insertDay(RoutineDayEntity(programId = programId, sequenceIndex = 9, label = "b", dayOfWeek = null, isRest = false))
        routineRepo.insertDay(RoutineDayEntity(programId = programId, sequenceIndex = 1, label = "a", dayOfWeek = null, isRest = false))
        val viewModel = track(
            ProgramEditorViewModel(
                SavedStateHandle(mapOf("programId" to programId)),
                programRepo,
                routineRepo,
            ),
        )

        viewModel.onEvent(ProgramEditorEvent.MoveDay(from = 0, to = 1))

        awaitUntil {
            val days = routineRepo.days(programId)
            days.map { it.sequenceIndex } == listOf(0, 1, 2) && days.map { it.label } == listOf("c", "a", "b")
        }
        viewModel.onEvent(ProgramEditorEvent.MoveDay(from = 1, to = 0))
        awaitUntil {
            val days = routineRepo.days(programId)
            days.map { it.sequenceIndex } == listOf(0, 1, 2) && days.map { it.label } == listOf("a", "c", "b")
        }
        val days = routineRepo.days(programId)
        assertEquals(listOf(0, 1, 2), days.map { it.sequenceIndex })
        assertEquals(days.size, days.map { it.sequenceIndex }.distinct().size)
    }

    @Test
    fun movingASlotWritesContiguousIndices() = runBlocking {
        val programId = programRepo.insert(ProgramEntity(name = "program", scheduleMode = ScheduleMode.ROLLING, rollingSequence = 0, deloadActive = false, deloadStartedOn = null))
        val dayId = routineRepo.insertDay(RoutineDayEntity(programId = programId, label = "day", dayOfWeek = null, sequenceIndex = 0, isRest = false))
        
        val first = exerciseRepo.insert(ExerciseEntity(name = "a", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
        val second = exerciseRepo.insert(ExerciseEntity(name = "b", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
        val third = exerciseRepo.insert(ExerciseEntity(name = "c", equipment = Equipment.BARBELL, barWeightKg = 20.0, loadIncrementKg = 2.5, isUnilateral = false, skillId = null, primaryMuscleGroups = emptyList(), secondaryMuscleGroups = emptyList(), setupNotes = "", archivedAt = null))
        
        routineRepo.insertSlot(RoutineSlotEntity(dayId = dayId, exerciseId = third, sortOrder = 5, category = SlotCategory.COMPOUND, metricType = MetricType.WEIGHT_REPS, setsMin = 3, setsMax = 3, repsLow = 8, repsHigh = 12, restMinSec = 60, restMaxSec = 90, progressionRule = ProgressionRule.DOUBLE, supersetGroup = null, isAmrap = false, holdTargetSec = null, blockDurationSec = null, restAsNeeded = false, isOptional = false, skipReasonLabel = null, targetSkillStepId = null, incrementOverrideKg = null))
        routineRepo.insertSlot(RoutineSlotEntity(dayId = dayId, exerciseId = second, sortOrder = 9, category = SlotCategory.COMPOUND, metricType = MetricType.WEIGHT_REPS, setsMin = 3, setsMax = 3, repsLow = 8, repsHigh = 12, restMinSec = 60, restMaxSec = 90, progressionRule = ProgressionRule.DOUBLE, supersetGroup = null, isAmrap = false, holdTargetSec = null, blockDurationSec = null, restAsNeeded = false, isOptional = false, skipReasonLabel = null, targetSkillStepId = null, incrementOverrideKg = null))
        routineRepo.insertSlot(RoutineSlotEntity(dayId = dayId, exerciseId = first, sortOrder = 1, category = SlotCategory.COMPOUND, metricType = MetricType.WEIGHT_REPS, setsMin = 3, setsMax = 3, repsLow = 8, repsHigh = 12, restMinSec = 60, restMaxSec = 90, progressionRule = ProgressionRule.DOUBLE, supersetGroup = null, isAmrap = false, holdTargetSec = null, blockDurationSec = null, restAsNeeded = false, isOptional = false, skipReasonLabel = null, targetSkillStepId = null, incrementOverrideKg = null))
        
        val viewModel = track(
            DayEditorViewModel(
                SavedStateHandle(mapOf("dayId" to dayId)),
                routineRepo,
                exerciseRepo,
                FakeBaselineRepository(),
                Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC),
            ),
        )

        viewModel.onEvent(DayEditorEvent.MoveSlot(from = 0, to = 1))

        awaitUntil {
            val slots = routineRepo.slots(dayId)
            slots.map { it.sortOrder } == listOf(0, 1, 2) &&
                slots.map { it.exerciseId } == listOf(third, first, second)
        }
        viewModel.onEvent(DayEditorEvent.MoveSlot(from = 1, to = 0))
        awaitUntil {
            val slots = routineRepo.slots(dayId)
            slots.map { it.sortOrder } == listOf(0, 1, 2) &&
                slots.map { it.exerciseId } == listOf(first, third, second)
        }
        val slots = routineRepo.slots(dayId)
        assertEquals(listOf(0, 1, 2), slots.map { it.sortOrder })
        assertEquals(slots.size, slots.map { it.sortOrder }.distinct().size)
    }

    private fun <T : ViewModel> track(model: T): T {
        activeViewModels.add(model)
        return model
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
        
        suspend fun slots(dayId: Long): List<RoutineSlotEntity> =
            slots.values.filter { it.dayId == dayId }.sortedBy { it.sortOrder }
    }

    private class FakeBaselineRepository : BaselineRepository {
        override suspend fun forSlot(slotId: Long): SlotBaselineEntity? = null
        override suspend fun forSlots(slotIds: List<Long>): List<SlotBaselineEntity> = emptyList()
        override suspend fun all(): List<SlotBaselineEntity> = emptyList()
        override suspend fun save(entity: SlotBaselineEntity) {}
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
            exercises[id] = exercises[id]!!.copy(archivedAt = Instant.now())
            flow.value = exercises.values.toList()
        }
        override suspend fun delete(id: Long) {
            exercises.remove(id)
            flow.value = exercises.values.toList()
        }
        override suspend fun referencedIds(): Set<Long> = emptySet()
    }
}
