package com.forge.hypertrophy.ui.screens.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.diagnostics.Breadcrumbs
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.session.SessionEdit
import com.forge.hypertrophy.data.session.SessionEditor
import com.forge.hypertrophy.data.session.SetEdit
import com.forge.hypertrophy.domain.model.SessionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditableSetUi(
    val id: Long,
    val sessionSlotId: Long,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val deleted: Boolean = false,
    val create: Boolean = false,
)

data class EditableSlotUi(
    val sessionSlotId: Long,
    val exerciseName: String,
    val sets: List<EditableSetUi>,
)

data class SessionDetailUiState(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val dateText: String = "",
    val status: SessionStatus = SessionStatus.COMPLETED,
    val slots: List<EditableSlotUi> = emptyList(),
    val warnings: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val dateInvalid: Boolean = false,
    val saved: Boolean = false,
)

sealed interface SessionDetailEvent {
    data class Date(val text: String) : SessionDetailEvent
    data class Status(val status: SessionStatus) : SessionDetailEvent
    data class StepWeight(val setId: Long, val direction: Int) : SessionDetailEvent
    data class StepReps(val setId: Long, val direction: Int) : SessionDetailEvent
    data class DeleteSet(val setId: Long) : SessionDetailEvent
    data class AddSet(val sessionSlotId: Long) : SessionDetailEvent
    data object Save : SessionDetailEvent
}

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
    private val exercises: ExerciseRepository,
    private val editor: SessionEditor,
    private val breadcrumbs: Breadcrumbs,
) : ViewModel() {
    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L
    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()
    private var nextTempId = -1L

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: SessionDetailEvent) {
        breadcrumbs.record(event.javaClass.simpleName)
        when (event) {
            is SessionDetailEvent.Date -> _uiState.update { it.copy(dateText = event.text, dateInvalid = false, saved = false) }
            is SessionDetailEvent.Status -> _uiState.update { it.copy(status = event.status, saved = false) }
            is SessionDetailEvent.StepWeight -> step(event.setId) { set ->
                set.copy(weightKg = ((set.weightKg ?: 0.0) + event.direction * 2.5).coerceAtLeast(0.0))
            }
            is SessionDetailEvent.StepReps -> step(event.setId) { set ->
                set.copy(reps = ((set.reps ?: 0) + event.direction).coerceAtLeast(0))
            }
            is SessionDetailEvent.DeleteSet -> step(event.setId) { it.copy(deleted = true) }
            is SessionDetailEvent.AddSet -> addSet(event.sessionSlotId)
            SessionDetailEvent.Save -> viewModelScope.launch { save() }
        }
    }

    private suspend fun load() {
        val session = sessions.get(sessionId)
        if (session == null) {
            _uiState.value = SessionDetailUiState(loaded = true, missing = true)
            return
        }
        val slots = sessions.observeSlots(sessionId).first().map { slot ->
            val exerciseId = slot.chosenAlternativeExerciseId ?: slot.prescriptionSnapshot.exerciseId
            val name = exercises.get(exerciseId)?.name.orEmpty()
            val sets = sessions.sets(slot.id).map { set ->
                EditableSetUi(set.id, slot.id, set.setNumber, set.weightKg, set.reps)
            }
            EditableSlotUi(slot.id, name, sets)
        }
        _uiState.value = SessionDetailUiState(
            loaded = true,
            dateText = session.date.toString(),
            status = session.status,
            slots = slots,
        )
    }

    private fun step(setId: Long, block: (EditableSetUi) -> EditableSetUi) {
        _uiState.update { state ->
            state.copy(
                saved = false,
                slots = state.slots.map { slot ->
                    slot.copy(sets = slot.sets.map { if (it.id == setId) block(it) else it })
                },
            )
        }
    }

    private fun addSet(sessionSlotId: Long) {
        val id = nextTempId
        nextTempId -= 1
        _uiState.update { state ->
            state.copy(
                saved = false,
                slots = state.slots.map { slot ->
                    if (slot.sessionSlotId != sessionSlotId) {
                        slot
                    } else {
                        val number = (slot.sets.maxOfOrNull { it.setNumber } ?: 0) + 1
                        slot.copy(
                            sets = slot.sets + EditableSetUi(
                                id = id,
                                sessionSlotId = sessionSlotId,
                                setNumber = number,
                                weightKg = 0.0,
                                reps = 5,
                                create = true,
                            ),
                        )
                    }
                },
            )
        }
    }

    private suspend fun save() {
        val state = _uiState.value
        val date = runCatching { LocalDate.parse(state.dateText) }.getOrNull()
        if (date == null) {
            _uiState.update { it.copy(dateInvalid = true) }
            return
        }
        val edits = state.slots.flatMap { slot ->
            slot.sets.map { set ->
                SetEdit(
                    id = if (set.create) 0L else set.id,
                    sessionSlotId = set.sessionSlotId,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    delete = set.deleted,
                    create = set.create && !set.deleted,
                )
            }
        }
        val report = editor.apply(sessionId, SessionEdit(date, state.status, edits))
        _uiState.update {
            it.copy(
                saved = true,
                warnings = report.stageWarnings,
                suggestions = report.suggestions.map { note ->
                    val kg = note.suggestion.weightKg?.toString() ?: "—"
                    "${note.exerciseName}: ${note.suggestion.action.name} $kg"
                },
            )
        }
        load()
        _uiState.update { current ->
            current.copy(
                saved = true,
                warnings = report.stageWarnings,
                suggestions = report.suggestions.map { note ->
                    val kg = note.suggestion.weightKg?.toString() ?: "—"
                    "${note.exerciseName}: ${note.suggestion.action.name} $kg"
                },
            )
        }
    }
}
