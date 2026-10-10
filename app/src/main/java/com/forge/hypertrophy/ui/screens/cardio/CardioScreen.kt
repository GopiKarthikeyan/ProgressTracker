package com.forge.hypertrophy.ui.screens.cardio

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.cardio.CardioDistanceUnit
import com.forge.hypertrophy.domain.model.CardioActivity
import com.forge.hypertrophy.domain.model.CardioSource
import com.forge.hypertrophy.domain.model.CardioStyle
import com.forge.hypertrophy.ui.components.BackButton
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.theme.Black
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.NeonAccent
import java.util.Locale

private val RUNNING_STYLES = listOf(
    CardioStyle.JOG,
    CardioStyle.WALK,
    CardioStyle.INTERVALS,
    CardioStyle.SPRINT,
    CardioStyle.LONG_RUN,
)

@Composable
fun CardioScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CardioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onEvent(CardioEvent.StartGps)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BackButton(onClick = onBack, size = TouchTargets.Workout)
            Text(
                stringResource(R.string.cardio_title),
                color = Ink,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Notice(state.notice, viewModel::onEvent)
        state.openSessionId?.let {
            Text(stringResource(R.string.cardio_open_session), color = NeonAccent)
            WideButton(stringResource(R.string.cardio_finish_open)) { viewModel.onEvent(CardioEvent.FinishOpen) }
        }
        if (state.tracking) {
            TrackingSection(state, viewModel::onEvent)
        } else {
            LogForm(
                state = state,
                onEvent = viewModel::onEvent,
                onStartGps = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.onEvent(CardioEvent.StartGps)
                    } else {
                        permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
            )
        }
        LogsSection(state, viewModel::onEvent)
        if (state.fieldSpec.showsGear) {
            GearSection(state, viewModel::onEvent)
        }
    }
}

@Composable
private fun TrackingSection(state: CardioUiState, onEvent: (CardioEvent) -> Unit) {
    Section(stringResource(R.string.cardio_gps)) {
        if (state.paused) Text(stringResource(R.string.cardio_paused), color = NeonAccent)
        Row {
            Text(stringResource(R.string.cardio_distance), color = Ink, modifier = Modifier.weight(1f))
            NumericText(formatKm(state.liveDistanceM), color = NeonAccent)
        }
        Row {
            Text(stringResource(R.string.cardio_duration), color = Ink, modifier = Modifier.weight(1f))
            NumericText(formatClock(state.liveMovingSec), color = NeonAccent)
        }
        if (state.livePoints.size < 2) {
            Text(stringResource(R.string.cardio_route_empty), color = Ink)
        }
        RouteCanvas(state.livePoints)
        WideButton(stringResource(R.string.cardio_stop)) { onEvent(CardioEvent.StopGps) }
    }
}

