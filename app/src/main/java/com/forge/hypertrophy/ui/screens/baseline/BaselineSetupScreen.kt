package com.forge.hypertrophy.ui.screens.baseline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.screens.routine.BuilderColumn
import com.forge.hypertrophy.ui.screens.routine.EditorButton
import com.forge.hypertrophy.ui.screens.routine.formatKg
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.White

@Composable
fun BaselineSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BaselineSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BaselineSetupContent(state, viewModel::onEvent, onBack, modifier)
}

@Composable
fun BaselineSetupContent(
    state: BaselineSetupUiState,
    onEvent: (BaselineSetupEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuilderColumn(title = stringResource(R.string.baseline_title), onBack = onBack, modifier = modifier) {
        Text(stringResource(R.string.baseline_hint), color = White)
        if (state.loaded && state.days.isEmpty()) {
            Text(stringResource(R.string.baseline_empty), color = White)
        }
        state.days.forEach { day ->
            Text(day.label, color = NeonAccent, style = MaterialTheme.typography.titleMedium)
            day.slots.forEach { slot -> SlotRow(slot, onEvent) }
        }
        if (state.saved) Text(stringResource(R.string.baseline_saved), color = NeonAccent)
        if (state.days.isNotEmpty()) {
            EditorButton(label = stringResource(R.string.baseline_save), onClick = { onEvent(BaselineSetupEvent.Save) })
        }
    }
}

@Composable
private fun SlotRow(slot: BaselineSlotUi, onEvent: (BaselineSetupEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(slot.exerciseName, color = White)
        if (slot.calibrate) {
            Text(stringResource(R.string.baseline_calibrating), color = NeonAccent)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EditorButton(stringResource(R.string.baseline_minus), { onEvent(BaselineSetupEvent.StepWeight(slot.slotId, -1)) })
                NumericText(formatKg(slot.weightKg) + " kg", color = White)
                EditorButton(stringResource(R.string.baseline_plus), { onEvent(BaselineSetupEvent.StepWeight(slot.slotId, 1)) })
                EditorButton(stringResource(R.string.baseline_minus), { onEvent(BaselineSetupEvent.StepReps(slot.slotId, -1)) })
                NumericText(slot.reps.toString(), color = White)
                EditorButton(stringResource(R.string.baseline_plus), { onEvent(BaselineSetupEvent.StepReps(slot.slotId, 1)) })
            }
        }
        EditorButton(
            label = stringResource(R.string.baseline_skip),
            onClick = { onEvent(BaselineSetupEvent.Calibrate(slot.slotId)) },
        )
    }
}
