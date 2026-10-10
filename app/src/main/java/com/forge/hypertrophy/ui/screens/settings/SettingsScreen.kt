package com.forge.hypertrophy.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.ReorderableColumn
import com.forge.hypertrophy.ui.components.ToggleChip
import com.forge.hypertrophy.ui.screens.routine.BuilderColumn
import com.forge.hypertrophy.ui.screens.routine.EditorButton
import com.forge.hypertrophy.ui.screens.routine.NumericEntry
import com.forge.hypertrophy.ui.screens.routine.formatKg
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.Ink

private const val TRANSITION_REST_STEP_SECONDS = 15

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onLoadSample: () -> Unit,
    onOpenBaselines: () -> Unit,
    onOpenSnapshots: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val weekdays = stringArrayResource(R.array.builder_weekdays)
    BuilderColumn(title = stringResource(R.string.nav_settings), onBack = null, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_program), style = MaterialTheme.typography.titleMedium)
            val programName = state.programName
            if (programName == null) {
                Text(stringResource(R.string.settings_no_program))
            } else {
                Text(programName)
                ScheduleSection(state, weekdays, viewModel::onEvent)
            }
            EditorButton(label = stringResource(R.string.settings_load_sample), onClick = onLoadSample)
        }
        PlateSection(state, viewModel::onEvent)
        TransitionRestSection(state, viewModel::onEvent)
        DefaultRestSection(state, viewModel::onEvent)
        ClipTrimSection(state, viewModel::onEvent)
        BackupSection(state, viewModel::onEvent)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorButton(label = stringResource(R.string.settings_starting_weights), onClick = onOpenBaselines)
            EditorButton(label = stringResource(R.string.settings_snapshots), onClick = onOpenSnapshots)
            EditorButton(label = stringResource(R.string.settings_diagnostics), onClick = onOpenDiagnostics)
        }
    }
}

@Composable
private fun ScheduleSection(
    state: SettingsUiState,
    weekdays: Array<String>,
    onEvent: (SettingsEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_schedule_mode))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip(
                label = stringResource(R.string.settings_mode_fixed),
                selected = state.scheduleMode == ScheduleMode.FIXED,
                onClick = { onEvent(SettingsEvent.ScheduleModeChanged(ScheduleMode.FIXED)) },
                modifier = Modifier.weight(1f),
            )
            ToggleChip(
                label = stringResource(R.string.settings_mode_rolling),
                selected = state.scheduleMode == ScheduleMode.ROLLING,
                onClick = { onEvent(SettingsEvent.ScheduleModeChanged(ScheduleMode.ROLLING)) },
                modifier = Modifier.weight(1f),
            )
        }
        when (state.scheduleMode) {
            ScheduleMode.FIXED -> {
                Text(stringResource(R.string.settings_weekdays))
                state.days.forEach { day ->
                    Text(day.label)
                    FlowRow {
                        EditorButton(
                            label = stringResource(R.string.builder_weekday_none),
                            onClick = { onEvent(SettingsEvent.Weekday(day.id, null)) },
                            enabled = day.weekday != null,
                        )
                        weekdays.forEachIndexed { index, label ->
                            EditorButton(
                                label = label,
                                onClick = { onEvent(SettingsEvent.Weekday(day.id, index + 1)) },
                                enabled = day.weekday != index + 1,
                            )
                        }
                    }
                }
                state.weekdayConflicts.forEach { conflict ->
                    Text(
                        text = stringResource(
                            R.string.settings_weekday_conflict,
                            weekdays[conflict.weekday - 1],
                            conflict.dayLabels.joinToString(", "),
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                EditorButton(
                    label = stringResource(R.string.builder_save),
                    onClick = { onEvent(SettingsEvent.SaveWeekdays) },
                    enabled = state.weekdaysDirty,
                )
            }
            ScheduleMode.ROLLING -> {
                Text(stringResource(R.string.settings_day_order))
                ReorderableColumn(
                    items = state.days,
                    itemKey = { it.id },
                    onMove = { from, to -> onEvent(SettingsEvent.MoveDay(from, to)) },
                ) { day ->
                    Text(day.label, modifier = Modifier.fillMaxWidth())
                }
            }
            null -> Unit
        }
    }
}

@Composable
private fun PlateSection(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_plates), style = MaterialTheme.typography.titleMedium)
        state.platesKg.forEach { kg ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                NumericText(text = formatKg(kg), color = NeonAccent, modifier = Modifier.weight(1f))
                EditorButton(
                    label = stringResource(R.string.settings_remove),
                    onClick = { onEvent(SettingsEvent.RemovePlate(kg)) },
                )
            }
        }
        OutlinedTextField(
            value = state.plateDraft,
            onValueChange = { onEvent(SettingsEvent.PlateDraft(it)) },
            label = { Text(stringResource(R.string.settings_plate_kg)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum",
            ),
            singleLine = true,
        )
        EditorButton(label = stringResource(R.string.builder_add), onClick = { onEvent(SettingsEvent.AddPlate) })
    }
}

