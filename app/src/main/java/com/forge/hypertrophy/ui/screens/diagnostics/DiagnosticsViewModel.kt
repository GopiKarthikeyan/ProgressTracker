package com.forge.hypertrophy.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import com.forge.hypertrophy.data.diagnostics.Breadcrumbs
import com.forge.hypertrophy.data.diagnostics.CrashLogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiagnosticsUiState(
    val files: List<String> = emptyList(),
    val selectedName: String? = null,
    val body: String = "",
    /** Set only from the share button. The screen consumes it and clears it. */
    val shareText: String? = null,
)

sealed interface DiagnosticsEvent {
    data class Open(val name: String) : DiagnosticsEvent
    data object Share : DiagnosticsEvent
    data object Clear : DiagnosticsEvent
    data object ConsumeShare : DiagnosticsEvent
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val store: CrashLogStore,
    private val breadcrumbs: Breadcrumbs,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun onEvent(event: DiagnosticsEvent) {
        breadcrumbs.record(event.javaClass.simpleName)
        when (event) {
            is DiagnosticsEvent.Open -> open(event.name)
            DiagnosticsEvent.Share -> share()
            DiagnosticsEvent.Clear -> {
                store.clear()
                reload()
            }
            DiagnosticsEvent.ConsumeShare -> _uiState.update { it.copy(shareText = null) }
        }
    }

    private fun open(name: String) {
        val file = store.list().firstOrNull { it.name == name } ?: return
        _uiState.update { it.copy(selectedName = name, body = store.read(file)) }
    }

    private fun share() {
        val state = _uiState.value
        val text = state.body.ifBlank {
            store.list().joinToString("\n\n") { file -> store.read(file) }
        }
        if (text.isBlank()) return
        _uiState.update { it.copy(shareText = text) }
    }

    private fun reload() {
        _uiState.value = DiagnosticsUiState(files = store.list().map { it.name })
    }
}
