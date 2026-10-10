package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.data.transfer.LibraryCatalogImporter
import com.forge.hypertrophy.data.transfer.LibraryCatalogProvider
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

data class SkillLibraryRow(
    val id: Long,
    val name: String,
    val canHardDelete: Boolean,
    val progress: SkillProgressSummary?,
)

data class SkillLibraryUiState(
    val rows: List<SkillLibraryRow> = emptyList(),
    val error: LibraryError? = null,
    val builtinMessage: BuiltinCatalogMessage? = null,
)

sealed interface SkillLibraryEvent {
    data class Archive(val id: Long) : SkillLibraryEvent
    data class HardDelete(val id: Long) : SkillLibraryEvent
    data object AddBuiltIn : SkillLibraryEvent
    data object DismissError : SkillLibraryEvent
    data object DismissBuiltinMessage : SkillLibraryEvent
}

@HiltViewModel
class SkillLibraryViewModel @Inject constructor(
    private val skills: SkillRepository,
    private val catalog: LibraryCatalogProvider,
    private val catalogImporter: LibraryCatalogImporter,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SkillLibraryUiState())
    val uiState: StateFlow<SkillLibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            skills.observeActive().collect { rows ->
                val referenced = skills.referencedIds()
                val progressBySkill = skills.allProgress().associateBy { it.skillId }
                _uiState.update { state ->
                    state.copy(
                        rows = rows.map { skill ->
                            val steps = skills.getSteps(skill.id).map {
                                SkillStepTargets(
                                    id = it.id,
                                    name = it.name,
                                    stage1TotalSec = it.stage1TotalSec,
                                    stage2TotalHighSec = it.stage2TotalHighSec,
                                    stage3UnbrokenSec = it.stage3UnbrokenSec,
                                )
                            }
                            val progress = progressBySkill[skill.id]?.let {
                                SkillProgressRef(it.currentStepId, it.stage)
                            }
                            SkillLibraryRow(
                                id = skill.id,
                                name = skill.name,
                                canHardDelete = skill.id !in referenced,
                                progress = skillProgressSummary(steps, progress),
                            )
                        },
                    )
                }
            }
        }
    }

    fun onEvent(event: SkillLibraryEvent) {
        when (event) {
            is SkillLibraryEvent.Archive -> viewModelScope.launch { skills.archive(event.id) }
            is SkillLibraryEvent.HardDelete -> hardDelete(event.id)
            SkillLibraryEvent.AddBuiltIn -> addBuiltIn()
            SkillLibraryEvent.DismissError -> _uiState.update { it.copy(error = null) }
            SkillLibraryEvent.DismissBuiltinMessage -> _uiState.update { it.copy(builtinMessage = null) }
        }
    }

    private fun addBuiltIn() {
        viewModelScope.launch {
            val result = catalogImporter.import(catalog.catalog())
            _uiState.update {
                it.copy(
                    builtinMessage = BuiltinCatalogMessage(result.addedExercises, result.addedSkills),
                )
            }
        }
    }

    private fun hardDelete(id: Long) {
        viewModelScope.launch {
            try {
                skills.delete(id)
            } catch (error: Exception) {
                if (error.isForeignKeyRestriction()) {
                    _uiState.update { it.copy(error = LibraryError.RESTRICTED) }
                } else {
                    throw error
                }
            }
        }
    }
}
