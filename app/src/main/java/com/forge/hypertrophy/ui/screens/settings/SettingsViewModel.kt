package com.forge.hypertrophy.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.repository.MediaPreferencesRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.data.transfer.SampleProgramProvider
import com.forge.hypertrophy.data.backup.BackupClient
import com.forge.hypertrophy.data.backup.NewerBackupException
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.ui.screens.routine.moveItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsDay(
    val id: Long,
    val label: String,
    /** ISO weekday, Monday is 1 and Sunday is 7. */
    val weekday: Int?,
)

data class WeekdayConflict(
    val weekday: Int,
    val dayLabels: List<String>,
)

data class SettingsUiState(
    val programName: String? = null,
    val scheduleMode: ScheduleMode? = null,
    val days: List<SettingsDay> = emptyList(),
    val weekdayConflicts: List<WeekdayConflict> = emptyList(),
    val weekdaysDirty: Boolean = false,
    val platesKg: List<Double> = emptyList(),
    val plateDraft: String = "",
    val transitionRestSeconds: Int = 0,
    /** Rest the Quick Settings tile starts. */
    val defaultRestSeconds: Int = 0,
    /** Mirrors [SampleProgramProvider.available]; the only gate for the Developer section. */
    val developerVisible: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    val autoBackupFolderChosen: Boolean = false,
    val lastAutoBackupDate: String? = null,
    val pendingRestore: PendingRestore? = null,
    val backupNotice: BackupNotice? = null,
    /** 0..1 while an export or restore streams, null when idle. */
    val backupProgress: Float? = null,
    val clipLeadTrimSeconds: Int = 0,
    val clipTailTrimSeconds: Int = 0,
)

data class PendingRestore(
    val exportedAt: String,
    val schemaVersion: Int,
)

enum class BackupNotice {
    EXPORTED,
    EXPORT_FAILED,
    RESTORE_FAILED,
    NEWER_SCHEMA,
    FOLDER_FAILED,
}

