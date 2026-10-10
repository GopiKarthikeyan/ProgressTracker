package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.SkillEntity
import com.forge.hypertrophy.data.entity.SkillStepEntity
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.domain.skill.SkillProgressRef
import com.forge.hypertrophy.domain.skill.SkillProgressSummary
import com.forge.hypertrophy.domain.skill.SkillStepTargets
import com.forge.hypertrophy.domain.skill.skillProgressSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SkillStepRow(
    val id: Long,
    val name: String,
    val stage1TotalSec: Int,
    val stage2TotalLowSec: Int,
    val stage2TotalHighSec: Int,
    val stage3UnbrokenSec: Int,
    val isCurrent: Boolean = false,
)

data class SkillEditorUiState(
    val ready: Boolean = false,
    val name: String = "",
    val steps: List<SkillStepRow> = emptyList(),
    val progress: SkillProgressSummary? = null,
    val saved: Boolean = false,
)

sealed interface SkillEditorEvent {
    data class Name(val value: String) : SkillEditorEvent
    data object SaveName : SkillEditorEvent
    data object AddStep : SkillEditorEvent
    data class StepName(val id: Long, val value: String) : SkillEditorEvent
    data class Stage1(val id: Long, val value: Int) : SkillEditorEvent
    data class Stage2Low(val id: Long, val value: Int) : SkillEditorEvent
    data class Stage2High(val id: Long, val value: Int) : SkillEditorEvent
    data class Stage3(val id: Long, val value: Int) : SkillEditorEvent
    data class DeleteStep(val id: Long) : SkillEditorEvent
}

@HiltViewModel
class SkillEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val skills: SkillRepository,
) : ViewModel() {
    private var skillId: Long = savedStateHandle.get<Long>("skillId") ?: 0L
    private val _uiState = MutableStateFlow(SkillEditorUiState())
    val uiState: StateFlow<SkillEditorUiState> = _uiState.asStateFlow()

    init {
        if (skillId != 0L) {
            viewModelScope.launch { load() }
        } else {
            _uiState.update { it.copy(ready = true) }
        }
    }

    fun onEvent(event: SkillEditorEvent) {
        when (event) {
            is SkillEditorEvent.Name -> _uiState.update { it.copy(name = event.value) }
            SkillEditorEvent.SaveName -> saveName()
            SkillEditorEvent.AddStep -> addStep()
            is SkillEditorEvent.StepName -> updateStep(event.id) { it.copy(name = event.value) }
            is SkillEditorEvent.Stage1 -> updateStep(event.id) { it.copy(stage1TotalSec = event.value) }
            is SkillEditorEvent.Stage2Low -> updateStep(event.id) { it.copy(stage2TotalLowSec = event.value) }
            is SkillEditorEvent.Stage2High -> updateStep(event.id) { it.copy(stage2TotalHighSec = event.value) }
            is SkillEditorEvent.Stage3 -> updateStep(event.id) { it.copy(stage3UnbrokenSec = event.value) }
            is SkillEditorEvent.DeleteStep -> viewModelScope.launch {
                skills.deleteStep(event.id)
                load()
            }
        }
    }

    private suspend fun load() {
        val skill = skills.get(skillId) ?: return
        val entities = skills.getSteps(skillId)
        val progressEntity = skills.getProgress(skillId)
        val summary = skillProgressSummary(
            entities.map {
                SkillStepTargets(
                    id = it.id,
                    name = it.name,
                    stage1TotalSec = it.stage1TotalSec,
                    stage2TotalHighSec = it.stage2TotalHighSec,
                    stage3UnbrokenSec = it.stage3UnbrokenSec,
                )
            },
            progressEntity?.let { SkillProgressRef(it.currentStepId, it.stage) },
        )
        val currentStepId = summary?.let { entities.getOrNull(it.stepIndex)?.id }
        val steps = entities.map {
            SkillStepRow(
                id = it.id,
                name = it.name,
                stage1TotalSec = it.stage1TotalSec,
                stage2TotalLowSec = it.stage2TotalLowSec,
                stage2TotalHighSec = it.stage2TotalHighSec,
                stage3UnbrokenSec = it.stage3UnbrokenSec,
                isCurrent = it.id == currentStepId,
            )
        }
        _uiState.update {
            it.copy(ready = true, name = skill.name, steps = steps, progress = summary)
        }
    }

    private fun saveName() {
        val name = _uiState.value.name.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            if (skillId == 0L) {
                skillId = skills.insert(SkillEntity(name = name, archivedAt = null))
            } else {
                val current = skills.get(skillId) ?: return@launch
                skills.update(current.copy(name = name))
            }
            _uiState.update { it.copy(saved = true, name = name) }
        }
    }

    private fun addStep() {
        viewModelScope.launch {
            if (skillId == 0L) {
                val name = _uiState.value.name.trim()
                if (name.isEmpty()) return@launch
                skillId = skills.insert(SkillEntity(name = name, archivedAt = null))
            }
            val steps = skills.getSteps(skillId)
            skills.insertStep(SkillStepEntity(skillId = skillId, sortOrder = steps.size))
            load()
        }
    }

    private fun updateStep(id: Long, transform: (SkillStepEntity) -> SkillStepEntity) {
        viewModelScope.launch {
            val current = skills.getSteps(skillId).firstOrNull { it.id == id } ?: return@launch
            skills.updateStep(transform(current))
            load()
        }
    }
}
