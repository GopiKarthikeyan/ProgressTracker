package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.storage.ProgramDocumentStore
import com.forge.hypertrophy.data.transfer.ProgramExporter
import com.forge.hypertrophy.domain.model.ScheduleMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgramRow(
    val id: Long,
    val name: String,
    val isActive: Boolean,
)

enum class ProgramListNotice {
    EXPORTED,
    EXPORT_FAILED,
}

data class ProgramListUiState(
    val programs: List<ProgramRow> = emptyList(),
    val draftName: String = "",
    val notice: ProgramListNotice? = null,
)

sealed interface ProgramListEvent {
    data class DraftName(val value: String) : ProgramListEvent
    data object Add : ProgramListEvent
    data class SetActive(val id: Long) : ProgramListEvent
    data class Delete(val id: Long) : ProgramListEvent

    /** Writes [programId] as a document to the URI the user picked. */
    data class Export(val programId: Long, val uri: String) : ProgramListEvent
    data object DismissNotice : ProgramListEvent
}

@HiltViewModel
class ProgramListViewModel @Inject constructor(
    private val programs: ProgramRepository,
    private val exporter: ProgramExporter,
    private val documents: ProgramDocumentStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProgramListUiState())
    val uiState: StateFlow<ProgramListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            programs.observeAll().collect { rows ->
                _uiState.update { state ->
                    state.copy(
                        programs = rows.map { ProgramRow(it.id, it.name, it.isActive) },
                    )
                }
            }
        }
    }

    fun onEvent(event: ProgramListEvent) {
        when (event) {
            is ProgramListEvent.DraftName -> _uiState.update { it.copy(draftName = event.value) }
            ProgramListEvent.Add -> add()
            is ProgramListEvent.SetActive -> viewModelScope.launch { programs.setActive(event.id) }
            is ProgramListEvent.Delete -> viewModelScope.launch { programs.delete(event.id) }
            is ProgramListEvent.Export -> export(event.programId, event.uri)
            ProgramListEvent.DismissNotice -> _uiState.update { it.copy(notice = null) }
        }
    }

    private fun export(programId: Long, uri: String) {
        viewModelScope.launch {
            val notice = runCatching { documents.write(uri, exporter.export(programId)) }
                .fold({ ProgramListNotice.EXPORTED }, { ProgramListNotice.EXPORT_FAILED })
            _uiState.update { it.copy(notice = notice) }
        }
    }

    private fun add() {
        val name = _uiState.value.draftName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val id = programs.insert(
                ProgramEntity(
                    name = name,
                    scheduleMode = ScheduleMode.FIXED,
                    rollingSequence = 0,
                    deloadActive = false,
                    deloadStartedOn = null,
                    isActive = false,
                ),
            )
            if (programs.observeActive().first() == null) {
                programs.setActive(id)
            }
            _uiState.update { it.copy(draftName = "") }
        }
    }
}
