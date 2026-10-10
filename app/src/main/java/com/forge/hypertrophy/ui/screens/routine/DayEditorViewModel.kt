package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.CardioPlanEntity
import com.forge.hypertrophy.data.entity.ChecklistItemEntity
import com.forge.hypertrophy.data.entity.RoutineSlotEntity
import com.forge.hypertrophy.data.entity.SlotBaselineEntity
import com.forge.hypertrophy.data.repository.BaselineRepository
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.domain.model.CardioType
import com.forge.hypertrophy.domain.model.ChecklistPhase
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.routine.slotPrescriptionSummary
import com.forge.hypertrophy.domain.routine.slotRowDetailLine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChecklistRow(
    val id: Long,
    val phase: ChecklistPhase,
    val text: String,
    val reps: Int?,
    val seconds: Int?,
)

data class SlotRow(
    val id: Long,
    val title: String,
    /** Prescription and optional baseline, e.g. `3×8–12 · 60 kg`. */
    val detail: String,
    val baselineWeightKg: Double?,
    val loadIncrementKg: Double,
)

data class DayEditorUiState(
    val ready: Boolean = false,
    val label: String = "",
    val weekday: Int? = null,
    val prepMinutes: Int? = null,
    val cooldownMinutes: Int? = null,
    val prepItems: List<ChecklistRow> = emptyList(),
    val cooldownItems: List<ChecklistRow> = emptyList(),
    val slots: List<SlotRow> = emptyList(),
    val cardioType: CardioType? = null,
    val cardioLabel: String = "",
    val cardioDistanceM: Int? = null,
    val cardioOptional: Boolean = false,
    val needsExercise: Boolean = false,
)

sealed interface DayEditorEvent {
    data class Label(val value: String) : DayEditorEvent
    data class Weekday(val isoDay: Int?) : DayEditorEvent
    data class PrepMinutes(val value: Int?) : DayEditorEvent
    data class CooldownMinutes(val value: Int?) : DayEditorEvent
    data class AddChecklist(val phase: ChecklistPhase) : DayEditorEvent
    data class ChecklistText(val id: Long, val value: String) : DayEditorEvent
    data class ChecklistReps(val id: Long, val value: Int?) : DayEditorEvent
    data class ChecklistSeconds(val id: Long, val value: Int?) : DayEditorEvent
    data class DeleteChecklist(val id: Long) : DayEditorEvent
    data class CardioTypeChanged(val type: CardioType?) : DayEditorEvent
    data class CardioLabel(val value: String) : DayEditorEvent
    data class CardioDistance(val value: Int?) : DayEditorEvent
    data class CardioOptional(val value: Boolean) : DayEditorEvent
    data object AddSlot : DayEditorEvent
    data class DeleteSlot(val id: Long) : DayEditorEvent
    data class MoveSlot(val from: Int, val to: Int) : DayEditorEvent
    data class StepBaselineWeight(val slotId: Long, val direction: Int) : DayEditorEvent
    data object Save : DayEditorEvent
    data object DismissNeedsExercise : DayEditorEvent
}

