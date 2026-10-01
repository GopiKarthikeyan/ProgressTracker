package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SlotAlternativeEntity
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.SlotCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExerciseChoice(
    val id: Long,
    val name: String,
    val skillId: Long?,
)

data class StepChoice(
    val id: Long,
    val name: String,
)

data class NeighbourSlot(
    val id: Long,
    val name: String,
    val sameGroup: Boolean,
)

enum class SlotValidationError {
    SETS,
    REPS,
    REST,
    HOLD,
}

data class SlotEditorUiState(
    val ready: Boolean = false,
    val exerciseId: Long = 0,
    val exercises: List<ExerciseChoice> = emptyList(),
    val category: SlotCategory = SlotCategory.COMPOUND,
    val metricType: MetricType = MetricType.WEIGHT_REPS,
    val setsMin: Int = 0,
    val setsMax: Int = 0,
    val repsLow: Int? = null,
    val repsHigh: Int? = null,
    val isAmrap: Boolean = false,
    val holdTargetSec: Int? = null,
    val holdTargetMaxSec: Int? = null,
    val blockDurationSec: Int? = null,
    val restMinSec: Int? = null,
    val restMaxSec: Int? = null,
    val restAsNeeded: Boolean = false,
    val isOptional: Boolean = false,
    val skipReasonLabel: String = "",
    val progressionRule: ProgressionRule = ProgressionRule.DOUBLE,
    val incrementOverrideKg: Double? = null,
    val notes: String = "",
    val targetSkillStepId: Long? = null,
    val skillSteps: List<StepChoice> = emptyList(),
    val alternativeExerciseIds: Set<Long> = emptySet(),
    val neighbours: List<NeighbourSlot> = emptyList(),
    val supersetGroup: Int? = null,
    val paired: Boolean = false,
    val validationError: SlotValidationError? = null,
)

sealed interface SlotEditorEvent {
    data class Exercise(val id: Long) : SlotEditorEvent
    data class Category(val value: SlotCategory) : SlotEditorEvent
    data class Metric(val value: MetricType) : SlotEditorEvent
    data class SetsMin(val value: Int) : SlotEditorEvent
    data class SetsMax(val value: Int) : SlotEditorEvent
    data class RepsLow(val value: Int?) : SlotEditorEvent
    data class RepsHigh(val value: Int?) : SlotEditorEvent
    data class Amrap(val value: Boolean) : SlotEditorEvent
    data class HoldTarget(val value: Int?) : SlotEditorEvent
    data class HoldTargetMax(val value: Int?) : SlotEditorEvent
    data class BlockDuration(val value: Int?) : SlotEditorEvent
    data class RestMin(val value: Int?) : SlotEditorEvent
    data class RestMax(val value: Int?) : SlotEditorEvent
    data class RestAsNeeded(val value: Boolean) : SlotEditorEvent
    data class Optional(val value: Boolean) : SlotEditorEvent
    data class SkipReason(val value: String) : SlotEditorEvent
    data class Progression(val value: ProgressionRule) : SlotEditorEvent
    data class IncrementOverride(val value: Double?) : SlotEditorEvent
    data class Notes(val value: String) : SlotEditorEvent
    data class TargetStep(val id: Long?) : SlotEditorEvent
    data class ToggleAlternative(val exerciseId: Long) : SlotEditorEvent
    data class PairWith(val neighbourId: Long) : SlotEditorEvent
    data object Save : SlotEditorEvent
    data object DismissError : SlotEditorEvent
}

