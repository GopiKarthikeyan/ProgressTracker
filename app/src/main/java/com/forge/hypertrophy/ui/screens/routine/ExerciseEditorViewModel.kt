package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.ExerciseEntity
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.domain.model.Equipment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SkillChoice(
    val id: Long,
    val name: String,
)

data class ExerciseEditorUiState(
    val ready: Boolean = false,
    val name: String = "",
    val equipment: Equipment = Equipment.BARBELL,
    val loadIncrementKg: Double = 2.5,
    val barWeightKg: Double? = null,
    val isUnilateral: Boolean = false,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val knownMuscles: List<String> = emptyList(),
    val primaryDraft: String = "",
    val secondaryDraft: String = "",
    val setupNotes: String = "",
    val skillId: Long? = null,
    val skills: List<SkillChoice> = emptyList(),
    val saved: Boolean = false,
)

sealed interface ExerciseEditorEvent {
    data class Name(val value: String) : ExerciseEditorEvent
    data class EquipmentChanged(val value: Equipment) : ExerciseEditorEvent
    data class Increment(val value: Double) : ExerciseEditorEvent
    data class BarWeight(val value: Double?) : ExerciseEditorEvent
    data class Unilateral(val value: Boolean) : ExerciseEditorEvent
    data class PrimaryDraft(val value: String) : ExerciseEditorEvent
    data class SecondaryDraft(val value: String) : ExerciseEditorEvent
    data class AddPrimary(val token: String) : ExerciseEditorEvent
    data class AddSecondary(val token: String) : ExerciseEditorEvent
    data class RemovePrimary(val token: String) : ExerciseEditorEvent
    data class RemoveSecondary(val token: String) : ExerciseEditorEvent
    data class SetupNotes(val value: String) : ExerciseEditorEvent
    data class Skill(val id: Long?) : ExerciseEditorEvent
    data object Save : ExerciseEditorEvent
}

@HiltViewModel
class ExerciseEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exercises: ExerciseRepository,
    private val skills: SkillRepository,
) : ViewModel() {
    private val exerciseId: Long = savedStateHandle.get<Long>("exerciseId") ?: 0L
    private val _uiState = MutableStateFlow(ExerciseEditorUiState())
    val uiState: StateFlow<ExerciseEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val library = exercises.observeActive().first()
            val skillRows = skills.observeActive().first().map { SkillChoice(it.id, it.name) }
            val tokens = (library.flatMap { it.primaryMuscleGroups + it.secondaryMuscleGroups })
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
            val existing = if (exerciseId == 0L) null else exercises.get(exerciseId)
            _uiState.update {
                it.copy(
                    ready = true,
                    name = existing?.name.orEmpty(),
                    equipment = existing?.equipment ?: Equipment.BARBELL,
                    loadIncrementKg = existing?.loadIncrementKg ?: 2.5,
                    barWeightKg = existing?.barWeightKg,
                    isUnilateral = existing?.isUnilateral == true,
                    primaryMuscles = existing?.primaryMuscleGroups.orEmpty(),
                    secondaryMuscles = existing?.secondaryMuscleGroups.orEmpty(),
                    knownMuscles = tokens,
                    setupNotes = existing?.setupNotes.orEmpty(),
                    skillId = existing?.skillId,
                    skills = skillRows,
                )
            }
        }
    }

    fun onEvent(event: ExerciseEditorEvent) {
        when (event) {
            is ExerciseEditorEvent.Name -> _uiState.update { it.copy(name = event.value) }
            is ExerciseEditorEvent.EquipmentChanged -> _uiState.update { it.copy(equipment = event.value) }
            is ExerciseEditorEvent.Increment -> _uiState.update { it.copy(loadIncrementKg = event.value) }
            is ExerciseEditorEvent.BarWeight -> _uiState.update { it.copy(barWeightKg = event.value) }
            is ExerciseEditorEvent.Unilateral -> _uiState.update { it.copy(isUnilateral = event.value) }
            is ExerciseEditorEvent.PrimaryDraft -> _uiState.update { it.copy(primaryDraft = event.value) }
            is ExerciseEditorEvent.SecondaryDraft -> _uiState.update { it.copy(secondaryDraft = event.value) }
            is ExerciseEditorEvent.AddPrimary -> addMuscle(event.token, primary = true)
            is ExerciseEditorEvent.AddSecondary -> addMuscle(event.token, primary = false)
            is ExerciseEditorEvent.RemovePrimary -> _uiState.update {
                it.copy(primaryMuscles = it.primaryMuscles.filterNot { token -> token == event.token })
            }
            is ExerciseEditorEvent.RemoveSecondary -> _uiState.update {
                it.copy(secondaryMuscles = it.secondaryMuscles.filterNot { token -> token == event.token })
            }
            is ExerciseEditorEvent.SetupNotes -> _uiState.update { it.copy(setupNotes = event.value) }
            is ExerciseEditorEvent.Skill -> _uiState.update { it.copy(skillId = event.id) }
            ExerciseEditorEvent.Save -> save()
        }
    }

    private fun addMuscle(token: String, primary: Boolean) {
        val cleaned = token.trim()
        if (cleaned.isEmpty()) return
        _uiState.update { state ->
            val known = if (cleaned in state.knownMuscles) state.knownMuscles else state.knownMuscles + cleaned
            if (primary) {
                state.copy(
                    primaryMuscles = (state.primaryMuscles + cleaned).distinct(),
                    knownMuscles = known,
                    primaryDraft = "",
                )
            } else {
                state.copy(
                    secondaryMuscles = (state.secondaryMuscles + cleaned).distinct(),
                    knownMuscles = known,
                    secondaryDraft = "",
                )
            }
        }
    }

    private fun save() {
        val state = _uiState.value
        val name = state.name.trim()
        if (!state.ready || name.isEmpty() || state.saved) return
        viewModelScope.launch {
            val entity = ExerciseEntity(
                id = exerciseId,
                name = name,
                equipment = state.equipment,
                barWeightKg = state.barWeightKg,
                loadIncrementKg = state.loadIncrementKg,
                isUnilateral = state.isUnilateral,
                skillId = state.skillId,
                primaryMuscleGroups = state.primaryMuscles,
                secondaryMuscleGroups = state.secondaryMuscles,
                setupNotes = state.setupNotes,
                archivedAt = if (exerciseId == 0L) null else exercises.get(exerciseId)?.archivedAt,
            )
            if (exerciseId == 0L) exercises.insert(entity.copy(id = 0)) else exercises.update(entity)
            _uiState.update { it.copy(saved = true) }
        }
    }
}