sealed interface SettingsEvent {
    data class ScheduleModeChanged(val mode: ScheduleMode) : SettingsEvent
    data class Weekday(val dayId: Long, val isoDay: Int?) : SettingsEvent
    data object SaveWeekdays : SettingsEvent
    data class MoveDay(val from: Int, val to: Int) : SettingsEvent
    data class PlateDraft(val value: String) : SettingsEvent
    data object AddPlate : SettingsEvent
    data class RemovePlate(val kg: Double) : SettingsEvent
    data class TransitionRest(val seconds: Int) : SettingsEvent
    data class DefaultRest(val seconds: Int) : SettingsEvent
    data class ExportBackup(val uri: String) : SettingsEvent
    data class StageRestore(val uri: String) : SettingsEvent
    data object ConfirmRestore : SettingsEvent
    data object CancelRestore : SettingsEvent
    data class SetAutoBackup(val enabled: Boolean) : SettingsEvent
    data class ChooseBackupFolder(val uri: String) : SettingsEvent
    data object DismissBackupNotice : SettingsEvent
    data class ClipLeadTrim(val seconds: Int) : SettingsEvent
    data class ClipTailTrim(val seconds: Int) : SettingsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val programs: ProgramRepository,
    private val routines: RoutineRepository,
    private val preferences: TrainingPreferencesRepository,
    sampleProgram: SampleProgramProvider,
    private val backup: BackupClient,
    private val mediaPreferences: MediaPreferencesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(developerVisible = sampleProgram.available))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var programId: Long? = null
    private var storedDays: List<RoutineDayEntity> = emptyList()
    private val weekdayDraft = mutableMapOf<Long, Int?>()

    init {
        viewModelScope.launch {
            programs.observeActive()
                .flatMapLatest { program ->
                    if (program == null) {
                        flowOf(null to emptyList())
                    } else {
                        routines.observeDays(program.id).map { program to it }
                    }
                }
                .collect { (program, days) ->
                    programId = program?.id
                    storedDays = days
                    weekdayDraft.keys.retainAll(days.map { it.id }.toSet())
                    _uiState.update {
                        it.copy(
                            programName = program?.name,
                            scheduleMode = program?.scheduleMode,
                            days = renderDays(),
                        )
                    }
                }
        }
        viewModelScope.launch {
            preferences.plateInventoryKg.collect { plates ->
                _uiState.update { it.copy(platesKg = plates) }
            }
        }
        viewModelScope.launch {
            preferences.transitionRestSeconds.collect { seconds ->
                _uiState.update { it.copy(transitionRestSeconds = seconds) }
            }
        }
        viewModelScope.launch {
            preferences.defaultRestSeconds.collect { seconds ->
                _uiState.update { it.copy(defaultRestSeconds = seconds) }
            }
        }
        viewModelScope.launch {
            backup.autoBackupEnabled.collect { enabled ->
                _uiState.update { it.copy(autoBackupEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            backup.autoBackupFolderUri.collect { uri ->
                _uiState.update { it.copy(autoBackupFolderChosen = uri != null) }
            }
        }
        viewModelScope.launch {
            backup.lastAutoBackupDate.collect { date ->
                _uiState.update { it.copy(lastAutoBackupDate = date?.toString()) }
            }
        }
        viewModelScope.launch {
            backup.progress.collect { progress ->
                _uiState.update { it.copy(backupProgress = progress?.fraction) }
            }
        }
        viewModelScope.launch {
            mediaPreferences.leadTrimMs.collect { ms ->
                _uiState.update { it.copy(clipLeadTrimSeconds = (ms / 1_000L).toInt()) }
            }
        }
        viewModelScope.launch {
            mediaPreferences.tailTrimMs.collect { ms ->
                _uiState.update { it.copy(clipTailTrimSeconds = (ms / 1_000L).toInt()) }
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ScheduleModeChanged -> changeScheduleMode(event.mode)
            is SettingsEvent.Weekday -> {
                weekdayDraft[event.dayId] = event.isoDay
                _uiState.update { it.copy(days = renderDays(), weekdaysDirty = true, weekdayConflicts = emptyList()) }
            }
            SettingsEvent.SaveWeekdays -> saveWeekdays()
            is SettingsEvent.MoveDay -> moveDay(event.from, event.to)
            is SettingsEvent.PlateDraft -> _uiState.update { it.copy(plateDraft = event.value) }
            SettingsEvent.AddPlate -> addPlate()
            is SettingsEvent.RemovePlate -> viewModelScope.launch {
                preferences.setPlateInventoryKg(_uiState.value.platesKg.filterNot { it == event.kg })
            }
            is SettingsEvent.TransitionRest -> viewModelScope.launch {
                preferences.setTransitionRestSeconds(event.seconds.coerceAtLeast(0))
            }
            is SettingsEvent.DefaultRest -> viewModelScope.launch {
                preferences.setDefaultRestSeconds(event.seconds.coerceAtLeast(MIN_DEFAULT_REST_SECONDS))
            }
            is SettingsEvent.ExportBackup -> exportBackup(event.uri)
            is SettingsEvent.StageRestore -> stageRestore(event.uri)
            SettingsEvent.ConfirmRestore -> viewModelScope.launch {
                runCatching { backup.commitRestore() }
                    .onFailure { error -> _uiState.update { it.copy(backupNotice = noticeFor(error), pendingRestore = null) } }
            }
            SettingsEvent.CancelRestore -> {
                backup.discardRestore()
                _uiState.update { it.copy(pendingRestore = null) }
            }
            is SettingsEvent.SetAutoBackup -> viewModelScope.launch {
                backup.setAutoBackupEnabled(event.enabled)
            }
            is SettingsEvent.ChooseBackupFolder -> viewModelScope.launch {
                runCatching { backup.setAutoBackupFolder(event.uri) }
                    .onFailure { _uiState.update { it.copy(backupNotice = BackupNotice.FOLDER_FAILED) } }
            }
            SettingsEvent.DismissBackupNotice -> _uiState.update { it.copy(backupNotice = null) }
            is SettingsEvent.ClipLeadTrim -> viewModelScope.launch {
                mediaPreferences.setLeadTrimMs(event.seconds.coerceAtLeast(0) * 1_000L)
            }
            is SettingsEvent.ClipTailTrim -> viewModelScope.launch {
                mediaPreferences.setTailTrimMs(event.seconds.coerceAtLeast(0) * 1_000L)
            }
        }
    }

    private fun exportBackup(uri: String) {
        viewModelScope.launch {
            runCatching { backup.exportTo(uri) }
                .onSuccess { _uiState.update { it.copy(backupNotice = BackupNotice.EXPORTED) } }
                .onFailure { _uiState.update { it.copy(backupNotice = BackupNotice.EXPORT_FAILED) } }
        }
    }

    private fun stageRestore(uri: String) {
        viewModelScope.launch {
            runCatching { backup.stageRestore(uri) }
                .onSuccess { manifest ->
                    _uiState.update {
                        it.copy(
                            pendingRestore = PendingRestore(manifest.exportedAt, manifest.schemaVersion),
                            backupNotice = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(backupNotice = noticeFor(error), pendingRestore = null) }
                }
        }
    }

    private fun noticeFor(error: Throwable): BackupNotice = when (error) {
        is NewerBackupException -> BackupNotice.NEWER_SCHEMA
        else -> BackupNotice.RESTORE_FAILED
    }

    private fun renderDays(): List<SettingsDay> = storedDays.map { day ->
        SettingsDay(
            id = day.id,
            label = day.label,
            weekday = if (weekdayDraft.containsKey(day.id)) weekdayDraft[day.id] else day.dayOfWeek,
        )
    }

    private fun changeScheduleMode(mode: ScheduleMode) {
        viewModelScope.launch {
            val program = programs.observeActive().first() ?: return@launch
            if (program.scheduleMode != mode) programs.update(program.copy(scheduleMode = mode))
        }
    }

    /** Rejects the save when two days share a weekday and names those days. */
    private fun saveWeekdays() {
        val rows = renderDays()
        val conflicts = rows
            .filter { it.weekday != null }
            .groupBy { it.weekday!! }
            .filterValues { it.size > 1 }
            .map { (weekday, days) -> WeekdayConflict(weekday, days.map { it.label }) }
            .sortedBy { it.weekday }
        if (conflicts.isNotEmpty()) {
            _uiState.update { it.copy(weekdayConflicts = conflicts) }
            return
        }
        viewModelScope.launch {
            storedDays.forEach { day ->
                if (weekdayDraft.containsKey(day.id) && weekdayDraft[day.id] != day.dayOfWeek) {
                    routines.updateDay(day.copy(dayOfWeek = weekdayDraft[day.id]))
                }
            }
            weekdayDraft.clear()
            _uiState.update { it.copy(weekdayConflicts = emptyList(), weekdaysDirty = false, days = renderDays()) }
        }
    }

    private fun moveDay(from: Int, to: Int) {
        val id = programId ?: return
        viewModelScope.launch {
            val days = routines.observeDays(id).first()
            val ordered = moveItem(days.map { it.id }, from, to) ?: return@launch
            routines.reorderDays(id, ordered)
        }
    }

    private fun addPlate() {
        val kg = _uiState.value.plateDraft.trim().toDoubleOrNull() ?: return
        if (kg <= 0.0) return
        viewModelScope.launch {
            val plates = (_uiState.value.platesKg + kg).distinct().sortedDescending()
            preferences.setPlateInventoryKg(plates)
            _uiState.update { it.copy(plateDraft = "") }
        }
    }

    private companion object {
        const val MIN_DEFAULT_REST_SECONDS = 15
    }
}
