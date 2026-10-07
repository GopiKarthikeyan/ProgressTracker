package com.forge.hypertrophy.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.screens.routine.EditorButton
import com.forge.hypertrophy.ui.screens.routine.formatKg
import com.forge.hypertrophy.ui.theme.Black
import com.forge.hypertrophy.ui.theme.CardioBlue
import com.forge.hypertrophy.ui.theme.EmptyDay
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.RecoveryAmber
import com.forge.hypertrophy.ui.theme.RestGray
import com.forge.hypertrophy.ui.theme.White
import java.util.Locale

@Composable
fun DashboardScreen(
    onOpenWeeklyReview: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenWeeklyReview = onOpenWeeklyReview,
        onOpenSessions = onOpenSessions,
        onOpenWorkout = onOpenWorkout,
        modifier = modifier,
    )
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    onEvent: (DashboardEvent) -> Unit,
    onOpenWeeklyReview: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.nav_dashboard), style = MaterialTheme.typography.headlineSmall, color = White)
        Notice(state.notice, onEvent, onOpenWorkout)
        EditorButton(label = stringResource(R.string.review_open), onClick = onOpenWeeklyReview)
        EditorButton(label = stringResource(R.string.session_list_open), onClick = onOpenSessions)
        TodaySection(state.today, onEvent, onOpenWorkout)
        StreakSection(state.currentStreak, state.bestStreak)
        HeatmapSection(state.heatmap)
        RecordsSection(state.records)
        SkillsSection(state.skills)
        BodySection(state, onEvent)
        VolumeSection(state.volume)
        StallSection(state.stalls)
        DeloadSection(state.deload)
    }
}

@Composable
private fun Notice(
    notice: DashboardNotice?,
    onEvent: (DashboardEvent) -> Unit,
    onOpenWorkout: (Long) -> Unit
) {
    if (notice == null) return
    val text = when (notice) {
        DashboardNotice.WORKOUT_STARTED -> R.string.dashboard_notice_started
        DashboardNotice.ALREADY_IN_PROGRESS -> R.string.dashboard_notice_in_progress
        DashboardNotice.WEIGH_IN_SAVED -> R.string.dashboard_notice_weigh_in
        DashboardNotice.WEIGH_IN_INVALID -> R.string.dashboard_notice_invalid
        DashboardNotice.SCHEDULE_REJECTED -> R.string.dashboard_notice_rejected
    }
    Column {
        Text(stringResource(text), color = NeonAccent)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (notice == DashboardNotice.WORKOUT_STARTED || notice == DashboardNotice.ALREADY_IN_PROGRESS) {
                EditorButton(stringResource(R.string.workout_resume), { onOpenWorkout(0L) }) // ViewModel will pick up the active one
            }
            EditorButton(stringResource(R.string.dashboard_dismiss), { onEvent(DashboardEvent.DismissNotice) })
        }
    }
}

@Composable
private fun TodaySection(
    today: TodayCard?,
    onEvent: (DashboardEvent) -> Unit,
    onOpenWorkout: (Long) -> Unit
) {
    Section(stringResource(R.string.dashboard_today)) {
        if (today == null) {
            Text(stringResource(R.string.dashboard_empty_program), color = White)
            return@Section
        }
        Text(today.label, color = White, style = MaterialTheme.typography.titleMedium)
        Text(
            if (today.mode == ScheduleMode.FIXED) {
                stringResource(R.string.dashboard_mode_fixed)
            } else {
                stringResource(R.string.dashboard_mode_rolling)
            },
            color = White.copy(alpha = 0.72f),
        )
        if (today.isRest) {
            Text(stringResource(R.string.dashboard_rest_day), color = White)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.dashboard_estimate), color = White, modifier = Modifier.weight(1f))
            NumericText(formatDuration(today.estimatedSeconds), color = NeonAccent)
        }
        EditorButton(
            label = if (today.startEnabled) stringResource(R.string.dashboard_start) else stringResource(R.string.dashboard_resume),
            onClick = {
                if (today.startEnabled) {
                    onEvent(DashboardEvent.Start(shortOnTime = false))
                } else {
                    onOpenWorkout(0L)
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            EditorButton(
                label = stringResource(R.string.dashboard_start_short),
                onClick = { onEvent(DashboardEvent.Start(shortOnTime = true)) },
                enabled = today.startEnabled,
                modifier = Modifier.weight(1f),
            )
            NumericText(formatDuration(today.shortEstimatedSeconds), color = NeonAccent)
        }
        if (today.mode == ScheduleMode.ROLLING) {
            EditorButton(
                label = stringResource(R.string.dashboard_take_rest),
                onClick = { onEvent(DashboardEvent.TakeRestNow) },
                enabled = today.takeRestEnabled,
            )
            EditorButton(
                label = stringResource(R.string.dashboard_skip),
                onClick = { onEvent(DashboardEvent.SkipToNext) },
                enabled = today.skipEnabled,
            )
        } else {
            EditorButton(
                label = stringResource(R.string.dashboard_swap),
                onClick = { onEvent(DashboardEvent.SwapWithTomorrow) },
                enabled = today.swapEnabled,
            )
        }
    }
}

