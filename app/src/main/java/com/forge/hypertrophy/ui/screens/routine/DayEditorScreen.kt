package com.forge.hypertrophy.ui.screens.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.model.CardioType
import com.forge.hypertrophy.domain.model.ChecklistPhase
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.ReorderableColumn
import com.forge.hypertrophy.ui.components.SurfaceCard
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.Rose

@Composable
fun DayEditorScreen(
    viewModel: DayEditorViewModel,
    onBack: () -> Unit,
    onOpenSlot: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val weekdays = stringArrayResource(R.array.builder_weekdays)
    BuilderColumn(title = stringResource(R.string.builder_days), onBack = onBack, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.label,
                onValueChange = { viewModel.onEvent(DayEditorEvent.Label(it)) },
                label = { Text(stringResource(R.string.builder_day_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(stringResource(R.string.builder_weekday))
            FlowRow {
                EditorButton(
                    label = stringResource(R.string.builder_weekday_none),
                    onClick = { viewModel.onEvent(DayEditorEvent.Weekday(null)) },
                )
                weekdays.forEachIndexed { index, label ->
                    val selected = state.weekday == index + 1
                    EditorButton(
                        label = label,
                        onClick = { viewModel.onEvent(DayEditorEvent.Weekday(index + 1)) },
                        enabled = !selected,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericEntry(
                label = stringResource(R.string.builder_prep),
                value = state.prepMinutes,
                onValue = { viewModel.onEvent(DayEditorEvent.PrepMinutes(it)) },
            )
            NumericEntry(
                label = stringResource(R.string.builder_cooldown),
                value = state.cooldownMinutes,
                onValue = { viewModel.onEvent(DayEditorEvent.CooldownMinutes(it)) },
            )
        }

        ChecklistSection(
            title = stringResource(R.string.builder_prep),
            items = state.prepItems,
            onAdd = { viewModel.onEvent(DayEditorEvent.AddChecklist(ChecklistPhase.PREP)) },
            onEvent = viewModel::onEvent,
        )
        ChecklistSection(
            title = stringResource(R.string.builder_cooldown),
            items = state.cooldownItems,
            onAdd = { viewModel.onEvent(DayEditorEvent.AddChecklist(ChecklistPhase.COOLDOWN)) },
            onEvent = viewModel::onEvent,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.builder_cardio))
            FlowRow {
                CardioType.entries.forEach { type ->
                    EditorButton(
                        label = type.name,
                        onClick = { viewModel.onEvent(DayEditorEvent.CardioTypeChanged(type)) },
                        enabled = state.cardioType != type,
                    )
                }
            }
            OutlinedTextField(
                value = state.cardioLabel,
                onValueChange = { viewModel.onEvent(DayEditorEvent.CardioLabel(it)) },
                label = { Text(stringResource(R.string.builder_cardio_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            NumericEntry(
                label = stringResource(R.string.builder_distance_m),
                value = state.cardioDistanceM,
                onValue = { viewModel.onEvent(DayEditorEvent.CardioDistance(it)) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                EditorButton(
                    label = stringResource(R.string.builder_optional),
                    onClick = { viewModel.onEvent(DayEditorEvent.CardioOptional(!state.cardioOptional)) },
                )
                if (state.cardioOptional) {
                    Text(
                        stringResource(R.string.builder_optional),
                        color = NeonAccent,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        EditorButton(
            label = stringResource(R.string.builder_save),
            onClick = { viewModel.onEvent(DayEditorEvent.Save) },
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.builder_slots))
            if (state.needsExercise) {
                Text(stringResource(R.string.builder_need_exercise))
                EditorButton(
                    label = stringResource(R.string.builder_dismiss),
                    onClick = { viewModel.onEvent(DayEditorEvent.DismissNeedsExercise) },
                )
            }
            EditorButton(
                label = stringResource(R.string.builder_add),
                onClick = { viewModel.onEvent(DayEditorEvent.AddSlot) },
            )
            ReorderableColumn(
                items = state.slots,
                itemKey = { it.id },
                onMove = { from, to -> viewModel.onEvent(DayEditorEvent.MoveSlot(from, to)) },
            ) { slot ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            onClick = { onOpenSlot(slot.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = TouchTargets.Editor),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(
                                    text = slot.title.ifBlank { "—" },
                                    color = Ink,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = slot.detail,
                                    color = Rose,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            EditorButton(
                                label = stringResource(R.string.baseline_minus),
                                onClick = {
                                    viewModel.onEvent(DayEditorEvent.StepBaselineWeight(slot.id, -1))
                                },
                            )
                            NumericText(
                                text = formatKg(slot.baselineWeightKg ?: 0.0),
                                color = Ink,
                                modifier = Modifier.widthIn(min = 40.dp),
                                textAlign = TextAlign.Center,
                            )
                            EditorButton(
                                label = stringResource(R.string.baseline_plus),
                                onClick = {
                                    viewModel.onEvent(DayEditorEvent.StepBaselineWeight(slot.id, 1))
                                },
                            )
                        }
                    }
                    DeleteIconButton(
                        onClick = { viewModel.onEvent(DayEditorEvent.DeleteSlot(slot.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecklistSection(
    title: String,
    items: List<ChecklistRow>,
    onAdd: () -> Unit,
    onEvent: (DayEditorEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title)
        EditorButton(label = stringResource(R.string.builder_add), onClick = onAdd)
        items.forEach { item ->
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = item.text,
                            onValueChange = { onEvent(DayEditorEvent.ChecklistText(item.id, it)) },
                            label = { Text(stringResource(R.string.builder_checklist_text)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        DeleteIconButton(
                            onClick = { onEvent(DayEditorEvent.DeleteChecklist(item.id)) },
                        )
                    }
                    NumericEntry(
                        label = stringResource(R.string.builder_reps),
                        value = item.reps,
                        onValue = { onEvent(DayEditorEvent.ChecklistReps(item.id, it)) },
                    )
                    NumericEntry(
                        label = stringResource(R.string.builder_seconds),
                        value = item.seconds,
                        onValue = { onEvent(DayEditorEvent.ChecklistSeconds(item.id, it)) },
                    )
                }
            }
        }
    }
}
