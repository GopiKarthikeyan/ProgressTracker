package com.forge.hypertrophy.ui.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.workout.WorkoutPosition
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.screens.routine.EditorButton
import com.forge.hypertrophy.ui.screens.routine.formatKg
import com.forge.hypertrophy.ui.theme.Black
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.White

@Composable
fun WorkoutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WorkoutContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun WorkoutContent(
    state: WorkoutUiState,
    onEvent: (WorkoutEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Black)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(onBack = onBack, etaSeconds = state.etaSeconds)

        when (val pos = state.position) {
            is WorkoutPosition.Readiness -> ReadinessView(onEvent)
            is WorkoutPosition.Prep -> PrepView(pos, onEvent)
            is WorkoutPosition.PracticeBlock -> PracticeBlockView(pos, state, onEvent)
            is WorkoutPosition.WorkingSet -> WorkingSetView(pos, state, onEvent)
            is WorkoutPosition.Resting -> RestingView(pos, state, onEvent)
            is WorkoutPosition.Cooldown -> CooldownView(pos, onEvent)
            is WorkoutPosition.Summary -> SummaryView(state.summary, onBack)
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit, etaSeconds: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditorButton(label = stringResource(R.string.builder_back), onClick = onBack)
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.workout_eta), color = White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            NumericText(formatDuration(etaSeconds), color = NeonAccent)
        }
    }
}

@Composable
private fun ReadinessView(onEvent: (WorkoutEvent) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.workout_readiness_title), color = White, style = MaterialTheme.typography.headlineSmall)
        // Basic readiness inputs could go here
        EditorButton(label = stringResource(R.string.workout_readiness_submit), onClick = { onEvent(WorkoutEvent.SubmitReadiness(5, 5, 5)) })
        EditorButton(label = stringResource(R.string.workout_readiness_skip), onClick = { onEvent(WorkoutEvent.SkipReadiness) })
    }
}

@Composable
private fun PrepView(pos: WorkoutPosition.Prep, onEvent: (WorkoutEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.workout_prep_title), color = NeonAccent, style = MaterialTheme.typography.titleMedium)
        pos.items.forEach { item ->
            EditorButton(
                label = item.text,
                onClick = { onEvent(WorkoutEvent.CheckOff(item.id)) },
                enabled = !item.done
            )
        }
        if (pos.items.all { it.done }) {
            EditorButton(label = stringResource(R.string.workout_start_first), onClick = { onEvent(WorkoutEvent.Primary(com.forge.hypertrophy.domain.model.EntryMethod.SCREEN)) })
        }
    }
}

@Composable
private fun WorkingSetView(
    pos: WorkoutPosition.WorkingSet,
    state: WorkoutUiState,
    onEvent: (WorkoutEvent) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(pos.slot.exerciseName, color = NeonAccent, style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.workout_set_label, pos.setNumber, pos.setCount), color = White)
        
        val weight = pos.suggestion.weightKg ?: 0.0
        val reps = pos.suggestion.reps ?: 0
        
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NumericText(text = stringResource(R.string.workout_weight_kg, weight), color = White, style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.workout_reps_multiplier), color = White, style = MaterialTheme.typography.headlineMedium)
            NumericText(text = reps.toString(), color = White, style = MaterialTheme.typography.headlineLarge)
        }

        PlatesPerSide(pos.requiredPlates)

        pos.regulation?.let { prompt ->
            Text(
                text = stringResource(
                    R.string.workout_regulation_suggestion,
                    formatRpe(prompt.lastRpe),
                    formatKg(prompt.suggestedWeightKg),
                ),
                color = White.copy(alpha = 0.7f),
            )
            EditorButton(
                label = stringResource(R.string.workout_regulation_accept),
                onClick = { onEvent(WorkoutEvent.AcceptRegulation) },
                modifier = Modifier.fillMaxWidth(),
            )
            EditorButton(
                label = stringResource(R.string.workout_regulation_dismiss),
                onClick = { onEvent(WorkoutEvent.DismissRegulation) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorButton(label = stringResource(R.string.workout_adjust_weight_minus), onClick = { onEvent(WorkoutEvent.Adjust(weightDeltaKg = -2.5)) })
            EditorButton(label = stringResource(R.string.workout_adjust_weight_plus), onClick = { onEvent(WorkoutEvent.Adjust(weightDeltaKg = 2.5)) })
            EditorButton(label = stringResource(R.string.workout_adjust_reps_minus), onClick = { onEvent(WorkoutEvent.Adjust(repDelta = -1)) })
            EditorButton(label = stringResource(R.string.workout_adjust_reps_plus), onClick = { onEvent(WorkoutEvent.Adjust(repDelta = 1)) })
        }

        EditorButton(
            label = stringResource(R.string.workout_log_set),
            onClick = { onEvent(WorkoutEvent.Primary(com.forge.hypertrophy.domain.model.EntryMethod.SCREEN)) },
            modifier = Modifier.fillMaxWidth()
        )
        
        EditorButton(label = stringResource(R.string.workout_skip_exercise), onClick = { onEvent(WorkoutEvent.Skip("Not feeling it")) })
    }
}

