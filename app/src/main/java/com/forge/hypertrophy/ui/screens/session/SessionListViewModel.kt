package com.forge.hypertrophy.ui.screens.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.domain.model.SessionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionRowUi(
    val id: Long,
    val date: LocalDate,
    val status: SessionStatus,
    val edited: Boolean,
)

data class SessionListUiState(
    val sessions: List<SessionRowUi> = emptyList(),
    val loaded: Boolean = false,
)

sealed interface SessionListEvent {
    data object Refresh : SessionListEvent
}

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val sessions: SessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: SessionListEvent) {
        when (event) {
            SessionListEvent.Refresh -> viewModelScope.launch { load() }
        }
    }

    private suspend fun load() {
        val rows = sessions.history().map { session ->
            SessionRowUi(
                id = session.id,
                date = session.date,
                status = session.status,
                edited = session.editedAt != null,
            )
        }
        _uiState.value = SessionListUiState(sessions = rows, loaded = true)
    }
}
