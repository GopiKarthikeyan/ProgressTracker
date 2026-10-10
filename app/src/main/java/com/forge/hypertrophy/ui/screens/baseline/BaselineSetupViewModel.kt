package com.forge.hypertrophy.ui.screens.baseline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.diagnostics.Breadcrumbs
import com.forge.hypertrophy.data.entity.SlotBaselineEntity
import com.forge.hypertrophy.data.repository.BaselineRepository
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.domain.engine.DoubleProgressionEngine
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionInput
import com.forge.hypertrophy.domain.model.SetType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BaselineSlotUi(
    val slotId: Long,
    val exerciseName: String,
    val incrementKg: Double,
    val weightKg: Double,
    val reps: Int,
    val calibrate: Boolean,
)

data class BaselineDayUi(
    val label: String,
    val slots: List<BaselineSlotUi>,
)

data class BaselineSetupUiState(
    val days: List<BaselineDayUi> = emptyList(),
    val loaded: Boolean = false,
    val saved: Boolean = false,
)

sealed interface BaselineSetupEvent {
    data class StepWeight(val slotId: Long, val direction: Int) : BaselineSetupEvent
    data class StepReps(val slotId: Long, val direction: Int) : BaselineSetupEvent
    data class Calibrate(val slotId: Long) : BaselineSetupEvent
    data object SkipAll : BaselineSetupEvent
    data object Save : BaselineSetupEvent
}

@HiltViewModel
class BaselineSetupViewModel @Inject constructor(
    private val programs: ProgramRepository,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val sessions: SessionRepository,
    private val baselines: BaselineRepository,
    private val clock: Clock,
    private val breadcrumbs: Breadcrumbs,
) : ViewModel() {
    private val progression = DoubleProgressionEngine()
    private val _uiState = MutableStateFlow(BaselineSetupUiState())
    val uiState: StateFlow<BaselineSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: BaselineSetupEvent) {
        breadcrumbs.record(event.javaClass.simpleName)
        when (event) {
            is BaselineSetupEvent.StepWeight -> stepWeight(event.slotId, event.direction)
            is BaselineSetupEvent.StepReps -> stepReps(event.slotId, event.direction)
            is BaselineSetupEvent.Calibrate -> toggleCalibrate(event.slotId)
            BaselineSetupEvent.SkipAll -> skipAll()
            BaselineSetupEvent.Save -> viewModelScope.launch { save() }
        }
    }

    private suspend fun load() {
        val program = programs.observeActive().first()
        if (program == null) {
            _uiState.value = BaselineSetupUiState(loaded = true)
            return
        }
        val days = routines.observeDays(program.id).first()
        val slots = routines.slotsForDays(days.map { it.id })
        val exerciseById = exercises.all().associateBy { it.id }
        val stored = baselines.all().associateBy { it.slotId }
        val completed = sessions.completedSets()
        val rows = days.mapNotNull { day ->
            val daySlots = slots.filter { it.dayId == day.id }.sortedBy { it.sortOrder }.mapNotNull { slot ->
                val exercise = exerciseById[slot.exerciseId] ?: return@mapNotNull null
                val weighted = exercise.equipment == Equipment.WEIGHTED_BODYWEIGHT
                if (slot.metricType != MetricType.WEIGHT_REPS && !weighted) return@mapNotNull null
                val existing = stored[slot.id]
                val increment = slot.incrementOverrideKg ?: exercise.loadIncrementKg
                val prefill = coldStartKg(slot, exercise, completed, increment)
                BaselineSlotUi(
                    slotId = slot.id,
                    exerciseName = exercise.name,
                    incrementKg = increment,
                    weightKg = existing?.weightKg ?: prefill,
                    reps = existing?.repsHint ?: slot.repsLow ?: slot.repsHigh ?: 5,
                    calibrate = existing != null && existing.weightKg == null,
                )
            }
            if (daySlots.isEmpty()) null else BaselineDayUi(day.label, daySlots)
        }
        _uiState.value = BaselineSetupUiState(days = rows, loaded = true)
    }

    private fun coldStartKg(
        slot: com.forge.hypertrophy.data.entity.RoutineSlotEntity,
        exercise: com.forge.hypertrophy.data.entity.ExerciseEntity,
        completed: List<com.forge.hypertrophy.data.dao.CompletedSetRow>,
        increment: Double,
    ): Double {
        val latest = completed
            .filter { (it.chosenAlternativeExerciseId ?: it.prescriptionSnapshot.exerciseId) == exercise.id }
            .filter { it.setType == SetType.WORKING && it.weightKg != null }
            .maxByOrNull { it.completedAt ?: java.time.Instant.EPOCH }
            ?.weightKg
        val low = slot.repsLow ?: 1
        val high = slot.repsHigh ?: low
        return progression.suggest(
            ProgressionInput(
                rule = slot.progressionRule,
                equipment = exercise.equipment,
                metricType = slot.metricType,
                repsLow = low,
                repsHigh = high,
                exerciseIncrementKg = increment,
                incrementOverrideKg = slot.incrementOverrideKg,
                slotSessions = emptyList(),
                latestWeightFromAnySlotKg = latest,
            ),
        ).weightKg ?: 0.0
    }

    private fun updateSlot(slotId: Long, block: (BaselineSlotUi) -> BaselineSlotUi) {
        _uiState.update { state ->
            state.copy(
                days = state.days.map { day ->
                    day.copy(slots = day.slots.map { if (it.slotId == slotId) block(it) else it })
                },
            )
        }
    }

    private fun stepWeight(slotId: Long, direction: Int) {
        updateSlot(slotId) { slot ->
            val next = (slot.weightKg + direction * slot.incrementKg).coerceAtLeast(0.0)
            slot.copy(weightKg = next, calibrate = false)
        }
    }

    private fun stepReps(slotId: Long, direction: Int) {
        updateSlot(slotId) { slot ->
            slot.copy(reps = (slot.reps + direction).coerceAtLeast(1), calibrate = false)
        }
    }

    private fun toggleCalibrate(slotId: Long) {
        updateSlot(slotId) { it.copy(calibrate = !it.calibrate) }
    }

    private fun skipAll() {
        _uiState.update { state ->
            state.copy(
                days = state.days.map { day ->
                    day.copy(slots = day.slots.map { it.copy(calibrate = true) })
                },
            )
        }
    }

    private suspend fun save() {
        val now = clock.instant()
        for (day in _uiState.value.days) {
            for (slot in day.slots) {
                baselines.save(
                    SlotBaselineEntity(
                        slotId = slot.slotId,
                        weightKg = if (slot.calibrate) null else slot.weightKg,
                        repsHint = if (slot.calibrate) null else slot.reps,
                        setAt = now,
                    ),
                )
            }
        }
        _uiState.update { it.copy(saved = true) }
    }
}
