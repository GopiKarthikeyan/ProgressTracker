package com.forge.hypertrophy.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.forge.hypertrophy.ui.components.PrimaryButton
import com.forge.hypertrophy.ui.components.ProgressRing
import com.forge.hypertrophy.ui.components.SecondaryButton
import com.forge.hypertrophy.ui.components.SurfaceCard
import com.forge.hypertrophy.ui.screens.cardio.activityLabel
import com.forge.hypertrophy.ui.screens.routine.formatKg
import com.forge.hypertrophy.ui.theme.CardioBlue
import com.forge.hypertrophy.ui.theme.Cream
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.Muted
import com.forge.hypertrophy.ui.theme.RecoveryAmber
import com.forge.hypertrophy.ui.theme.RestGray
import com.forge.hypertrophy.ui.theme.Rose
import com.forge.hypertrophy.ui.theme.Sand
import com.forge.hypertrophy.ui.theme.White
import java.time.DayOfWeek
import java.time.format.TextStyle
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
            .background(Cream)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.nav_dashboard), style = MaterialTheme.typography.headlineMedium, color = Ink)
        Notice(state.notice, state.activeSessionId, onEvent, onOpenWorkout)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(
                label = stringResource(R.string.review_open),
                onClick = onOpenWeeklyReview,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                label = stringResource(R.string.session_list_open),
                onClick = onOpenSessions,
                modifier = Modifier.weight(1f),
            )
        }
        WeekChips(state.heatmap)
        StreakSection(state.currentStreak, state.bestStreak)
        TodaySection(state.today, onEvent, onOpenWorkout)
        RecordsSection(state.records)
        SkillsSection(state.skills)
        HeatmapSection(state.heatmap)
        BodySection(state, onEvent)
        VolumeSection(state.volume)
        CardioWeekSection(state.cardioWeek)
        StallSection(state.stalls)
        DeloadSection(state.deload)
    }
}