@Composable
private fun LogForm(
    state: CardioUiState,
    onEvent: (CardioEvent) -> Unit,
    onStartGps: () -> Unit,
) {
    val spec = state.fieldSpec
    Section(if (state.editingId == null) stringResource(R.string.cardio_manual) else stringResource(R.string.cardio_edit)) {
        Text(stringResource(R.string.cardio_activity), color = Ink)
        CardioActivity.entries.forEach { activity ->
            WideButton(activityLabel(activity), enabled = state.activity != activity) {
                onEvent(CardioEvent.ActivityChosen(activity))
            }
        }
        if (spec.showsStyle) {
            Text(stringResource(R.string.cardio_style), color = Ink)
            RUNNING_STYLES.forEach { style ->
                WideButton(styleLabel(style), enabled = state.style != style) {
                    onEvent(CardioEvent.StyleChosen(style))
                }
            }
        }
        if (spec.showsCustomName) {
            Field(stringResource(R.string.cardio_custom_name), state.customName) {
                onEvent(CardioEvent.CustomName(it))
            }
        }
        if (spec.showsDistance) {
            val label = when (spec.distanceUnit) {
                CardioDistanceUnit.KM -> stringResource(R.string.cardio_distance_km)
                CardioDistanceUnit.M -> stringResource(R.string.cardio_distance_m)
            }
            Field(label, state.distanceText) { onEvent(CardioEvent.Distance(it)) }
        }
        Field(stringResource(R.string.cardio_minutes), state.minutes) { onEvent(CardioEvent.Minutes(it)) }
        Field(stringResource(R.string.cardio_seconds), state.seconds) { onEvent(CardioEvent.Seconds(it)) }
        if (spec.showsElevation) {
            Field(stringResource(R.string.cardio_elevation_m), state.elevationText) {
                onEvent(CardioEvent.Elevation(it))
            }
        }
        if (spec.showsCount) {
            val countLabel = when {
                spec.countIsLaps -> stringResource(R.string.cardio_laps)
                spec.countIsJumps -> stringResource(R.string.cardio_jumps)
                spec.countIsFloors -> stringResource(R.string.cardio_floors)
                else -> stringResource(R.string.cardio_count)
            }
            Field(countLabel, state.countText) { onEvent(CardioEvent.Count(it)) }
        }
        if (spec.showsGear) {
            Text(
                if (spec.gearIsShoe) stringResource(R.string.cardio_shoe) else stringResource(R.string.cardio_gear_item),
                color = Ink,
            )
            WideButton(stringResource(R.string.cardio_shoe_none), enabled = state.gearId != null) {
                onEvent(CardioEvent.GearChosen(null))
            }
            state.gear.forEach { item ->
                WideButton(item.name, enabled = state.gearId != item.id) {
                    onEvent(CardioEvent.GearChosen(item.id))
                }
            }
        }
        if (state.editingId == null) {
            WideButton(stringResource(R.string.cardio_save_manual)) { onEvent(CardioEvent.SaveManual) }
            if (spec.allowsGps) {
                WideButton(stringResource(R.string.cardio_start_gps), onClick = onStartGps)
            }
        } else {
            WideButton(stringResource(R.string.cardio_save_edit)) { onEvent(CardioEvent.SaveEdit) }
        }
    }
}

@Composable
private fun LogsSection(state: CardioUiState, onEvent: (CardioEvent) -> Unit) {
    Section(stringResource(R.string.cardio_logs)) {
        if (state.logs.isEmpty()) {
            Text(stringResource(R.string.cardio_logs_empty), color = Ink)
            return@Section
        }
        state.logs.forEach { log ->
            Text(logTitle(log), color = Ink)
            Row {
                Text(
                    if (log.source == CardioSource.GPS) {
                        stringResource(R.string.cardio_source_gps)
                    } else {
                        stringResource(R.string.cardio_source_manual)
                    },
                    color = Ink,
                    modifier = Modifier.weight(1f),
                )
                NumericText(formatClock(log.durationSec), color = NeonAccent)
            }
            if (log.distanceM > 0.0) {
                NumericText(formatKm(log.distanceM), color = NeonAccent)
            }
            log.count?.let { NumericText(it.toString(), color = NeonAccent) }
            log.gearName?.let { Text(it, color = Ink) }
            WideButton(stringResource(R.string.cardio_edit)) { onEvent(CardioEvent.Edit(log.id)) }
        }
    }
}