@Composable
private fun RestingView(
    pos: WorkoutPosition.Resting,
    state: WorkoutUiState,
    onEvent: (WorkoutEvent) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.workout_resting_title), color = White, style = MaterialTheme.typography.headlineSmall)
        
        val timer = state.timer
        NumericText(
            text = formatDuration((timer.remainingMillis / 1000).toInt()),
            color = NeonAccent,
            style = MaterialTheme.typography.displayLarge
        )

        EditorButton(label = stringResource(R.string.workout_skip_rest), onClick = { onEvent(WorkoutEvent.Primary(com.forge.hypertrophy.domain.model.EntryMethod.SCREEN)) })
        
        state.nextUp?.let { next ->
            Text(stringResource(R.string.workout_next_up, next.exerciseName), color = White.copy(alpha = 0.7f))
            PlatesPerSide(next.platesPerSideKg)
        }
    }
}

@Composable
private fun PlatesPerSide(plates: List<Double>) {
    if (plates.isEmpty()) return
    Text(
        text = stringResource(R.string.workout_plates_per_side, plates.joinToString(" + ", transform = ::formatKg)),
        color = White.copy(alpha = 0.7f),
    )
}

private fun formatRpe(rpe: Double): String {
    return if (rpe % 1.0 == 0.0) rpe.toInt().toString() else formatKg(rpe)
}

@Composable
private fun PracticeBlockView(
    pos: WorkoutPosition.PracticeBlock,
    state: WorkoutUiState,
    onEvent: (WorkoutEvent) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(pos.slot.exerciseName, color = NeonAccent, style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.workout_timed_block), color = White)
        
        val timer = state.timer
        NumericText(
            text = formatDuration((timer.remainingMillis / 1000).toInt()),
            color = NeonAccent,
            style = MaterialTheme.typography.displayLarge
        )

        EditorButton(label = stringResource(R.string.workout_complete_block), onClick = { onEvent(WorkoutEvent.Primary(com.forge.hypertrophy.domain.model.EntryMethod.SCREEN)) })
    }
}

@Composable
private fun CooldownView(pos: WorkoutPosition.Cooldown, onEvent: (WorkoutEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.workout_cooldown_title), color = NeonAccent, style = MaterialTheme.typography.titleMedium)
        pos.items.forEach { item ->
            EditorButton(
                label = item.text,
                onClick = { onEvent(WorkoutEvent.CheckOff(item.id)) },
                enabled = !item.done
            )
        }
        if (pos.items.all { it.done }) {
            EditorButton(label = stringResource(R.string.workout_finish), onClick = { onEvent(WorkoutEvent.CompleteWorkout) })
        }
    }
}

@Composable
private fun SummaryView(summary: WorkoutSummary?, onBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.workout_complete_title), color = NeonAccent, style = MaterialTheme.typography.headlineMedium)
        
        summary?.prs?.forEach { pr ->
            Text(stringResource(R.string.workout_pr_summary, pr.exerciseName, pr.e1rmKg), color = White)
        }
        
        EditorButton(label = stringResource(R.string.workout_done), onClick = onBack)
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(java.util.Locale.US, mins, secs)
}
