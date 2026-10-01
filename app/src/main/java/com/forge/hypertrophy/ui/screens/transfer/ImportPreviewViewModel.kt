package com.forge.hypertrophy.ui.screens.transfer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.storage.ProgramDocumentStore
import com.forge.hypertrophy.data.transfer.ImportMode
import com.forge.hypertrophy.data.transfer.ImportPreview
import com.forge.hypertrophy.data.transfer.ImportResult
import com.forge.hypertrophy.data.transfer.ProgramImporter
import com.forge.hypertrophy.data.transfer.ProgramJson
import com.forge.hypertrophy.data.transfer.ProgramJsonIssue
import com.forge.hypertrophy.data.transfer.SampleProgramProvider
import com.forge.hypertrophy.domain.model.ScheduleMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportIssueRow(
    val path: String,
    val message: String,
)

data class ImportPreviewUiState(
    val loading: Boolean = true,
    val unreadable: Boolean = false,
    val programName: String = "",
    val mode: ImportMode = ImportMode.REPLACE_ROUTINE_MERGE_LIBRARY,
    val applyDefaults: Boolean = true,
    val errors: List<ImportIssueRow> = emptyList(),
    val warnings: List<ImportIssueRow> = emptyList(),
    val dayCount: Int = 0,
    val slotCount: Int = 0,
    val newExerciseCount: Int = 0,
    val newSkillCount: Int = 0,
    val archiveCount: Int = 0,
    val canConfirm: Boolean = false,
    val importing: Boolean = false,
    val imported: Boolean = false,
)

sealed interface ImportPreviewEvent {
    data class Mode(val mode: ImportMode) : ImportPreviewEvent
    data class ApplyDefaults(val value: Boolean) : ImportPreviewEvent
    data object Confirm : ImportPreviewEvent
}

/**
 * validate → preview → confirm. The document is parsed once, previewed for the
 * selected mode, and written only from [ImportPreviewEvent.Confirm] when the
 * preview has no errors.
 */
@HiltViewModel
class ImportPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documents: ProgramDocumentStore,
    private val sampleProgram: SampleProgramProvider,
    private val importer: ProgramImporter,
    private val programs: ProgramRepository,
) : ViewModel() {
    private val uri: String? = savedStateHandle.get<String>("uri")
    private val sample: Boolean = savedStateHandle.get<Boolean>("sample") == true
    private val _uiState = MutableStateFlow(ImportPreviewUiState())
    val uiState: StateFlow<ImportPreviewUiState> = _uiState.asStateFlow()

    private var document: ProgramJson? = null
    private var scheduleMode: ScheduleMode = ScheduleMode.FIXED

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: ImportPreviewEvent) {
        when (event) {
            is ImportPreviewEvent.Mode -> changeMode(event.mode)
            is ImportPreviewEvent.ApplyDefaults -> _uiState.update { it.copy(applyDefaults = event.value) }
            ImportPreviewEvent.Confirm -> confirm()
        }
    }

    private suspend fun load() {
        val parsed = runCatching {
            when {
                sample -> sampleProgram.program()
                uri != null -> documents.read(uri)
                else -> error("No document to import")
            }
        }.getOrNull()
        if (parsed == null) {
            _uiState.update { it.copy(loading = false, unreadable = true) }
            return
        }
        document = parsed
        scheduleMode = programs.observeActive().first()?.scheduleMode ?: ScheduleMode.FIXED
        refreshPreview(parsed, _uiState.value.mode)
    }

    private fun changeMode(mode: ImportMode) {
        _uiState.update { it.copy(mode = mode) }
        val parsed = document ?: return
        viewModelScope.launch { refreshPreview(parsed, mode) }
    }

    private suspend fun refreshPreview(parsed: ProgramJson, mode: ImportMode) {
        val preview = importer.preview(parsed, mode, scheduleMode)
        _uiState.update { it.applyPreview(preview).copy(loading = false, mode = mode) }
    }

    private fun confirm() {
        val state = _uiState.value
        val parsed = document ?: return
        if (!state.canConfirm || state.importing) return
        _uiState.update { it.copy(importing = true) }
        viewModelScope.launch {
            when (val result = importer.import(parsed, state.mode, scheduleMode, state.applyDefaults)) {
                is ImportResult.Imported -> _uiState.update { it.copy(importing = false, imported = true) }
                is ImportResult.Rejected -> _uiState.update {
                    it.applyPreview(result.preview).copy(importing = false)
                }
            }
        }
    }
}

private fun ImportPreviewUiState.applyPreview(preview: ImportPreview) = copy(
    programName = preview.programName,
    errors = preview.errors.map { it.toRow() },
    warnings = preview.warnings.map { it.toRow() },
    dayCount = preview.dayCount,
    slotCount = preview.slotCount,
    newExerciseCount = preview.newExerciseNames.size,
    newSkillCount = preview.newSkillNames.size,
    archiveCount = preview.exercisesToArchive.size,
    canConfirm = preview.errors.isEmpty(),
)

private fun ProgramJsonIssue.toRow() = ImportIssueRow(path = path, message = message)
