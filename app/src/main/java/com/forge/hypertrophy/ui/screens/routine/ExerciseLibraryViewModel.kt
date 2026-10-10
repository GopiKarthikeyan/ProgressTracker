package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.transfer.LibraryCatalogImporter
import com.forge.hypertrophy.data.transfer.LibraryCatalogProvider
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

data class BuiltinCatalogMessage(
    val addedExercises: Int,
    val addedSkills: Int,
)

data class ExerciseLibraryUiState(
    val rows: List<LibraryRow> = emptyList(),
    val error: LibraryError? = null,
    val builtinMessage: BuiltinCatalogMessage? = null,
)

sealed interface ExerciseLibraryEvent {
    data class Archive(val id: Long) : ExerciseLibraryEvent
    data class HardDelete(val id: Long) : ExerciseLibraryEvent
    data object AddBuiltIn : ExerciseLibraryEvent
    data object DismissError : ExerciseLibraryEvent
    data object DismissBuiltinMessage : ExerciseLibraryEvent
}

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val exercises: ExerciseRepository,
    private val catalog: LibraryCatalogProvider,
    private val catalogImporter: LibraryCatalogImporter,
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
            ExerciseLibraryEvent.AddBuiltIn -> addBuiltIn()
            ExerciseLibraryEvent.DismissError -> _uiState.update { it.copy(error = null) }
            ExerciseLibraryEvent.DismissBuiltinMessage -> _uiState.update { it.copy(builtinMessage = null) }
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