@Composable
private fun GearSection(state: CardioUiState, onEvent: (CardioEvent) -> Unit) {
    val shoe = state.fieldSpec.gearIsShoe
    Section(
        if (shoe) stringResource(R.string.cardio_gear) else stringResource(R.string.cardio_gear_generic),
    ) {
        if (state.gear.isEmpty()) {
            Text(
                if (shoe) stringResource(R.string.cardio_gear_empty) else stringResource(R.string.cardio_gear_generic_empty),
                color = Ink,
            )
        }
        state.gear.forEach { item ->
            Text(item.name, color = Ink)
            Row {
                Text(stringResource(R.string.cardio_used), color = Ink, modifier = Modifier.weight(1f))
                NumericText(formatKm(item.usedM), color = NeonAccent)
            }
            Row {
                Text(stringResource(R.string.cardio_limit), color = Ink, modifier = Modifier.weight(1f))
                NumericText(formatKm(item.limitM.toDouble()), color = NeonAccent)
            }
            if (item.retired) Text(stringResource(R.string.cardio_retire), color = NeonAccent)
        }
        Field(
            if (shoe) stringResource(R.string.cardio_shoe_name) else stringResource(R.string.cardio_gear_name),
            state.shoeName,
        ) { onEvent(CardioEvent.ShoeName(it)) }
        Field(stringResource(R.string.cardio_limit_km), state.shoeLimitKm) { onEvent(CardioEvent.ShoeLimit(it)) }
        WideButton(
            if (shoe) stringResource(R.string.cardio_add_shoe) else stringResource(R.string.cardio_add_gear),
        ) { onEvent(CardioEvent.AddShoe) }
    }
}

@Composable
private fun Notice(notice: CardioNotice?, onEvent: (CardioEvent) -> Unit) {
    if (notice == null) return
    val text = when (notice) {
        CardioNotice.SAVED -> R.string.cardio_saved
        CardioNotice.INVALID -> R.string.cardio_invalid
        CardioNotice.TRACKING -> R.string.cardio_already_tracking
        CardioNotice.GPS_UNSUPPORTED -> R.string.cardio_gps_unsupported
    }
    Text(stringResource(text), color = NeonAccent)
    WideButton(stringResource(R.string.dashboard_dismiss)) { onEvent(CardioEvent.DismissNotice) }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, color = NeonAccent, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontFeatureSettings = "tnum",
        ),
    )
}

@Composable
private fun WideButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTargets.Workout),
    ) {
        Text(label)
    }
}

@Composable
private fun logTitle(log: CardioLogRow): String {
    val activity = activityLabel(log.activity)
    return when {
        log.activity == CardioActivity.CUSTOM && log.customName.isNotBlank() -> log.customName
        log.activity == CardioActivity.RUNNING && log.style != CardioStyle.NONE ->
            "$activity · ${styleLabel(log.style)}"
        else -> activity
    }
}

@Composable
internal fun activityLabel(activity: CardioActivity): String = when (activity) {
    CardioActivity.RUNNING -> stringResource(R.string.cardio_activity_running)
    CardioActivity.CYCLING -> stringResource(R.string.cardio_activity_cycling)
    CardioActivity.SWIMMING -> stringResource(R.string.cardio_activity_swimming)
    CardioActivity.ROWING -> stringResource(R.string.cardio_activity_rowing)
    CardioActivity.ELLIPTICAL -> stringResource(R.string.cardio_activity_elliptical)
    CardioActivity.JUMP_ROPE -> stringResource(R.string.cardio_activity_jump_rope)
    CardioActivity.HIKING -> stringResource(R.string.cardio_activity_hiking)
    CardioActivity.STAIR_CLIMBER -> stringResource(R.string.cardio_activity_stair_climber)
    CardioActivity.SKI_ERG -> stringResource(R.string.cardio_activity_ski_erg)
    CardioActivity.CUSTOM -> stringResource(R.string.cardio_activity_custom)
}

@Composable
internal fun styleLabel(style: CardioStyle): String = when (style) {
    CardioStyle.JOG -> stringResource(R.string.cardio_type_jog)
    CardioStyle.WALK -> stringResource(R.string.cardio_type_walk)
    CardioStyle.INTERVALS -> stringResource(R.string.cardio_type_intervals)
    CardioStyle.SPRINT -> stringResource(R.string.cardio_type_sprint)
    CardioStyle.LONG_RUN -> stringResource(R.string.cardio_type_long_run)
    CardioStyle.NONE -> stringResource(R.string.builder_none)
}

private fun formatKm(meters: Double): String = String.format(Locale.US, "%.2f", meters / 1_000.0)

private fun formatClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%d:%02d".format(Locale.US, safe / 60, safe % 60)
}