@Composable
private fun TransitionRestSection(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_transition_rest), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            EditorButton(
                label = stringResource(R.string.settings_seconds_less),
                onClick = {
                    onEvent(SettingsEvent.TransitionRest(state.transitionRestSeconds - TRANSITION_REST_STEP_SECONDS))
                },
                enabled = state.transitionRestSeconds >= TRANSITION_REST_STEP_SECONDS,
            )
            NumericText(text = state.transitionRestSeconds.toString(), color = NeonAccent)
            EditorButton(
                label = stringResource(R.string.settings_seconds_more),
                onClick = {
                    onEvent(SettingsEvent.TransitionRest(state.transitionRestSeconds + TRANSITION_REST_STEP_SECONDS))
                },
            )
        }
    }
}

@Composable
private fun DefaultRestSection(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_default_rest), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_default_rest_hint), color = Ink.copy(alpha = 0.72f))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            EditorButton(
                label = stringResource(R.string.settings_seconds_less),
                onClick = { onEvent(SettingsEvent.DefaultRest(state.defaultRestSeconds - TRANSITION_REST_STEP_SECONDS)) },
                enabled = state.defaultRestSeconds > TRANSITION_REST_STEP_SECONDS,
            )
            NumericText(text = state.defaultRestSeconds.toString(), color = NeonAccent)
            EditorButton(
                label = stringResource(R.string.settings_seconds_more),
                onClick = { onEvent(SettingsEvent.DefaultRest(state.defaultRestSeconds + TRANSITION_REST_STEP_SECONDS)) },
            )
        }
    }
}

@Composable
private fun ClipTrimSection(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_clip_trim), style = MaterialTheme.typography.titleMedium)
        NumericEntry(
            label = stringResource(R.string.settings_clip_lead),
            value = state.clipLeadTrimSeconds,
            onValue = { seconds -> onEvent(SettingsEvent.ClipLeadTrim(seconds ?: 0)) },
        )
        NumericEntry(
            label = stringResource(R.string.settings_clip_tail),
            value = state.clipTailTrimSeconds,
            onValue = { seconds -> onEvent(SettingsEvent.ClipTailTrim(seconds ?: 0)) },
        )
    }
}

@Composable
private fun BackupSection(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) onEvent(SettingsEvent.ExportBackup(uri.toString()))
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onEvent(SettingsEvent.StageRestore(uri.toString()))
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onEvent(SettingsEvent.ChooseBackupFolder(uri.toString()))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
        EditorButton(
            label = stringResource(R.string.settings_export_backup),
            onClick = { exportLauncher.launch("hypertrophy-backup.zip") },
        )
        EditorButton(
            label = stringResource(R.string.settings_restore_backup),
            onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
        )
        state.backupProgress?.let { progress ->
            Text(stringResource(R.string.settings_backup_progress))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = NeonAccent,
            )
        }
        val pending = state.pendingRestore
        if (pending != null) {
            Text(stringResource(R.string.settings_backup_confirm, pending.exportedAt, pending.schemaVersion))
            EditorButton(
                label = stringResource(R.string.settings_backup_confirm_action),
                onClick = { onEvent(SettingsEvent.ConfirmRestore) },
            )
            EditorButton(
                label = stringResource(R.string.settings_backup_cancel),
                onClick = { onEvent(SettingsEvent.CancelRestore) },
            )
        }
        state.backupNotice?.let { notice ->
            Text(
                stringResource(
                    when (notice) {
                        BackupNotice.EXPORTED -> R.string.settings_backup_exported
                        BackupNotice.EXPORT_FAILED -> R.string.settings_backup_export_failed
                        BackupNotice.RESTORE_FAILED -> R.string.settings_backup_restore_failed
                        BackupNotice.NEWER_SCHEMA -> R.string.settings_backup_newer
                        BackupNotice.FOLDER_FAILED -> R.string.settings_backup_folder_failed
                    },
                ),
            )
            EditorButton(
                label = stringResource(R.string.settings_dismiss),
                onClick = { onEvent(SettingsEvent.DismissBackupNotice) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_backup_weekly), modifier = Modifier.weight(1f))
            Switch(
                checked = state.autoBackupEnabled,
                onCheckedChange = { enabled -> onEvent(SettingsEvent.SetAutoBackup(enabled)) },
            )
        }
        EditorButton(
            label = stringResource(R.string.settings_backup_choose_folder),
            onClick = { folderLauncher.launch(null) },
        )
        Text(
            stringResource(
                if (state.autoBackupFolderChosen) {
                    R.string.settings_backup_folder_chosen
                } else {
                    R.string.settings_backup_folder_missing
                },
            ),
        )
        state.lastAutoBackupDate?.let { date ->
            Text(stringResource(R.string.settings_backup_last, date))
        }
    }
}
