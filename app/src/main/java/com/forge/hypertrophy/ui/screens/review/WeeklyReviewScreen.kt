package com.forge.hypertrophy.ui.screens.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.engine.MuscleWeekComparison
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.SurfaceCard
import com.forge.hypertrophy.ui.screens.routine.BuilderColumn
import com.forge.hypertrophy.ui.screens.routine.EditorButton
import com.forge.hypertrophy.ui.screens.routine.formatKg
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.Rose
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeeklyReviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeeklyReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WeeklyReviewContent(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

@Composable
fun WeeklyReviewContent(
    state: WeeklyReviewUiState,
    onEvent: (WeeklyReviewEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    BuilderColumn(title = stringResource(R.string.review_title), onBack = onBack, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            EditorButton(label = stringResource(R.string.review_previous_week), onClick = { onEvent(WeeklyReviewEvent.PreviousWeek) })
            Text(
                text = if (state.weekStart != null && state.weekEnd != null) {
                    stringResource(R.string.review_week_range, formatter.format(state.weekStart), formatter.format(state.weekEnd))
                } else {
                    ""
                },
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            EditorButton(
                label = stringResource(R.string.review_next_week),
                onClick = { onEvent(WeeklyReviewEvent.NextWeek) },
                enabled = !state.isCurrentWeek,
            )
        }
        Section(stringResource(R.string.review_sessions)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_this_week), color = Ink, modifier = Modifier.weight(1f))
                NumericText(state.sessionsCompleted.toString(), color = Rose)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_last_week), color = Ink.copy(alpha = 0.72f), modifier = Modifier.weight(1f))
                NumericText(state.previousSessionsCompleted.toString(), color = Ink.copy(alpha = 0.72f))
            }
        }
        Section(stringResource(R.string.review_prs)) {
            if (state.prs.isEmpty()) {
                Text(stringResource(R.string.review_prs_empty), color = Ink)
            }
            state.prs.forEach { pr ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(pr.exerciseName, color = Ink, modifier = Modifier.weight(1f))
                    NumericText(
                        stringResource(R.string.review_pr_value, formatKg(pr.weightKg), pr.reps, formatKg(pr.e1rmKg)),
                        color = Rose,
                    )
                }
            }
        }
        Section(stringResource(R.string.review_stages)) {
            if (state.advancements.isEmpty()) {
                Text(stringResource(R.string.review_stages_empty), color = Ink)
            }
            state.advancements.forEach { step ->
                Column {
                    Text(step.skillName, color = Ink, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(
                            R.string.review_stage_change,
                            step.fromTierName,
                            step.fromStage,
                            step.toTierName,
                            step.toStage,
                        ),
                        color = Rose,
                    )
                }
            }
        }
        Section(stringResource(R.string.review_volume)) {
            if (state.volume.isEmpty()) {
                Text(stringResource(R.string.review_volume_empty), color = Ink)
            }
            state.volume.forEach { VolumeRow(it) }
        }
    }
}

@Composable
private fun VolumeRow(row: MuscleWeekComparison) {
    val delta = row.delta
    val deltaText = when {
        delta > 0.0 -> stringResource(R.string.review_delta_up, formatSets(delta))
        delta < 0.0 -> stringResource(R.string.review_delta_down, formatSets(-delta))
        else -> stringResource(R.string.review_delta_same)
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(row.muscle, color = Ink, modifier = Modifier.weight(1f))
        NumericText(
            pluralStringResource(R.plurals.review_sets, row.thisWeek.toInt(), formatSets(row.thisWeek)),
            color = Rose,
        )
        NumericText(
            text = "  $deltaText",
            color = Ink.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    SurfaceCard {
        Text(title, color = Rose, style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            content()
        }
    }
}

private fun formatSets(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
