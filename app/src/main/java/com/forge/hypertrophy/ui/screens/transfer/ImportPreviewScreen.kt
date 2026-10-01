package com.forge.hypertrophy.ui.screens.transfer

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.data.transfer.ImportMode
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.screens.routine.BuilderColumn
import com.forge.hypertrophy.ui.screens.routine.EditorButton
import com.forge.hypertrophy.ui.theme.NeonAccent

@Composable
fun ImportPreviewScreen(
    viewModel: ImportPreviewViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.imported) {
        if (state.imported) onDone()
    }
    BuilderColumn(title = stringResource(R.string.import_title), onBack = onBack, modifier = modifier) {
        when {
            state.loading -> Text(stringResource(R.string.import_loading))
            state.unreadable -> Text(stringResource(R.string.import_unreadable))
            else -> PreviewBody(state, viewModel::onEvent)
        }
    }
}

@Composable
private fun PreviewBody(
    state: ImportPreviewUiState,
    onEvent: (ImportPreviewEvent) -> Unit,
) {
    Text(state.programName, style = MaterialTheme.typography.titleLarge)
    if (state.errors.isNotEmpty()) {
        Text(stringResource(R.string.import_errors), color = MaterialTheme.colorScheme.error)
        state.errors.forEach { issue ->
            Text("${issue.path}: ${issue.message}", color = MaterialTheme.colorScheme.error)
        }
    }
    if (state.warnings.isNotEmpty()) {
        Text(stringResource(R.string.import_warnings), color = NeonAccent)
        state.warnings.forEach { issue ->
            Text("${issue.path}: ${issue.message}")
        }
    }
    CountRow(stringResource(R.string.import_days), state.dayCount)
    CountRow(stringResource(R.string.import_slots), state.slotCount)
    CountRow(stringResource(R.string.import_new_exercises), state.newExerciseCount)
    CountRow(stringResource(R.string.import_new_skills), state.newSkillCount)
    if (state.mode == ImportMode.REPLACE_EVERYTHING) {
        CountRow(stringResource(R.string.import_archive_count), state.archiveCount)
    }
    Text(stringResource(R.string.import_mode))
    FlowRow {
        ImportMode.entries.forEach { mode ->
            EditorButton(
                label = stringResource(mode.labelRes()),
                onClick = { onEvent(ImportPreviewEvent.Mode(mode)) },
                enabled = state.mode != mode,
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(
            checked = state.applyDefaults,
            onCheckedChange = { onEvent(ImportPreviewEvent.ApplyDefaults(it)) },
        )
        Text(stringResource(R.string.import_apply_defaults))
    }
    if (!state.canConfirm) {
        Text(stringResource(R.string.import_blocked), color = MaterialTheme.colorScheme.error)
    }
    EditorButton(
        label = stringResource(R.string.import_confirm),
        onClick = { onEvent(ImportPreviewEvent.Confirm) },
        enabled = state.canConfirm && !state.importing,
    )
}

@Composable
private fun CountRow(label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        NumericText(text = count.toString(), color = NeonAccent)
    }
}

private fun ImportMode.labelRes(): Int = when (this) {
    ImportMode.REPLACE_ROUTINE_MERGE_LIBRARY -> R.string.import_mode_replace_routine
    ImportMode.REPLACE_EVERYTHING -> R.string.import_mode_replace_everything
    ImportMode.ADD_PROGRAM -> R.string.import_mode_add
}