@Composable
private fun Notice(
    notice: DashboardNotice?,
    activeSessionId: Long?,
    onEvent: (DashboardEvent) -> Unit,
    onOpenWorkout: (Long) -> Unit,
) {
    if (notice == null) return
    val text = when (notice) {
        DashboardNotice.WORKOUT_STARTED -> R.string.dashboard_notice_started
        DashboardNotice.ALREADY_IN_PROGRESS -> R.string.dashboard_notice_in_progress
        DashboardNotice.WEIGH_IN_SAVED -> R.string.dashboard_notice_weigh_in
        DashboardNotice.WEIGH_IN_INVALID -> R.string.dashboard_notice_invalid
        DashboardNotice.SCHEDULE_REJECTED -> R.string.dashboard_notice_rejected
    }
    SurfaceCard(color = Sand) {
        Text(stringResource(text), color = Rose)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (notice == DashboardNotice.WORKOUT_STARTED || notice == DashboardNotice.ALREADY_IN_PROGRESS) {
                PrimaryButton(
                    label = stringResource(R.string.workout_resume),
                    onClick = { onOpenWorkout(activeSessionId ?: 0L) },
                    modifier = Modifier.weight(1f),
                )
            }
            SecondaryButton(
                label = stringResource(R.string.dashboard_dismiss),
                onClick = { onEvent(DashboardEvent.DismissNotice) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeekChips(cells: List<HeatmapCell>) {
    val today = java.time.LocalDate.now()
    val start = today.with(DayOfWeek.MONDAY)
    val byDate = cells.associateBy { it.date }
    val week = (0..6).map { offset ->
        val date = start.plusDays(offset.toLong())
        byDate[date] ?: HeatmapCell(date, null)
    }
    SurfaceCard {
        Text(stringResource(R.string.dashboard_week), color = Muted, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            week.forEach { cell ->
                val trained = cell.kind == SessionKind.GYM || cell.kind == SessionKind.CARDIO
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(if (trained) Rose else Sand, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = cell.date.dayOfMonth.toString(),
                            color = if (trained) White else Ink,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Text(
                        text = cell.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                        color = Muted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TodaySection(
    today: TodayCard?,
    onEvent: (DashboardEvent) -> Unit,
    onOpenWorkout: (Long) -> Unit,
) {
    SurfaceCard(color = Sand) {
        Text(stringResource(R.string.dashboard_today), color = Muted, style = MaterialTheme.typography.labelLarge)
        if (today == null) {
            Text(stringResource(R.string.dashboard_empty_program), color = Ink)
            return@SurfaceCard
        }
        Text(today.label, color = Ink, style = MaterialTheme.typography.headlineSmall)
        Text(
            if (today.mode == ScheduleMode.FIXED) {
                stringResource(R.string.dashboard_mode_fixed)
            } else {
                stringResource(R.string.dashboard_mode_rolling)
            },
            color = Muted,
        )
        if (today.isRest) {
            Text(stringResource(R.string.dashboard_rest_day), color = Rose)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Text(stringResource(R.string.dashboard_estimate), color = Ink, modifier = Modifier.weight(1f))
            NumericText(formatDuration(today.estimatedSeconds), color = Rose)
        }
        PrimaryButton(
            label = if (today.startEnabled) stringResource(R.string.dashboard_start) else stringResource(R.string.dashboard_resume),
            onClick = {
                if (today.startEnabled) {
                    onEvent(DashboardEvent.Start(shortOnTime = false))
                } else {
                    onOpenWorkout(today.activeSessionId ?: 0L)
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                label = stringResource(R.string.dashboard_start_short),
                onClick = { onEvent(DashboardEvent.Start(shortOnTime = true)) },
                enabled = today.startEnabled,
                modifier = Modifier.weight(1f),
            )
            NumericText(formatDuration(today.shortEstimatedSeconds), color = Rose)
        }
        Spacer(Modifier.height(8.dp))
        if (today.mode == ScheduleMode.ROLLING) {
            SecondaryButton(
                label = stringResource(R.string.dashboard_take_rest),
                onClick = { onEvent(DashboardEvent.TakeRestNow) },
                enabled = today.takeRestEnabled,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                label = stringResource(R.string.dashboard_skip),
                onClick = { onEvent(DashboardEvent.SkipToNext) },
                enabled = today.skipEnabled,
            )
        } else {
            SecondaryButton(
                label = stringResource(R.string.dashboard_swap),
                onClick = { onEvent(DashboardEvent.SwapWithTomorrow) },
                enabled = today.swapEnabled,
            )
        }
    }
}

@Composable
private fun StreakSection(current: Int, best: Int) {
    Column {
        Text(stringResource(R.string.dashboard_streak), color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SurfaceCard(modifier = Modifier.weight(1f), color = White) {
                Text(stringResource(R.string.dashboard_streak_current), color = Muted, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val progress = if (best <= 0) 0f else (current.toFloat() / best.toFloat()).coerceIn(0f, 1f)
                    ProgressRing(progress = progress, size = 88.dp, stroke = 8.dp) {
                        NumericText(current.toString(), color = Ink, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            SurfaceCard(modifier = Modifier.weight(1f), color = White) {
                Text(stringResource(R.string.dashboard_streak_best), color = Muted, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ProgressRing(progress = if (best > 0) 1f else 0f, size = 88.dp, stroke = 8.dp) {
                        NumericText(best.toString(), color = Ink, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapSection(cells: List<HeatmapCell>) {
    SurfaceCard {
        Text(stringResource(R.string.dashboard_heatmap), color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (cells.none { it.kind != null }) {
            Text(stringResource(R.string.dashboard_heatmap_empty), color = Muted)
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
                                .background(kindColor(cell.kind), RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Legend(SessionKind.GYM, R.string.dashboard_kind_gym)
        Legend(SessionKind.CARDIO, R.string.dashboard_kind_cardio)
        Legend(SessionKind.ACTIVE_RECOVERY, R.string.dashboard_kind_recovery)
        Legend(SessionKind.REST, R.string.dashboard_kind_rest)
    }
}

@Composable
private fun Legend(kind: SessionKind, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(12.dp).background(kindColor(kind), RoundedCornerShape(3.dp)))
        Text(stringResource(label), color = Ink)
    }
}

@Composable
private fun RecordsSection(records: List<ExerciseRecordUi>) {
    Column {
        Text(stringResource(R.string.dashboard_records), color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (records.isEmpty()) {
            SurfaceCard { Text(stringResource(R.string.dashboard_records_empty), color = Muted) }
            return
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            records.forEach { record ->
                SurfaceCard(modifier = Modifier.width(200.dp), color = Sand) {
                    Text(record.name, color = Ink, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.dashboard_e1rm), color = Muted, style = MaterialTheme.typography.labelMedium)
                    NumericText(
                        record.bestE1rmKg?.let(::formatKg) ?: stringResource(R.string.dashboard_none),
                        color = Rose,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(weightReps(record), color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SkillsSection(skills: List<SkillLadderUi>) {
    Column {
        Text(stringResource(R.string.dashboard_skills), color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (skills.isEmpty()) {
            SurfaceCard { Text(stringResource(R.string.dashboard_skills_empty), color = Muted) }
            return
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            skills.forEach { skill ->
                SurfaceCard(modifier = Modifier.width(220.dp), color = Sand) {
                    Text(skill.name, color = Ink, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.dashboard_stage) + " " + skill.stage,
                        color = Rose,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        skill.maxHoldSec?.let { "${it}s" } ?: stringResource(R.string.dashboard_none),
                        color = Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun BodySection(state: DashboardUiState, onEvent: (DashboardEvent) -> Unit) {
    SurfaceCard {
        Text(stringResource(R.string.dashboard_body), color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.dashboard_weight), color = Muted)
        if (state.weight.samples.isEmpty()) {
            Text(stringResource(R.string.dashboard_weight_empty), color = Ink)
        } else {
            CompositionChart(state.weight.samples, state.weight.average)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.dashboard_body_fat), color = Muted)
        if (state.bodyFat.samples.isEmpty()) {
            Text(stringResource(R.string.dashboard_body_fat_empty), color = Ink)
        } else {
            CompositionChart(state.bodyFat.samples, state.bodyFat.average)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.dashboard_weigh_in), color = Ink, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = state.weightDraft,
            onValueChange = { onEvent(DashboardEvent.WeightDraft(it)) },
            label = { Text(stringResource(R.string.dashboard_weight_kg)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.bodyFatDraft,
            onValueChange = { onEvent(DashboardEvent.BodyFatDraft(it)) },
            label = { Text(stringResource(R.string.dashboard_body_fat_percent)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            label = stringResource(R.string.dashboard_save_weigh_in),
            onClick = { onEvent(DashboardEvent.SaveWeighIn) },
        )
    }
}

@Composable
private fun VolumeSection(volume: List<MuscleVolumeUi>) {
    SurfaceCard {
        Text(stringResource(R.string.dashboard_volume), color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (volume.isEmpty()) {
            Text(stringResource(R.string.dashboard_volume_empty), color = Muted)
            return@SurfaceCard
        }
        volume.forEach { muscle ->
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(muscle.muscle, color = Ink, modifier = Modifier.weight(1f))
                NumericText(formatSets(muscle.sets), color = Rose)
            }
        }
    }
}

@Composable
private fun CardioWeekSection(cardio: WeeklyCardioUi) {
    SurfaceCard {
        Text(stringResource(R.string.dashboard_cardio_week), color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (cardio.sessions == 0) {
            Text(stringResource(R.string.dashboard_cardio_empty), color = Muted)
            return@SurfaceCard
        }
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(stringResource(R.string.dashboard_cardio_sessions), color = Ink, modifier = Modifier.weight(1f))
            NumericText(cardio.sessions.toString(), color = CardioBlue)
        }
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(stringResource(R.string.dashboard_cardio_duration), color = Ink, modifier = Modifier.weight(1f))
            NumericText(formatDuration(cardio.durationSec), color = CardioBlue)
        }
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(stringResource(R.string.dashboard_cardio_distance), color = Ink, modifier = Modifier.weight(1f))
            NumericText(formatKm(cardio.distanceM), color = CardioBlue)
        }
        cardio.byActivity.forEach { row ->
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(activityLabel(row.activity), color = Ink, modifier = Modifier.weight(1f))
                NumericText(
                    breakdownLine(row.durationSec, row.distanceM),
                    color = CardioBlue,
                )
            }
        }
    }
}

@Composable
private fun breakdownLine(durationSec: Int, distanceM: Double): String {
    val duration = formatDuration(durationSec)
    return if (distanceM > 0.0) {
        "$duration · ${formatKm(distanceM)} km"
    } else {
        duration
    }
}

private fun formatKm(meters: Double): String =
    String.format(Locale.US, "%.2f", meters / 1_000.0)

@Composable
private fun StallSection(stalls: List<String>) {
    SurfaceCard {
        Text(stringResource(R.string.dashboard_stalls), color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (stalls.isEmpty()) {
            Text(stringResource(R.string.dashboard_stalls_empty), color = Muted)
            return@SurfaceCard
        }
        stalls.forEach { name ->
            Text(name, color = Rose)
        }
    }
}

@Composable
private fun DeloadSection(deload: DeloadStatus?) {
    SurfaceCard {
        Text(stringResource(R.string.dashboard_deload), color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (deload == null) {
            Text(stringResource(R.string.dashboard_deload_empty), color = Muted)
            return@SurfaceCard
        }
        Text(
            if (deload.rotationComplete) {
                stringResource(R.string.dashboard_deload_done)
            } else {
                stringResource(R.string.dashboard_deload_active)
            },
            color = Rose,
        )
        deload.startedOn?.let { started ->
            Row {
                Text(stringResource(R.string.dashboard_started), color = Ink, modifier = Modifier.weight(1f))
                NumericText(started.toString(), color = Rose)
            }
        }
    }
}

@Composable
private fun weightReps(record: ExerciseRecordUi): String {
    val weight = record.bestWeightKg ?: return stringResource(R.string.dashboard_none)
    val reps = record.bestReps ?: return stringResource(R.string.dashboard_none)
    return stringResource(R.string.dashboard_weight_reps_value, formatKg(weight), reps)
}

private fun kindColor(kind: SessionKind?): Color = when (kind) {
    SessionKind.GYM -> Rose
    SessionKind.CARDIO -> CardioBlue
    SessionKind.ACTIVE_RECOVERY -> RecoveryAmber
    SessionKind.REST -> RestGray
    null -> Sand
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
