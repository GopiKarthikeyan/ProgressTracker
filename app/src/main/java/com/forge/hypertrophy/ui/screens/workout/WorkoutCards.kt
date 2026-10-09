package com.forge.hypertrophy.ui.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.model.ProgressionAction
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.workout.JointFlags
import com.forge.hypertrophy.domain.workout.TimerPhase
import com.forge.hypertrophy.domain.workout.WorkoutPosition
import com.forge.hypertrophy.domain.workout.setReadout
import com.forge.hypertrophy.domain.workout.showsHold
import com.forge.hypertrophy.domain.workout.showsReps
import com.forge.hypertrophy.domain.workout.showsWeight
import com.forge.hypertrophy.ui.components.CardShape
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.ProgressRing
import com.forge.hypertrophy.ui.components.SecondaryButton
import com.forge.hypertrophy.ui.components.StepperButton
import com.forge.hypertrophy.ui.components.SurfaceCard
import com.forge.hypertrophy.ui.components.ToggleChip
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.screens.routine.formatKg
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.Muted
import com.forge.hypertrophy.ui.theme.Rose
import com.forge.hypertrophy.ui.theme.RoseSoft
import com.forge.hypertrophy.ui.theme.Sand
import com.forge.hypertrophy.ui.theme.White

@Composable
internal fun WorkoutHeader(
    title: String,
    subtitle: String?,
    etaSeconds: Int?,
    showMore: Boolean,
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoundIconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.builder_back),
                tint = Ink,
            )
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = Ink,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null || etaSeconds != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (subtitle != null) {
                        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (etaSeconds != null) {
                        NumericText(
                            text = formatDuration(etaSeconds),
                            color = Rose,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
        if (showMore) {
            RoundIconButton(onClick = onMore) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.workout_more),
                    tint = Ink,
                )
            }
        } else {
            Spacer(Modifier.size(TouchTargets.Workout))
        }
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(TouchTargets.Workout)
            .background(Sand, CircleShape),
    ) {
        content()
    }
}

@Composable
internal fun ReadinessCard(
    sessionReady: Boolean,
    onEvent: (WorkoutEvent) -> Unit,
) {
    var sleep by remember { mutableStateOf<Int?>(null) }
    var soreness by remember { mutableStateOf<Int?>(null) }
    var energy by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(sessionReady, sleep, soreness, energy) {
        val nextSleep = sleep
        val nextSoreness = soreness
        val nextEnergy = energy
        if (!sessionReady || nextSleep == null || nextSoreness == null || nextEnergy == null) return@LaunchedEffect
        onEvent(WorkoutEvent.SubmitReadiness(nextSleep, nextSoreness, nextEnergy))
    }
    fun choose(nextSleep: Int?, nextSoreness: Int?, nextEnergy: Int?) {
        sleep = nextSleep
        soreness = nextSoreness
        energy = nextEnergy
    }
    SurfaceCard {
        Text(
            stringResource(R.string.workout_readiness_scale),
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        ScoreRow(stringResource(R.string.workout_readiness_sleep), sleep) { choose(it, soreness, energy) }
        Spacer(Modifier.height(12.dp))
        ScoreRow(stringResource(R.string.workout_readiness_soreness), soreness) { choose(sleep, it, energy) }
        Spacer(Modifier.height(12.dp))
        ScoreRow(stringResource(R.string.workout_readiness_energy), energy) { choose(sleep, soreness, it) }
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            label = stringResource(R.string.workout_readiness_skip),
            onClick = { onEvent(WorkoutEvent.SkipReadiness) },
        )
    }
}

