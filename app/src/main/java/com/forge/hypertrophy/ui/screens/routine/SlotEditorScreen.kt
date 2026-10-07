package com.forge.hypertrophy.ui.screens.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.ui.theme.NeonAccent

@Composable
fun SlotEditorScreen(
    viewModel: SlotEditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BuilderColumn(title = stringResource(R.string.builder_slots), onBack = onBack, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.builder_exercise))
            ChoiceButtons(
                labels = state.exercises.map { it.name },
                selected = state.exercises.indexOfFirst { it.id == state.exerciseId },
                onSelect = { index -> viewModel.onEvent(SlotEditorEvent.Exercise(state.exercises[index].id)) },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.builder_category))
            ChoiceButtons(
                labels = SlotCategory.entries.map { it.name },
                selected = SlotCategory.entries.indexOf(state.category),
                onSelect = { viewModel.onEvent(SlotEditorEvent.Category(SlotCategory.entries[it])) },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.builder_metric))
            ChoiceButtons(
                labels = MetricType.entries.map { it.name },
                selected = MetricType.entries.indexOf(state.metricType),
                onSelect = { viewModel.onEvent(SlotEditorEvent.Metric(MetricType.entries[it])) },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericEntry(
                    label = stringResource(R.string.builder_sets_min),
                    value = state.setsMin,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.onEvent(SlotEditorEvent.SetsMin(it ?: 0))
                }
                NumericEntry(
                    label = stringResource(R.string.builder_sets_max),
                    value = state.setsMax,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.onEvent(SlotEditorEvent.SetsMax(it ?: 0))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericEntry(
                    label = stringResource(R.string.builder_reps_low),
                    value = state.repsLow,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.onEvent(SlotEditorEvent.RepsLow(it))
                }
                NumericEntry(
                    label = stringResource(R.string.builder_reps_high),
                    value = state.repsHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.onEvent(SlotEditorEvent.RepsHigh(it))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                EditorButton(
                    label = stringResource(R.string.builder_amrap),
                    onClick = { viewModel.onEvent(SlotEditorEvent.Amrap(!state.isAmrap)) },
                )
                if (state.isAmrap) {
                    Text(
                        stringResource(R.string.builder_amrap),
                        color = NeonAccent,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericEntry(
                    label = stringResource(R.string.builder_hold),
                    value = state.holdTargetSec,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.onEvent(SlotEditorEvent.HoldTarget(it))
                }
                NumericEntry(
                    label = stringResource(R.string.builder_hold_max),
                    value = state.holdTargetMaxSec,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.onEvent(SlotEditorEvent.HoldTargetMax(it))
                }
            }
            NumericEntry(stringResource(R.string.builder_block), state.blockDurationSec) {
                viewModel.onEvent(SlotEditorEvent.BlockDuration(it))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericEntry(
                    label = stringResource(R.string.builder_rest_min),
                    value = state.restMinSec,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.onEvent(SlotEditorEvent.RestMin(it))
                }
                NumericEntry(
                    label = stringResource(R.string.builder_rest_max),
                    value = state.restMaxSec,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.onEvent(SlotEditorEvent.RestMax(it))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                EditorButton(
                    label = stringResource(R.string.builder_rest_as_needed),
                    onClick = { viewModel.onEvent(SlotEditorEvent.RestAsNeeded(!state.restAsNeeded)) },
                )
                if (state.restAsNeeded) {
                    Text(
                        stringResource(R.string.builder_rest_as_needed),
                        color = NeonAccent,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EditorButton(
                    label = stringResource(R.string.builder_optional),
                    onClick = { viewModel.onEvent(SlotEditorEvent.Optional(!state.isOptional)) },
                )
                if (state.isOptional) {
                    Text(
                        stringResource(R.string.builder_optional),
                        color = NeonAccent,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            OutlinedTextField(
                value = state.skipReasonLabel,
                onValueChange = { viewModel.onEvent(SlotEditorEvent.SkipReason(it)) },
                label = { Text(stringResource(R.string.builder_skip_reason)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.builder_progression))
            ChoiceButtons(
                labels = ProgressionRule.entries.map { it.name },
                selected = ProgressionRule.entries.indexOf(state.progressionRule),
                onSelect = { viewModel.onEvent(SlotEditorEvent.Progression(ProgressionRule.entries[it])) },
            )
            KgEntry(
                label = stringResource(R.string.builder_increment),
                value = state.incrementOverrideKg,
                onValue = { viewModel.onEvent(SlotEditorEvent.IncrementOverride(it)) },
            )
        }

        OutlinedTextField(
            value = state.notes,
            onValueChange = { viewModel.onEvent(SlotEditorEvent.Notes(it)) },
            label = { Text(stringResource(R.string.builder_notes)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.builder_skill_step))
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                EditorButton(
                    label = stringResource(R.string.builder_none),
                    onClick = { viewModel.onEvent(SlotEditorEvent.TargetStep(null)) },
                )
                state.skillSteps.forEach { step ->
                    EditorButton(
                        label = step.name.ifBlank { stringResource(R.string.builder_skill_step) },
                        onClick = { viewModel.onEvent(SlotEditorEvent.TargetStep(step.id)) },
                        enabled = state.targetSkillStepId != step.id,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.builder_superset))
            state.neighbours.forEach { neighbour ->
                val action = if (neighbour.sameGroup) R.string.builder_unpair else R.string.builder_pair
                EditorButton(
                    label = stringResource(action) + " " + neighbour.name,
                    onClick = { viewModel.onEvent(SlotEditorEvent.PairWith(neighbour.id)) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.builder_alternatives))
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                state.exercises.filter { it.id != state.exerciseId }.forEach { exercise ->
                    EditorButton(
                        label = exercise.name,
                        onClick = { viewModel.onEvent(SlotEditorEvent.ToggleAlternative(exercise.id)) },
                    )
                }
            }
            state.exercises.filter { it.id in state.alternativeExerciseIds }.forEach { exercise ->
                Text(exercise.name, color = NeonAccent)
            }
        }

        val error = state.validationError
        if (error != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        when (error) {
                            SlotValidationError.SETS -> R.string.builder_error_sets
                            SlotValidationError.REPS -> R.string.builder_error_reps
                            SlotValidationError.REST -> R.string.builder_error_rest
                            SlotValidationError.HOLD -> R.string.builder_error_hold
                        },
                    ),
                    color = NeonAccent,
                )
                EditorButton(
                    label = stringResource(R.string.builder_dismiss),
                    onClick = { viewModel.onEvent(SlotEditorEvent.DismissError) },
                )
            }
        }

        EditorButton(
            label = stringResource(R.string.builder_save),
            onClick = { viewModel.onEvent(SlotEditorEvent.Save) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChoiceButtons(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            EditorButton(
                label = label,
                onClick = { onSelect(index) },
                enabled = index != selected,
            )
        }
    }
}