@Composable
private fun StreakSection(current: Int, best: Int) {
    Section(stringResource(R.string.dashboard_streak)) {
        Row {
            Text(stringResource(R.string.dashboard_streak_current), color = White, modifier = Modifier.weight(1f))
            NumericText(current.toString(), color = NeonAccent)
        }
        Row {
            Text(stringResource(R.string.dashboard_streak_best), color = White, modifier = Modifier.weight(1f))
            NumericText(best.toString(), color = NeonAccent)
        }
    }
}

@Composable
private fun HeatmapSection(cells: List<HeatmapCell>) {
    Section(stringResource(R.string.dashboard_heatmap)) {
        if (cells.none { it.kind != null }) {
            Text(stringResource(R.string.dashboard_heatmap_empty), color = White)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            cells.chunked(7).forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(kindColor(cell.kind)),
                        )
                    }
                }
            }
        }
        Legend(SessionKind.GYM, R.string.dashboard_kind_gym)
        Legend(SessionKind.CARDIO, R.string.dashboard_kind_cardio)
        Legend(SessionKind.ACTIVE_RECOVERY, R.string.dashboard_kind_recovery)
        Legend(SessionKind.REST, R.string.dashboard_kind_rest)
    }
}

@Composable
private fun Legend(kind: SessionKind, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(12.dp).background(kindColor(kind)))
        Text(stringResource(label), color = White)
    }
}

@Composable
private fun RecordsSection(records: List<ExerciseRecordUi>) {
    Section(stringResource(R.string.dashboard_records)) {
        if (records.isEmpty()) {
            Text(stringResource(R.string.dashboard_records_empty), color = White)
            return@Section
        }
        records.forEach { record ->
            Text(record.name, color = White)
            Row {
                Text(stringResource(R.string.dashboard_e1rm), color = White, modifier = Modifier.weight(1f))
                NumericText(
                    record.bestE1rmKg?.let(::formatKg) ?: stringResource(R.string.dashboard_none),
                    color = NeonAccent,
                )
            }
            Row {
                Text(stringResource(R.string.dashboard_weight_reps), color = White, modifier = Modifier.weight(1f))
                NumericText(weightReps(record), color = NeonAccent)
            }
        }
    }
}

