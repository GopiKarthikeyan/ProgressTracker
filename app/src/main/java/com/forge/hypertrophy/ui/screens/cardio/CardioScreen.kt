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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.model.CardioSource
import com.forge.hypertrophy.domain.model.CardioType
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.theme.Black
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.Ink
import java.util.Locale

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
        Row {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = TouchTargets.Workout)) {
                Text(stringResource(R.string.builder_back))
            }
            Text(
                stringResource(R.string.cardio_title),
                color = Ink,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp),
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
            LogForm(state, viewModel::onEvent, gps = true, onStartGps = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.onEvent(CardioEvent.StartGps) else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            })
        }
        LogsSection(state, viewModel::onEvent)
        GearSection(state, viewModel::onEvent)
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
    gps: Boolean,
    onStartGps: () -> Unit,
) {
    Section(if (state.editingId == null) stringResource(R.string.cardio_manual) else stringResource(R.string.cardio_edit)) {
        Field(stringResource(R.string.cardio_distance_km), state.distanceKm) { onEvent(CardioEvent.Distance(it)) }
        Field(stringResource(R.string.cardio_minutes), state.minutes) { onEvent(CardioEvent.Minutes(it)) }
        Field(stringResource(R.string.cardio_seconds), state.seconds) { onEvent(CardioEvent.Seconds(it)) }
        Text(stringResource(R.string.cardio_type), color = Ink)
        CardioType.entries.forEach { type ->
            WideButton(typeLabel(type), enabled = state.type != type) { onEvent(CardioEvent.TypeChosen(type)) }
        }
        Text(stringResource(R.string.cardio_shoe), color = Ink)
        WideButton(stringResource(R.string.cardio_shoe_none), enabled = state.gearId != null) {
            onEvent(CardioEvent.GearChosen(null))
        }
        state.gear.forEach { shoe ->
            WideButton(shoe.name, enabled = state.gearId != shoe.id) { onEvent(CardioEvent.GearChosen(shoe.id)) }
        }
        if (state.editingId == null) {
            WideButton(stringResource(R.string.cardio_save_manual)) { onEvent(CardioEvent.SaveManual) }
            if (gps) WideButton(stringResource(R.string.cardio_start_gps), onClick = onStartGps)
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
            Text(typeLabel(log.type), color = Ink)
            Row {
                Text(
                    if (log.source == CardioSource.GPS) stringResource(R.string.cardio_source_gps) else stringResource(R.string.cardio_source_manual),
                    color = Ink,
                    modifier = Modifier.weight(1f),
                )
                NumericText(formatKm(log.distanceM), color = NeonAccent)
            }
            log.gearName?.let { Text(it, color = Ink) }
            WideButton(stringResource(R.string.cardio_edit)) { onEvent(CardioEvent.Edit(log.id)) }
        }
    }
}

@Composable
private fun GearSection(state: CardioUiState, onEvent: (CardioEvent) -> Unit) {
    Section(stringResource(R.string.cardio_gear)) {
        if (state.gear.isEmpty()) {
            Text(stringResource(R.string.cardio_gear_empty), color = Ink)
        }
        state.gear.forEach { shoe ->
            Text(shoe.name, color = Ink)
            Row {
                Text(stringResource(R.string.cardio_used), color = Ink, modifier = Modifier.weight(1f))
                NumericText(formatKm(shoe.usedM), color = NeonAccent)
            }
            Row {
                Text(stringResource(R.string.cardio_limit), color = Ink, modifier = Modifier.weight(1f))
                NumericText(formatKm(shoe.limitM.toDouble()), color = NeonAccent)
            }
            if (shoe.retired) Text(stringResource(R.string.cardio_retire), color = NeonAccent)
        }
        Field(stringResource(R.string.cardio_shoe_name), state.shoeName) { onEvent(CardioEvent.ShoeName(it)) }
        Field(stringResource(R.string.cardio_limit_km), state.shoeLimitKm) { onEvent(CardioEvent.ShoeLimit(it)) }
        WideButton(stringResource(R.string.cardio_add_shoe)) { onEvent(CardioEvent.AddShoe) }
    }
}

@Composable
private fun Notice(notice: CardioNotice?, onEvent: (CardioEvent) -> Unit) {
    if (notice == null) return
    val text = when (notice) {
        CardioNotice.SAVED -> R.string.cardio_saved
        CardioNotice.INVALID -> R.string.cardio_invalid
        CardioNotice.TRACKING -> R.string.cardio_already_tracking
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
private fun typeLabel(type: CardioType): String = when (type) {
    CardioType.JOG -> stringResource(R.string.cardio_type_jog)
    CardioType.WALK -> stringResource(R.string.cardio_type_walk)
    CardioType.INTERVALS -> stringResource(R.string.cardio_type_intervals)
}

private fun formatKm(meters: Double): String = String.format(Locale.US, "%.2f", meters / 1_000.0)

private fun formatClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%d:%02d".format(Locale.US, safe / 60, safe % 60)
}