@HiltViewModel
class DayEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val baselines: BaselineRepository,
    private val clock: Clock,
) : ViewModel() {
    private val dayId: Long = checkNotNull(savedStateHandle.get<Long>("dayId"))
    private val _uiState = MutableStateFlow(DayEditorUiState())
    val uiState: StateFlow<DayEditorUiState> = _uiState.asStateFlow()
    private var loaded = false

    init {
        viewModelScope.launch {
            val day = routines.getDay(dayId) ?: return@launch
            val cardio = routines.observeCardioPlan(dayId).first()
            _uiState.update {
                it.copy(
                    ready = true,
                    label = day.label,
                    weekday = day.dayOfWeek,
                    prepMinutes = day.prepDurationMin,
                    cooldownMinutes = day.cooldownDurationMin,
                    cardioType = cardio?.type,
                    cardioLabel = cardio?.label.orEmpty(),
                    cardioDistanceM = cardio?.targetDistanceM,
                    cardioOptional = cardio?.isOptional == true,
                )
            }
            loaded = true
        }
        viewModelScope.launch {
            routines.observeChecklist(dayId).collect { items ->
                _uiState.update { state ->
                    state.copy(
                        prepItems = items.filter { it.phase == ChecklistPhase.PREP }.map { it.toRow() },
                        cooldownItems = items.filter { it.phase == ChecklistPhase.COOLDOWN }.map { it.toRow() },
                    )
                }
            }
        }
        viewModelScope.launch {
            routines.observeSlots(dayId).collect { slots ->
                _uiState.update { it.copy(slots = buildSlotRows(slots)) }
            }
        }
    }

    fun onEvent(event: DayEditorEvent) {
        when (event) {
            is DayEditorEvent.Label -> _uiState.update { it.copy(label = event.value) }
            is DayEditorEvent.Weekday -> _uiState.update { it.copy(weekday = event.isoDay) }
            is DayEditorEvent.PrepMinutes -> _uiState.update { it.copy(prepMinutes = event.value) }
            is DayEditorEvent.CooldownMinutes -> _uiState.update { it.copy(cooldownMinutes = event.value) }
            is DayEditorEvent.AddChecklist -> addChecklist(event.phase)
            is DayEditorEvent.ChecklistText -> updateChecklist(event.id) { it.copy(text = event.value) }
            is DayEditorEvent.ChecklistReps -> updateChecklist(event.id) { it.copy(reps = event.value) }
            is DayEditorEvent.ChecklistSeconds -> updateChecklist(event.id) { it.copy(seconds = event.value) }
            is DayEditorEvent.DeleteChecklist -> viewModelScope.launch { routines.deleteChecklist(event.id) }
            is DayEditorEvent.CardioTypeChanged -> _uiState.update { it.copy(cardioType = event.type) }
            is DayEditorEvent.CardioLabel -> _uiState.update { it.copy(cardioLabel = event.value) }
            is DayEditorEvent.CardioDistance -> _uiState.update { it.copy(cardioDistanceM = event.value) }
            is DayEditorEvent.CardioOptional -> _uiState.update { it.copy(cardioOptional = event.value) }
            DayEditorEvent.AddSlot -> addSlot()
            is DayEditorEvent.DeleteSlot -> viewModelScope.launch { routines.deleteSlot(event.id) }
            is DayEditorEvent.MoveSlot -> moveSlot(event.from, event.to)
            is DayEditorEvent.StepBaselineWeight -> stepBaselineWeight(event.slotId, event.direction)
            DayEditorEvent.Save -> save()
            DayEditorEvent.DismissNeedsExercise -> _uiState.update { it.copy(needsExercise = false) }
        }
    }

    private suspend fun buildSlotRows(slots: List<RoutineSlotEntity>): List<SlotRow> {
        val weights = baselines.forSlots(slots.map { it.id })
            .associate { it.slotId to it.weightKg }
        return slots.map { slot ->
            val exercise = exercises.get(slot.exerciseId)
            val increment = slot.incrementOverrideKg ?: exercise?.loadIncrementKg ?: 2.5
            val prescription = slotPrescriptionSummary(
                setsMin = slot.setsMin,
                setsMax = slot.setsMax,
                repsLow = slot.repsLow,
                repsHigh = slot.repsHigh,
                isAmrap = slot.isAmrap,
                metricType = slot.metricType,
                holdTargetSec = slot.holdTargetSec,
            )
            SlotRow(
                id = slot.id,
                title = exercise?.name.orEmpty(),
                detail = slotRowDetailLine(prescription, weights[slot.id]),
                baselineWeightKg = weights[slot.id],
                loadIncrementKg = increment,
            )
        }
    }

    private fun stepBaselineWeight(slotId: Long, direction: Int) {
        viewModelScope.launch {
            val slot = routines.getSlot(slotId) ?: return@launch
            val exercise = exercises.get(slot.exerciseId)
            val increment = slot.incrementOverrideKg ?: exercise?.loadIncrementKg ?: 2.5
            val existing = baselines.forSlot(slotId)
            val current = existing?.weightKg ?: 0.0
            val next = (current + direction * increment).coerceAtLeast(0.0)
            baselines.save(
                SlotBaselineEntity(
                    slotId = slotId,
                    weightKg = next,
                    repsHint = existing?.repsHint ?: slot.repsLow ?: slot.repsHigh,
                    setAt = clock.instant(),
                ),
            )
            val slots = routines.observeSlots(dayId).first()
            _uiState.update { it.copy(slots = buildSlotRows(slots)) }
        }
    }

    private fun addChecklist(phase: ChecklistPhase) {
        viewModelScope.launch {
            routines.insertChecklist(
                ChecklistItemEntity(
                    dayId = dayId,
                    phase = phase,
                    text = "",
                    reps = null,
                    seconds = null,
                ),
            )
        }
    }

    private fun updateChecklist(id: Long, transform: (ChecklistItemEntity) -> ChecklistItemEntity) {
        viewModelScope.launch {
            val item = routines.observeChecklist(dayId).first().firstOrNull { it.id == id } ?: return@launch
            routines.updateChecklist(transform(item))
        }
    }

    private fun addSlot() {
        viewModelScope.launch {
            val exercise = exercises.observeActive().first().firstOrNull()
            if (exercise == null) {
                _uiState.update { it.copy(needsExercise = true) }
                return@launch
            }
            val slots = routines.observeSlots(dayId).first()
            routines.insertSlot(
                RoutineSlotEntity(
                    dayId = dayId,
                    exerciseId = exercise.id,
                    category = SlotCategory.COMPOUND,
                    sortOrder = slots.size,
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
        }
    }

    private fun moveSlot(from: Int, to: Int) {
        viewModelScope.launch {
            val slots = routines.observeSlots(dayId).first()
            val ordered = moveItem(slots.map { it.id }, from, to) ?: return@launch
            routines.reorderSlots(dayId, ordered)
        }
    }

    private fun save() {
        val state = _uiState.value
        if (!loaded) return
        viewModelScope.launch {
            val day = routines.getDay(dayId) ?: return@launch
            routines.updateDay(
                day.copy(
                    label = state.label,
                    dayOfWeek = state.weekday,
                    prepDurationMin = state.prepMinutes,
                    cooldownDurationMin = state.cooldownMinutes,
                ),
            )
            val type = state.cardioType
            if (type != null) {
                val existing = routines.observeCardioPlan(dayId).first()
                routines.upsertCardioPlan(
                    CardioPlanEntity(
                        id = existing?.id ?: 0,
                        dayId = dayId,
                        type = type,
                        targetDistanceM = state.cardioDistanceM,
                        isOptional = state.cardioOptional,
                        label = state.cardioLabel,
                    ),
                )
            }
        }
    }
}

private fun ChecklistItemEntity.toRow() = ChecklistRow(id, phase, text, reps, seconds)
