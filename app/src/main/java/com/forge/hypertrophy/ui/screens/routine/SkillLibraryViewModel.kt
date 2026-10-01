package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.repository.SkillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SkillLibraryUiState(
    val rows: List<LibraryRow> = emptyList(),
    val error: LibraryError? = null,
)

sealed interface SkillLibraryEvent {
    data class Archive(val id: Long) : SkillLibraryEvent
    data class HardDelete(val id: Long) : SkillLibraryEvent
    data object DismissError : SkillLibraryEvent
}

@HiltViewModel
class SkillLibraryViewModel @Inject constructor(
    private val skills: SkillRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SkillLibraryUiState())
    val uiState: StateFlow<SkillLibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            skills.observeActive().collect { rows ->
                val referenced = skills.referencedIds()
                _uiState.update { state ->
                    state.copy(
                        rows = rows.map { LibraryRow(it.id, it.name, canHardDelete = it.id !in referenced) },
                    )
                }
            }
        }
    }

    fun onEvent(event: SkillLibraryEvent) {
        when (event) {
            is SkillLibraryEvent.Archive -> viewModelScope.launch { skills.archive(event.id) }
            is SkillLibraryEvent.HardDelete -> hardDelete(event.id)
            SkillLibraryEvent.DismissError -> _uiState.update { it.copy(error = null) }
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
