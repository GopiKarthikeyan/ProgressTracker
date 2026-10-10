package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.CardioPlanEntity
import com.forge.hypertrophy.data.entity.ChecklistItemEntity
import com.forge.hypertrophy.data.entity.ExerciseEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SlotAlternativeEntity
import com.forge.hypertrophy.data.entity.SlotBaselineEntity
import com.forge.hypertrophy.data.repository.BaselineRepository
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
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
class DayEditorViewModelTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC)
    private val routineRepo = FakeRoutineRepository()
    private val exerciseRepo = FakeExerciseRepository()
    private val baselineRepo = FakeBaselineRepository()
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
    fun steppingBaselineUpdatesWeightAndDetail() = runBlocking {
        val programId = 1L
        val dayId = routineRepo.insertDay(
            RoutineDayEntity(
                programId = programId,
                label = "day",
                dayOfWeek = null,
                sequenceIndex = 0,
                isRest = false,
            ),
        )
        val exerciseId = exerciseRepo.insert(
            ExerciseEntity(
                name = "press",
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
        val slotId = routineRepo.insertSlot(
            RoutineSlotEntity(
                dayId = dayId,
                exerciseId = exerciseId,
                category = SlotCategory.COMPOUND,
                sortOrder = 0,
                supersetGroup = null,
                metricType = MetricType.WEIGHT_REPS,
                setsMin = 3,
                setsMax = 3,
                repsLow = 8,
                repsHigh = 12,
                isAmrap = false,
                holdTargetSec = null,
                blockDurationSec = null,
                restMinSec = 90,
                restMaxSec = 120,
                restAsNeeded = false,
                isOptional = false,
                skipReasonLabel = null,
                targetSkillStepId = null,
                progressionRule = ProgressionRule.DOUBLE,
                incrementOverrideKg = null,
            ),
        )
        val viewModel = track(
            DayEditorViewModel(
                SavedStateHandle(mapOf("dayId" to dayId)),
                routineRepo,
                exerciseRepo,
                baselineRepo,
                clock,
            ),
        )
        awaitUntil { viewModel.uiState.value.ready && viewModel.uiState.value.slots.size == 1 }

        viewModel.onEvent(DayEditorEvent.StepBaselineWeight(slotId, 1))
        awaitUntil {
            val row = viewModel.uiState.value.slots.singleOrNull()
            row?.baselineWeightKg == 2.5 && row.detail.contains("2.5 kg")
        }
        assertEquals(2.5, baselineRepo.forSlot(slotId)!!.weightKg!!, 0.0)

        viewModel.onEvent(DayEditorEvent.StepBaselineWeight(slotId, 1))
        awaitUntil {
            val row = viewModel.uiState.value.slots.singleOrNull()
            row?.baselineWeightKg == 5.0 && row.detail.contains("5 kg")
        }
        assertEquals(5.0, baselineRepo.forSlot(slotId)!!.weightKg!!, 0.0)

        viewModel.onEvent(DayEditorEvent.StepBaselineWeight(slotId, -1))
        awaitUntil {
            val row = viewModel.uiState.value.slots.singleOrNull()
            row?.baselineWeightKg == 2.5
        }
        assertEquals(2.5, baselineRepo.forSlot(slotId)!!.weightKg!!, 0.0)
    }

    private fun <T : ViewModel> track(model: T): T {
        activeViewModels.add(model)
        return model
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
        override suspend fun reorderDays(programId: Long, orderedDayIds: List<Long>) {}
        override suspend fun days(programId: Long): List<RoutineDayEntity> =
            days.values.filter { it.programId == programId }.sortedBy { it.sequenceIndex }
        override suspend fun slotsForDays(dayIds: List<Long>): List<RoutineSlotEntity> =
            slots.values.filter { it.dayId in dayIds }
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

    private class FakeExerciseRepository : ExerciseRepository {
        val exercises = mutableMapOf<Long, ExerciseEntity>()
        private val flow = MutableStateFlow(emptyList<ExerciseEntity>())

        override fun observeActive(): Flow<List<ExerciseEntity>> =
            flow.map { it.filter { e -> e.archivedAt == null } }
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

    private class FakeBaselineRepository : BaselineRepository {
        private val bySlot = mutableMapOf<Long, SlotBaselineEntity>()

        override suspend fun forSlot(slotId: Long): SlotBaselineEntity? = bySlot[slotId]
        override suspend fun forSlots(slotIds: List<Long>): List<SlotBaselineEntity> =
            slotIds.mapNotNull { bySlot[it] }
        override suspend fun all(): List<SlotBaselineEntity> = bySlot.values.toList()
        override suspend fun save(entity: SlotBaselineEntity) {
            val id = bySlot[entity.slotId]?.id ?: ((bySlot.values.maxOfOrNull { it.id } ?: 0L) + 1)
            bySlot[entity.slotId] = entity.copy(id = id)
        }
    }
}
