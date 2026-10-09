package com.forge.hypertrophy.ui.screens.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DayRow(
    val id: Long,
    val label: String,
    val dayOfWeek: Int?,
    val isRest: Boolean,
    val slotCount: Int,
)

data class ProgramEditorUiState(
    val name: String = "",
    val days: List<DayRow> = emptyList(),
)

sealed interface ProgramEditorEvent {
    data class Name(val value: String) : ProgramEditorEvent
    data object SaveName : ProgramEditorEvent
    data object AddDay : ProgramEditorEvent
    data class DeleteDay(val id: Long) : ProgramEditorEvent
    data class MoveDay(val from: Int, val to: Int) : ProgramEditorEvent
    data object Refresh : ProgramEditorEvent
}

@HiltViewModel
class ProgramEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programs: ProgramRepository,
    private val routines: RoutineRepository,
) : ViewModel() {
    private val programId: Long = checkNotNull(savedStateHandle.get<Long>("programId"))
    private val _uiState = MutableStateFlow(ProgramEditorUiState())
    private var nameSeeded = false
    val uiState: StateFlow<ProgramEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            programs.observeAll().collect { rows ->
                if (nameSeeded) return@collect
                val name = rows.firstOrNull { it.id == programId }?.name ?: return@collect
                nameSeeded = true
                _uiState.update { it.copy(name = name) }
            }
        }
        viewModelScope.launch {
            routines.observeDays(programId).collect { days ->
                publishDays(days, countsFor(days))
            }
        }
    }

    fun onEvent(event: ProgramEditorEvent) {
        when (event) {
            is ProgramEditorEvent.Name -> _uiState.update { it.copy(name = event.value) }
            ProgramEditorEvent.SaveName -> saveName()
            ProgramEditorEvent.AddDay -> addDay()
            is ProgramEditorEvent.DeleteDay -> viewModelScope.launch { routines.deleteDay(event.id) }
            is ProgramEditorEvent.MoveDay -> move(event.from, event.to)
            ProgramEditorEvent.Refresh -> refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val days = routines.observeDays(programId).first()
            publishDays(days, countsFor(days))
        }
    }

    private suspend fun countsFor(days: List<RoutineDayEntity>): Map<Long, Int> {
        if (days.isEmpty()) return emptyMap()
        return routines.slotsForDays(days.map { it.id }).groupingBy { it.dayId }.eachCount()
    }

    private fun publishDays(days: List<RoutineDayEntity>, counts: Map<Long, Int>) {
        _uiState.update { state ->
            state.copy(
                days = days.map { day ->
                    DayRow(
                        id = day.id,
                        label = day.label,
                        dayOfWeek = day.dayOfWeek,
                        isRest = day.isRest,
                        slotCount = counts[day.id] ?: 0,
                    )
                },
            )
        }
    }

    private fun saveName() {
        val name = _uiState.value.name.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val current = programs.observeAll().first().firstOrNull { it.id == programId } ?: return@launch
            programs.update(current.copy(name = name))
        }
    }

    private fun addDay() {
        viewModelScope.launch {
            val days = routines.observeDays(programId).first()
            routines.insertDay(
                RoutineDayEntity(
                    programId = programId,
                    label = "",
                    dayOfWeek = null,
                    sequenceIndex = days.size,
                    isRest = false,
                ),
            )
        }
    }

    private fun move(from: Int, to: Int) {
        viewModelScope.launch {
            val days = routines.observeDays(programId).first()
            val ordered = moveItem(days.map { it.id }, from, to) ?: return@launch
            routines.reorderDays(programId, ordered)
        }
    }
}
