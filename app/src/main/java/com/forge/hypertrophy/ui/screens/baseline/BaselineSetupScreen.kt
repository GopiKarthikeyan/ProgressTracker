package com.forge.hypertrophy.ui.screens.baseline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.PrimaryButton
import com.forge.hypertrophy.ui.components.SecondaryButton
import com.forge.hypertrophy.ui.screens.routine.BuilderColumn
import com.forge.hypertrophy.ui.screens.routine.EditorButton
import com.forge.hypertrophy.ui.screens.routine.formatKg
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.NeonAccent

@Composable
fun BaselineSetupScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BaselineSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }
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
        Text(stringResource(R.string.baseline_hint), color = Ink)
        if (state.loaded && state.days.isEmpty()) {
            Text(stringResource(R.string.baseline_empty), color = Ink)
        }
        if (state.days.isNotEmpty()) {
            SecondaryButton(
                label = stringResource(R.string.baseline_skip_all),
                onClick = { onEvent(BaselineSetupEvent.SkipAll) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.days.forEach { day ->
            Text(day.label, color = NeonAccent, style = MaterialTheme.typography.titleMedium)
            day.slots.forEach { slot -> SlotRow(slot, onEvent) }
        }
        if (state.days.isNotEmpty()) {
            PrimaryButton(
                label = stringResource(R.string.baseline_done),
                onClick = { onEvent(BaselineSetupEvent.Save) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SlotRow(slot: BaselineSlotUi, onEvent: (BaselineSetupEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(slot.exerciseName, color = Ink, style = MaterialTheme.typography.titleSmall)
        if (slot.calibrate) {
            Text(stringResource(R.string.baseline_calibrating), color = NeonAccent)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.baseline_weight),
                    color = Ink,
                    modifier = Modifier.widthIn(min = 40.dp),
                )
                EditorButton(
                    label = stringResource(R.string.baseline_minus),
                    onClick = { onEvent(BaselineSetupEvent.StepWeight(slot.slotId, -1)) },
                )
                NumericText(
                    text = formatKg(slot.weightKg),
                    color = Ink,
                    modifier = Modifier.widthIn(min = 40.dp),
                    textAlign = TextAlign.Center,
                )
                EditorButton(
                    label = stringResource(R.string.baseline_plus),
                    onClick = { onEvent(BaselineSetupEvent.StepWeight(slot.slotId, 1)) },
                )
                Text(
                    text = stringResource(R.string.baseline_reps),
                    color = Ink,
                    modifier = Modifier.widthIn(min = 40.dp),
                )
                EditorButton(
                    label = stringResource(R.string.baseline_minus),
                    onClick = { onEvent(BaselineSetupEvent.StepReps(slot.slotId, -1)) },
                )
                NumericText(
                    text = slot.reps.toString(),
                    color = Ink,
                    modifier = Modifier.widthIn(min = 32.dp),
                    textAlign = TextAlign.Center,
                )
                EditorButton(
                    label = stringResource(R.string.baseline_plus),
                    onClick = { onEvent(BaselineSetupEvent.StepReps(slot.slotId, 1)) },
                )
            }
        }
        EditorButton(
            label = stringResource(R.string.baseline_skip),
            onClick = { onEvent(BaselineSetupEvent.Calibrate(slot.slotId)) },
        )
    }
}