@HiltViewModel
class SlotEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val skills: SkillRepository,
) : ViewModel() {
    private val slotId: Long = checkNotNull(savedStateHandle.get<Long>("slotId"))
    private val _uiState = MutableStateFlow(SlotEditorUiState())
    val uiState: StateFlow<SlotEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    fun onEvent(event: SlotEditorEvent) {
        when (event) {
            is SlotEditorEvent.Exercise -> selectExercise(event.id)
            is SlotEditorEvent.Category -> _uiState.update { it.copy(category = event.value) }
            is SlotEditorEvent.Metric -> _uiState.update { it.copy(metricType = event.value) }
            is SlotEditorEvent.SetsMin -> _uiState.update { it.copy(setsMin = event.value) }
            is SlotEditorEvent.SetsMax -> _uiState.update { it.copy(setsMax = event.value) }
            is SlotEditorEvent.RepsLow -> _uiState.update { it.copy(repsLow = event.value) }
            is SlotEditorEvent.RepsHigh -> _uiState.update { it.copy(repsHigh = event.value) }
            is SlotEditorEvent.Amrap -> _uiState.update { it.copy(isAmrap = event.value) }
            is SlotEditorEvent.HoldTarget -> _uiState.update { it.copy(holdTargetSec = event.value) }
            is SlotEditorEvent.HoldTargetMax -> _uiState.update { it.copy(holdTargetMaxSec = event.value) }
            is SlotEditorEvent.BlockDuration -> _uiState.update { it.copy(blockDurationSec = event.value) }
            is SlotEditorEvent.RestMin -> _uiState.update { it.copy(restMinSec = event.value) }
            is SlotEditorEvent.RestMax -> _uiState.update { it.copy(restMaxSec = event.value) }
            is SlotEditorEvent.RestAsNeeded -> _uiState.update { it.copy(restAsNeeded = event.value) }
            is SlotEditorEvent.Optional -> _uiState.update { it.copy(isOptional = event.value) }
            is SlotEditorEvent.SkipReason -> _uiState.update { it.copy(skipReasonLabel = event.value) }
            is SlotEditorEvent.Progression -> _uiState.update { it.copy(progressionRule = event.value) }
            is SlotEditorEvent.IncrementOverride -> _uiState.update { it.copy(incrementOverrideKg = event.value) }
            is SlotEditorEvent.Notes -> _uiState.update { it.copy(notes = event.value) }
            is SlotEditorEvent.TargetStep -> _uiState.update { it.copy(targetSkillStepId = event.id) }
            is SlotEditorEvent.ToggleAlternative -> toggleAlternative(event.exerciseId)
            is SlotEditorEvent.PairWith -> pair(event.neighbourId)
            SlotEditorEvent.Save -> save()
            SlotEditorEvent.DismissError -> _uiState.update { it.copy(validationError = null) }
        }
    }

    private suspend fun reload() {
        val slot = routines.getSlot(slotId) ?: return
        val library = exercises.observeActive().first().map { ExerciseChoice(it.id, it.name, it.skillId) }
        val skillId = library.firstOrNull { it.id == slot.exerciseId }?.skillId
        val steps = skillId?.let { skills.getSteps(it) }.orEmpty().map { StepChoice(it.id, it.name) }
        val alternatives = routines.observeAlternatives(slotId).first().map { it.exerciseId }.toSet()
        val daySlots = routines.observeSlots(slot.dayId).first()
        val index = daySlots.indexOfFirst { it.id == slotId }
        val neighbours = listOfNotNull(daySlots.getOrNull(index - 1), daySlots.getOrNull(index + 1))
        val neighbourRows = neighbours.map { neighbour ->
            NeighbourSlot(
                id = neighbour.id,
                name = library.firstOrNull { it.id == neighbour.exerciseId }?.name.orEmpty(),
                sameGroup = slot.supersetGroup != null && slot.supersetGroup == neighbour.supersetGroup,
            )
        }
        _uiState.update { state ->
            state.copy(
                ready = true,
                exerciseId = slot.exerciseId,
                exercises = library,
                category = slot.category,
                metricType = slot.metricType,
                setsMin = slot.setsMin,
                setsMax = slot.setsMax,
                repsLow = slot.repsLow,
                repsHigh = slot.repsHigh,
                isAmrap = slot.isAmrap,
                holdTargetSec = slot.holdTargetSec,
                holdTargetMaxSec = slot.holdTargetMaxSec,
                blockDurationSec = slot.blockDurationSec,
                restMinSec = slot.restMinSec,
                restMaxSec = slot.restMaxSec,
                restAsNeeded = slot.restAsNeeded,
                isOptional = slot.isOptional,
                skipReasonLabel = slot.skipReasonLabel.orEmpty(),
                progressionRule = slot.progressionRule,
                incrementOverrideKg = slot.incrementOverrideKg,
                notes = slot.notes.orEmpty(),
                targetSkillStepId = slot.targetSkillStepId,
                skillSteps = steps,
                alternativeExerciseIds = alternatives,
                neighbours = neighbourRows,
                supersetGroup = slot.supersetGroup,
                paired = neighbourRows.any { it.sameGroup },
            )
        }
    }

    private fun selectExercise(id: Long) {
        viewModelScope.launch {
            val choice = _uiState.value.exercises.firstOrNull { it.id == id } ?: return@launch
            val steps = choice.skillId?.let { skills.getSteps(it) }.orEmpty().map { StepChoice(it.id, it.name) }
            _uiState.update {
                it.copy(
                    exerciseId = id,
                    skillSteps = steps,
                    targetSkillStepId = it.targetSkillStepId?.takeIf { stepId -> steps.any { step -> step.id == stepId } },
                )
            }
        }
    }

    private fun toggleAlternative(exerciseId: Long) {
        viewModelScope.launch {
            val existing = routines.observeAlternatives(slotId).first()
            val match = existing.firstOrNull { it.exerciseId == exerciseId }
            if (match != null) {
                routines.deleteAlternative(match.id)
            } else if (exerciseId != _uiState.value.exerciseId) {
                routines.insertAlternative(SlotAlternativeEntity(slotId = slotId, exerciseId = exerciseId))
            }
            val ids = routines.observeAlternatives(slotId).first().map { it.exerciseId }.toSet()
            _uiState.update { it.copy(alternativeExerciseIds = ids) }
        }
    }

    private fun pair(neighbourId: Long) {
        viewModelScope.launch {
            val current = routines.getSlot(slotId) ?: return@launch
            val neighbour = routines.getSlot(neighbourId) ?: return@launch
            if (current.dayId != neighbour.dayId) return@launch
            val slots = routines.observeSlots(current.dayId).first()
            val shared = current.supersetGroup != null && current.supersetGroup == neighbour.supersetGroup
            val group = if (shared) {
                null
            } else {
                current.supersetGroup
                    ?: neighbour.supersetGroup
                    ?: ((slots.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1)
            }
            routines.updateSlot(current.copy(supersetGroup = group))
            routines.updateSlot(neighbour.copy(supersetGroup = group))
            val daySlots = routines.observeSlots(current.dayId).first()
            val index = daySlots.indexOfFirst { it.id == slotId }
            val adjacent = listOfNotNull(daySlots.getOrNull(index - 1), daySlots.getOrNull(index + 1))
            val library = _uiState.value.exercises
            val neighbourRows = adjacent.map { item ->
                NeighbourSlot(
                    id = item.id,
                    name = library.firstOrNull { it.id == item.exerciseId }?.name.orEmpty(),
                    sameGroup = group != null && item.supersetGroup == group,
                )
            }
            _uiState.update {
                it.copy(
                    neighbours = neighbourRows,
                    supersetGroup = group,
                    paired = neighbourRows.any { row -> row.sameGroup },
                )
            }
        }
    }

    private fun save() {
        val state = _uiState.value
        if (!state.ready) return
        val error = validate(state)
        if (error != null) {
            _uiState.update { it.copy(validationError = error) }
            return
        }
        viewModelScope.launch {
            val slot = routines.getSlot(slotId) ?: return@launch
            routines.updateSlot(state.applyTo(slot))
            _uiState.update { it.copy(validationError = null) }
        }
    }
}

internal fun validate(state: SlotEditorUiState): SlotValidationError? = when {
    state.setsMin > state.setsMax -> SlotValidationError.SETS
    state.repsLow != null && state.repsHigh != null && state.repsLow > state.repsHigh -> SlotValidationError.REPS
    state.restMinSec != null && state.restMaxSec != null && state.restMinSec > state.restMaxSec -> SlotValidationError.REST
    state.holdTargetSec != null && state.holdTargetMaxSec != null &&
        state.holdTargetSec > state.holdTargetMaxSec -> SlotValidationError.HOLD
    else -> null
}

private fun SlotEditorUiState.applyTo(slot: RoutineSlotEntity): RoutineSlotEntity = slot.copy(
    exerciseId = exerciseId,
    category = category,
    metricType = metricType,
    setsMin = setsMin,
    setsMax = setsMax,
    repsLow = repsLow,
    repsHigh = repsHigh,
    isAmrap = isAmrap,
    holdTargetSec = holdTargetSec,
    holdTargetMaxSec = holdTargetMaxSec,
    blockDurationSec = blockDurationSec,
    restMinSec = restMinSec,
    restMaxSec = restMaxSec,
    restAsNeeded = restAsNeeded,
    isOptional = isOptional,
    skipReasonLabel = skipReasonLabel.ifBlank { null },
    targetSkillStepId = targetSkillStepId,
    progressionRule = progressionRule,
    incrementOverrideKg = incrementOverrideKg,
    notes = notes.ifBlank { null },
)
