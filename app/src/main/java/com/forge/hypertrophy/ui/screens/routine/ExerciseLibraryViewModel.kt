package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryError {
    RESTRICTED,
}

data class LibraryRow(
    val id: Long,
    val name: String,
    val canHardDelete: Boolean,
)

data class ExerciseLibraryUiState(
    val rows: List<LibraryRow> = emptyList(),
    val error: LibraryError? = null,
)

sealed interface ExerciseLibraryEvent {
    data class Archive(val id: Long) : ExerciseLibraryEvent
    data class HardDelete(val id: Long) : ExerciseLibraryEvent
    data object DismissError : ExerciseLibraryEvent
}

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val exercises: ExerciseRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExerciseLibraryUiState())
    val uiState: StateFlow<ExerciseLibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            exercises.observeActive().collect { rows ->
                val referenced = exercises.referencedIds()
                _uiState.update { state ->
                    state.copy(
                        rows = rows.map { LibraryRow(it.id, it.name, canHardDelete = it.id !in referenced) },
                    )
                }
            }
        }
    }

    fun onEvent(event: ExerciseLibraryEvent) {
        when (event) {
            is ExerciseLibraryEvent.Archive -> viewModelScope.launch { exercises.archive(event.id) }
            is ExerciseLibraryEvent.HardDelete -> hardDelete(event.id)
            ExerciseLibraryEvent.DismissError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun hardDelete(id: Long) {
        viewModelScope.launch {
            try {
                exercises.delete(id)
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
