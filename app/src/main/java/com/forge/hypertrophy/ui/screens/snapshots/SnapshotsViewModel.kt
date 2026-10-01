package com.forge.hypertrophy.ui.screens.snapshots

import androidx.lifecycle.ViewModel
import com.forge.hypertrophy.data.backup.SchemaSnapshot
import com.forge.hypertrophy.data.backup.SnapshotInstaller
import com.forge.hypertrophy.data.backup.SnapshotLibrary
import com.forge.hypertrophy.data.diagnostics.Breadcrumbs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SnapshotRowUi(
    val name: String,
    val schemaVersion: Int,
    val label: String,
)

data class SnapshotsUiState(
    val snapshots: List<SnapshotRowUi> = emptyList(),
    val notice: String? = null,
)

sealed interface SnapshotsEvent {
    data object Refresh : SnapshotsEvent
    data class Restore(val name: String) : SnapshotsEvent
}

@HiltViewModel
class SnapshotsViewModel @Inject constructor(
    private val library: SnapshotLibrary,
    private val installer: SnapshotInstaller,
    private val breadcrumbs: Breadcrumbs,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SnapshotsUiState())
    val uiState: StateFlow<SnapshotsUiState> = _uiState.asStateFlow()
    private var files: List<SchemaSnapshot> = emptyList()

    init {
        reload()
    }

    fun onEvent(event: SnapshotsEvent) {
        breadcrumbs.record(event.javaClass.simpleName)
        when (event) {
            SnapshotsEvent.Refresh -> reload()
            is SnapshotsEvent.Restore -> restore(event.name)
        }
    }

    private fun reload() {
        files = library.list()
        _uiState.value = SnapshotsUiState(
            snapshots = files.map { SnapshotRowUi(it.file.name, it.schemaVersion, it.label) },
        )
    }

    private fun restore(name: String) {
        val snapshot = files.firstOrNull { it.file.name == name } ?: return
        runCatching { installer.install(snapshot.file) }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(notice = error.message ?: "Restore failed")
            }
    }
}
