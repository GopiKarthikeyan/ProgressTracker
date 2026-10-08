package com.forge.hypertrophy.ui.screens.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.screens.routine.BuilderColumn
import com.forge.hypertrophy.ui.screens.routine.EditorButton
import com.forge.hypertrophy.ui.screens.routine.formatKg
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.Ink

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SessionDetailContent(state, viewModel::onEvent, onBack, modifier)
}

@Composable
fun SessionDetailContent(
    state: SessionDetailUiState,
    onEvent: (SessionDetailEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuilderColumn(title = stringResource(R.string.session_detail_title), onBack = onBack, modifier = modifier) {
        if (state.missing) {
            Text(stringResource(R.string.session_missing), color = Ink)
            return@BuilderColumn
        }
        OutlinedTextField(
            value = state.dateText,
            onValueChange = { onEvent(SessionDetailEvent.Date(it)) },
            label = { Text(stringResource(R.string.session_date)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.dateInvalid) Text(stringResource(R.string.session_date_invalid), color = NeonAccent)
        Text(stringResource(R.string.session_status), color = Ink)
        Row {
            SessionStatus.entries.forEach { status ->
                EditorButton(
                    label = status.name,
                    onClick = { onEvent(SessionDetailEvent.Status(status)) },
                    enabled = state.status != status,
                )
            }
        }
        state.slots.forEach { slot ->
            Text(slot.exerciseName, color = NeonAccent)
            slot.sets.filter { !it.deleted }.forEach { set ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.session_set_number, set.setNumber), color = Ink)
                    EditorButton(stringResource(R.string.baseline_minus), { onEvent(SessionDetailEvent.StepWeight(set.id, -1)) })
                    NumericText(formatKg(set.weightKg ?: 0.0), color = Ink)
                    EditorButton(stringResource(R.string.baseline_plus), { onEvent(SessionDetailEvent.StepWeight(set.id, 1)) })
                    EditorButton(stringResource(R.string.baseline_minus), { onEvent(SessionDetailEvent.StepReps(set.id, -1)) })
                    NumericText((set.reps ?: 0).toString(), color = Ink)
                    EditorButton(stringResource(R.string.baseline_plus), { onEvent(SessionDetailEvent.StepReps(set.id, 1)) })
                    EditorButton(
                        stringResource(R.string.session_delete_set),
                        { onEvent(SessionDetailEvent.DeleteSet(set.id)) },
                    )
                }
            }
            EditorButton(
                label = stringResource(R.string.session_add_set),
                onClick = { onEvent(SessionDetailEvent.AddSet(slot.sessionSlotId)) },
            )
        }
        state.warnings.forEach { warning ->
            Text(warning, color = NeonAccent)
        }
        state.suggestions.forEach { note ->
            Column { Text(note, color = Ink) }
        }
        if (state.saved) Text(stringResource(R.string.session_saved), color = NeonAccent)
        EditorButton(label = stringResource(R.string.session_save), onClick = { onEvent(SessionDetailEvent.Save) })
    }
}
