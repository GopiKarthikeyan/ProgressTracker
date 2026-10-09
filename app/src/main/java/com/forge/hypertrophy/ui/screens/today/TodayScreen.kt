package com.forge.hypertrophy.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.components.PrimaryButton
import com.forge.hypertrophy.ui.components.SecondaryButton
import com.forge.hypertrophy.ui.components.SurfaceCard
import com.forge.hypertrophy.ui.theme.Cream
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.Muted
import com.forge.hypertrophy.ui.theme.Rose
import com.forge.hypertrophy.ui.theme.Sand

@Composable
fun TodayScreen(
    onOpenCardio: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(TodayEvent.Refresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.sessionToOpen) {
        val sessionId = state.sessionToOpen ?: return@LaunchedEffect
        onOpenWorkout(sessionId)
        viewModel.onEvent(TodayEvent.OpenedSession)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Cream)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.nav_today),
                color = Ink,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = state.dayLabel ?: stringResource(R.string.today_no_program),
                color = Muted,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton(
                label = stringResource(R.string.cardio_title),
                onClick = onOpenCardio,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                label = stringResource(R.string.media_gallery),
                onClick = onOpenGallery,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.weight(1f))

        SurfaceCard(color = Sand) {
            Text(
                text = stringResource(R.string.today_workout_plan),
                color = Muted,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = state.dayLabel ?: stringResource(R.string.today_no_program),
                color = Ink,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            when {
                state.isInProgress -> PrimaryButton(
                    label = stringResource(R.string.workout_resume),
                    onClick = { onOpenWorkout(state.activeSessionId ?: 0L) },
                )
                state.scheduledDayId != null && !state.isRestDay -> PrimaryButton(
                    label = stringResource(
                        if (state.completedToday) R.string.workout_restart else R.string.workout_start,
                    ),
                    onClick = { viewModel.onEvent(TodayEvent.StartWorkout) },
                )
                state.isRestDay -> Text(
                    text = stringResource(R.string.today_rest_day),
                    color = Rose,
                    style = MaterialTheme.typography.titleMedium,
                )
                else -> HeightPlaceholder()
            }
        }
    }
}

@Composable
private fun HeightPlaceholder() {
    Spacer(Modifier.height(0.dp))
}