@Composable
private fun SkillsSection(skills: List<SkillLadderUi>) {
    Section(stringResource(R.string.dashboard_skills)) {
        if (skills.isEmpty()) {
            Text(stringResource(R.string.dashboard_skills_empty), color = White)
            return@Section
        }
        skills.forEach { skill ->
            Text(skill.name, color = White)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                skill.tierNames.forEachIndexed { index, name ->
                    val current = index == skill.currentTier
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(width = 28.dp, height = 8.dp)
                                .background(if (current) NeonAccent else if (index < skill.currentTier) NeonAccent.copy(alpha = 0.4f) else EmptyDay),
                        )
                        Text(name, color = White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row {
                Text(stringResource(R.string.dashboard_stage), color = White, modifier = Modifier.weight(1f))
                NumericText(skill.stage.toString(), color = NeonAccent)
            }
            Row {
                Text(stringResource(R.string.dashboard_hold), color = White, modifier = Modifier.weight(1f))
                NumericText(
                    skill.maxHoldSec?.toString() ?: stringResource(R.string.dashboard_none),
                    color = NeonAccent,
                )
            }
        }
    }
}

@Composable
private fun BodySection(state: DashboardUiState, onEvent: (DashboardEvent) -> Unit) {
    Section(stringResource(R.string.dashboard_body)) {
        Text(stringResource(R.string.dashboard_weight), color = White)
        if (state.weight.samples.isEmpty()) {
            Text(stringResource(R.string.dashboard_weight_empty), color = White)
        } else {
            Text(stringResource(R.string.dashboard_average), color = White.copy(alpha = 0.72f))
            CompositionChart(state.weight.samples, state.weight.average)
        }
        Text(stringResource(R.string.dashboard_body_fat), color = White)
        if (state.bodyFat.samples.isEmpty()) {
            Text(stringResource(R.string.dashboard_body_fat_empty), color = White)
        } else {
            Text(stringResource(R.string.dashboard_average), color = White.copy(alpha = 0.72f))
            CompositionChart(state.bodyFat.samples, state.bodyFat.average)
        }
        Text(stringResource(R.string.dashboard_weigh_in), color = White)
        OutlinedTextField(
            value = state.weightDraft,
            onValueChange = { onEvent(DashboardEvent.WeightDraft(it)) },
            label = { Text(stringResource(R.string.dashboard_weight_kg)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"),
        )
        OutlinedTextField(
            value = state.bodyFatDraft,
            onValueChange = { onEvent(DashboardEvent.BodyFatDraft(it)) },
            label = { Text(stringResource(R.string.dashboard_body_fat_percent)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"),
        )
        EditorButton(stringResource(R.string.dashboard_save_weigh_in), { onEvent(DashboardEvent.SaveWeighIn) })
    }
}

@Composable
private fun VolumeSection(volume: List<MuscleVolumeUi>) {
    Section(stringResource(R.string.dashboard_volume)) {
        if (volume.isEmpty()) {
            Text(stringResource(R.string.dashboard_volume_empty), color = White)
            return@Section
        }
        volume.forEach { muscle ->
            Row {
                Text(muscle.muscle, color = White, modifier = Modifier.weight(1f))
                NumericText(formatSets(muscle.sets), color = NeonAccent)
            }
        }
    }
}

@Composable
private fun StallSection(stalls: List<String>) {
    Section(stringResource(R.string.dashboard_stalls)) {
        if (stalls.isEmpty()) {
            Text(stringResource(R.string.dashboard_stalls_empty), color = White)
            return@Section
        }
        stalls.forEach { name ->
            Text(name, color = NeonAccent)
        }
    }
}

@Composable
private fun DeloadSection(deload: DeloadStatus?) {
    Section(stringResource(R.string.dashboard_deload)) {
        if (deload == null) {
            Text(stringResource(R.string.dashboard_deload_empty), color = White)
            return@Section
        }
        Text(
            if (deload.rotationComplete) {
                stringResource(R.string.dashboard_deload_done)
            } else {
                stringResource(R.string.dashboard_deload_active)
            },
            color = NeonAccent,
        )
        deload.startedOn?.let { started ->
            Row {
                Text(stringResource(R.string.dashboard_started), color = White, modifier = Modifier.weight(1f))
                NumericText(started.toString(), color = NeonAccent)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, color = NeonAccent, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun weightReps(record: ExerciseRecordUi): String {
    val weight = record.bestWeightKg ?: return stringResource(R.string.dashboard_none)
    val reps = record.bestReps ?: return stringResource(R.string.dashboard_none)
    return stringResource(R.string.dashboard_weight_reps_value, formatKg(weight), reps)
}

private fun kindColor(kind: SessionKind?): Color = when (kind) {
    SessionKind.GYM -> NeonAccent
    SessionKind.CARDIO -> CardioBlue
    SessionKind.ACTIVE_RECOVERY -> RecoveryAmber
    SessionKind.REST -> RestGray
    null -> EmptyDay
}

internal fun formatDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.US, hours, minutes, secs)
    } else {
        "%d:%02d".format(Locale.US, minutes, secs)
    }
}

private fun formatSets(sets: Double): String {
    val rounded = kotlin.math.round(sets * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}