@Composable
private fun ScoreRow(title: String, selected: Int?, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                1 to R.string.workout_readiness_poor,
                2 to R.string.workout_readiness_ok,
                3 to R.string.workout_readiness_great,
            ).forEach { (score, label) ->
                ToggleChip(
                    label = stringResource(label),
                    selected = selected == score,
                    onClick = { onSelect(score) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun PrepCard(pos: WorkoutPosition.Prep, onEvent: (WorkoutEvent) -> Unit) {
    SurfaceCard {
        pos.items.forEach { item ->
            CheckRow(item.text, item.done) { onEvent(WorkoutEvent.CheckOff(item.id)) }
        }
    }
}

@Composable
internal fun CooldownCard(pos: WorkoutPosition.Cooldown, onEvent: (WorkoutEvent) -> Unit) {
    SurfaceCard {
        pos.items.forEach { item ->
            CheckRow(item.text, item.done) { onEvent(WorkoutEvent.CheckOff(item.id)) }
        }
    }
}

@Composable
private fun CheckRow(text: String, done: Boolean, onCheck: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTargets.Workout)
            .clip(CardShape)
            .background(if (done) RoseSoft.copy(alpha = 0.35f) else Sand)
            .clickable(enabled = !done, onClick = onCheck)
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .border(2.dp, if (done) Rose else Muted, CircleShape)
                .background(if (done) Rose else White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = text,
            color = if (done) Muted else Ink,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
internal fun WorkingSetCard(
    pos: WorkoutPosition.WorkingSet,
    onEvent: (WorkoutEvent) -> Unit,
) {
    val readout = setReadout(pos.slot.prescription.metricType, pos.slot.equipment)
    val faded = pos.suggestion.fromPreviousSession
    SurfaceCard(color = Sand) {
        Text(
            stringResource(R.string.workout_set_label, pos.setNumber, pos.setCount),
            color = Muted,
            style = MaterialTheme.typography.labelLarge,
        )
        if (pos.side != SetSide.BOTH) {
            Text(
                stringResource(if (pos.side == SetSide.LEFT) R.string.workout_side_left else R.string.workout_side_right),
                color = Rose,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        pos.slot.prescription.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(notes, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        if (readout.showsWeight()) {
            MetricStepper(
                value = pos.suggestion.weightKg?.let(::formatKg) ?: stringResource(R.string.workout_unset),
                unit = stringResource(R.string.workout_kg_unit),
                faded = faded && pos.suggestion.weightKg != null,
                minusDescription = stringResource(R.string.workout_adjust_weight_minus),
                plusDescription = stringResource(R.string.workout_adjust_weight_plus),
                onMinus = { onEvent(WorkoutEvent.Adjust(weightDeltaKg = -2.5)) },
                onPlus = { onEvent(WorkoutEvent.Adjust(weightDeltaKg = 2.5)) },
            )
            PlatesLine(pos.requiredPlates)
        }
        if (readout.showsReps()) {
            MetricStepper(
                value = pos.suggestion.reps?.toString() ?: stringResource(R.string.workout_unset),
                unit = stringResource(R.string.workout_reps_unit),
                faded = faded && pos.suggestion.reps != null,
                minusDescription = stringResource(R.string.workout_adjust_reps_minus),
                plusDescription = stringResource(R.string.workout_adjust_reps_plus),
                onMinus = { onEvent(WorkoutEvent.Adjust(repDelta = -1)) },
                onPlus = { onEvent(WorkoutEvent.Adjust(repDelta = 1)) },
            )
        }
        if (readout.showsHold()) {
            MetricStepper(
                value = pos.suggestion.holdSec?.toString() ?: stringResource(R.string.workout_unset),
                unit = stringResource(R.string.workout_seconds),
                faded = faded && pos.suggestion.holdSec != null,
                minusDescription = stringResource(R.string.workout_adjust_hold_minus),
                plusDescription = stringResource(R.string.workout_adjust_hold_plus),
                onMinus = { onEvent(WorkoutEvent.Adjust(holdDelta = -1)) },
                onPlus = { onEvent(WorkoutEvent.Adjust(holdDelta = 1)) },
            )
        }
        if (faded) {
            Text(
                stringResource(R.string.workout_from_previous),
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        pos.regulation?.let { prompt ->
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.workout_regulation_suggestion,
                    formatRpe(prompt.lastRpe),
                    formatKg(prompt.suggestedWeightKg),
                ),
                color = Ink,
                style = MaterialTheme.typography.bodyLarge,
            )
            SecondaryButton(
                label = stringResource(R.string.workout_regulation_accept),
                onClick = { onEvent(WorkoutEvent.AcceptRegulation) },
                accent = true,
            )
            SecondaryButton(
                label = stringResource(R.string.workout_regulation_dismiss),
                onClick = { onEvent(WorkoutEvent.DismissRegulation) },
            )
        }
    }
}

@Composable
private fun MetricStepper(
    value: String,
    unit: String,
    faded: Boolean,
    minusDescription: String,
    plusDescription: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val color = if (faded) Muted else Ink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepperButton("−", minusDescription, onMinus)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NumericText(
                text = value,
                color = color,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
            )
            Text(unit, color = color, style = MaterialTheme.typography.titleMedium)
        }
        StepperButton("+", plusDescription, onPlus)
    }
}

@Composable
internal fun RestCard(
    pos: WorkoutPosition.Resting,
    state: WorkoutUiState,
    onEvent: (WorkoutEvent) -> Unit,
) {
    val readout = setReadout(pos.slot.prescription.metricType, pos.slot.equipment)
    val lastSet = pos.slot.sets.maxByOrNull { it.id }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (readout.showsWeight() && lastSet != null) {
            SurfaceCard {
                Text(stringResource(R.string.workout_rpe_prompt), color = Ink, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(8.0, 9.0, 10.0).forEach { rpe ->
                        ToggleChip(
                            label = formatRpe(rpe),
                            selected = lastSet.rpe == rpe,
                            onClick = { onEvent(WorkoutEvent.SetRpe(lastSet.id, rpe)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        state.nextUp?.let { next -> NextUpCard(next) }
    }
}

@Composable
private fun NextUpCard(next: NextUp) {
    SurfaceCard(color = Sand) {
        Text(stringResource(R.string.workout_next_title), color = Muted, style = MaterialTheme.typography.labelLarge)
        Text(next.exerciseName, color = Ink, style = MaterialTheme.typography.titleLarge)
        PlatesLine(next.platesPerSideKg)
        if (next.warmup.isNotEmpty()) {
            Text(stringResource(R.string.workout_warmup_title), color = Muted, style = MaterialTheme.typography.labelLarge)
            next.warmup.forEach { step ->
                Text(
                    stringResource(R.string.workout_warmup_step, formatKg(step.weightKg), step.reps),
                    color = Ink,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
internal fun PracticeBlockCard(state: WorkoutUiState, slotId: Long) {
    val timer = state.timer
    val fraction = blockProgress(slotId, timer.remainingMillis, timer.phase)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProgressRing(progress = fraction, size = 160.dp, stroke = 12.dp) {
            NumericText(
                text = clockText(timer.phase, timer.remainingMillis, timer.elapsedMillis),
                color = Ink,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun blockProgress(key: Any, remainingMillis: Long, phase: TimerPhase): Float {
    var ceiling by remember(key) { mutableStateOf(0L) }
    if ((phase == TimerPhase.COUNTDOWN || phase == TimerPhase.WARNING) && remainingMillis > ceiling) {
        ceiling = remainingMillis
    }
    if (phase == TimerPhase.OVERTIME || phase == TimerPhase.FINISHED) return 1f
    if (ceiling <= 0L) return 0f
    val done = (ceiling - remainingMillis).coerceAtLeast(0)
    return (done.toFloat() / ceiling.toFloat()).coerceIn(0f, 1f)
}

@Composable
internal fun SummaryCard(summary: WorkoutSummary?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        summary?.prs?.forEach { pr ->
            SurfaceCard {
                Text(
                    stringResource(R.string.workout_pr_summary, pr.exerciseName, formatKg(pr.e1rmKg)),
                    color = Ink,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        summary?.stagePrompts?.forEach { prompt ->
            SurfaceCard(color = Sand) {
                Text(
                    stringResource(R.string.workout_stage_summary, prompt.skillName, prompt.stage),
                    color = Rose,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        summary?.nextSession?.forEach { note ->
            SurfaceCard {
                val action = stringResource(progressionLabel(note.action))
                val line = note.weightKg?.let { weight ->
                    stringResource(R.string.workout_next_session_weight, note.exerciseName, action, formatKg(weight))
                } ?: stringResource(R.string.workout_next_session_line, note.exerciseName, action)
                Text(line, color = Ink, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PlatesLine(plates: List<Double>) {
    if (plates.isEmpty()) return
    Text(
        text = stringResource(R.string.workout_plates_per_side, plates.joinToString(" + ", transform = ::formatKg)),
        color = Muted,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutMoreSheet(
    pos: WorkoutPosition.WorkingSet,
    jointFlags: Set<String>,
    shortOnTime: Boolean,
    cuesEnabled: Boolean,
    skipReason: String,
    onEvent: (WorkoutEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White,
        contentColor = Ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.workout_more),
                    color = Ink,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    label = stringResource(R.string.workout_close),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(0.35f),
                )
            }
            ToggleChip(
                label = stringResource(if (shortOnTime) R.string.workout_short_off else R.string.workout_short_on),
                selected = shortOnTime,
                onClick = { onEvent(WorkoutEvent.ToggleShortOnTime) },
            )
            ToggleChip(
                label = stringResource(if (cuesEnabled) R.string.workout_cues_on else R.string.workout_cues_off),
                selected = cuesEnabled,
                onClick = { onEvent(WorkoutEvent.Cues(!cuesEnabled)) },
            )
            if (pos.slot.alternatives.isNotEmpty() || pos.slot.chosenAlternativeExerciseId != null) {
                pos.slot.alternatives.forEach { choice ->
                    ToggleChip(
                        label = stringResource(R.string.workout_alternative, choice.name),
                        selected = choice.id == pos.slot.chosenAlternativeExerciseId,
                        onClick = {
                            onEvent(WorkoutEvent.ChooseAlternative(choice.id))
                            onDismiss()
                        },
                    )
                }
                if (pos.slot.chosenAlternativeExerciseId != null) {
                    SecondaryButton(
                        label = stringResource(R.string.workout_alternative_planned),
                        onClick = {
                            onEvent(WorkoutEvent.ChooseAlternative(null))
                            onDismiss()
                        },
                    )
                }
            }
            if (pos.slotCount > 1) {
                Text(stringResource(R.string.workout_section_order), color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        label = stringResource(R.string.workout_move_earlier),
                        onClick = {
                            onEvent(WorkoutEvent.MoveSlot(pos.slotIndex, pos.slotIndex - 1))
                            onDismiss()
                        },
                        enabled = pos.slotIndex > 0,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        label = stringResource(R.string.workout_move_later),
                        onClick = {
                            onEvent(WorkoutEvent.MoveSlot(pos.slotIndex, pos.slotIndex + 1))
                            onDismiss()
                        },
                        enabled = pos.slotIndex < pos.slotCount - 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            SetupNotes(pos, onEvent)
            Text(stringResource(R.string.workout_section_joints), color = Muted)
            JointFlags.all.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { flag ->
                        ToggleChip(
                            label = stringResource(jointLabel(flag)),
                            selected = flag in jointFlags,
                            onClick = { onEvent(WorkoutEvent.ToggleJoint(flag)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            SecondaryButton(
                label = stringResource(R.string.workout_add_set),
                onClick = {
                    onEvent(WorkoutEvent.AddSet)
                    onDismiss()
                },
            )
            SecondaryButton(
                label = stringResource(R.string.workout_skip_exercise),
                onClick = {
                    onEvent(WorkoutEvent.Skip(skipReason))
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun SetupNotes(pos: WorkoutPosition.WorkingSet, onEvent: (WorkoutEvent) -> Unit) {
    var notes by remember(pos.slot.sessionSlotId, pos.slot.setupNotes) {
        mutableStateOf(pos.slot.setupNotes)
    }
    OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text(stringResource(R.string.workout_setup_notes)) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTargets.Workout),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            focusedBorderColor = Rose,
            unfocusedBorderColor = Muted.copy(alpha = 0.4f),
            focusedLabelColor = Rose,
            unfocusedLabelColor = Muted,
            cursorColor = Rose,
        ),
    )
    if (notes != pos.slot.setupNotes) {
        SecondaryButton(
            label = stringResource(R.string.workout_setup_save),
            onClick = { onEvent(WorkoutEvent.EditSetupNotes(notes)) },
            accent = true,
        )
    }
}

internal fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(java.util.Locale.US, mins, secs)
}

private fun formatRpe(rpe: Double): String =
    if (rpe % 1.0 == 0.0) rpe.toInt().toString() else formatKg(rpe)

private fun jointLabel(flag: String): Int = when (flag) {
    JointFlags.SHOULDER -> R.string.workout_joint_shoulder
    JointFlags.ELBOW -> R.string.workout_joint_elbow
    JointFlags.WRIST -> R.string.workout_joint_wrist
    JointFlags.KNEE -> R.string.workout_joint_knee
    else -> R.string.workout_joint_back
}

private fun progressionLabel(action: ProgressionAction): Int = when (action) {
    ProgressionAction.HOLD -> R.string.progression_hold
    ProgressionAction.INCREASE -> R.string.progression_increase
    ProgressionAction.DECREASE -> R.string.progression_decrease
    ProgressionAction.VARIATION_OR_ADDED_LOAD -> R.string.progression_variation
    ProgressionAction.STALL -> R.string.progression_stall
    ProgressionAction.COLD_START -> R.string.progression_cold_start
    ProgressionAction.BASELINE -> R.string.progression_baseline
}
