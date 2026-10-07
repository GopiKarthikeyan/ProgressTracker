package com.forge.hypertrophy.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.screens.routine.EditorButton
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.White

@Composable
fun TodayScreen(
    onOpenCardio: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.nav_today),
                color = White,
                style = MaterialTheme.typography.headlineMedium,
            )
            state.dayLabel?.let {
                Text(
                    text = it,
                    color = NeonAccent,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (state.isInProgress) {
            EditorButton(
                label = stringResource(R.string.workout_resume),
                onClick = { onOpenWorkout(state.activeSessionId ?: 0L) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onOpenCardio,
                modifier = Modifier.heightIn(min = TouchTargets.Workout),
            ) {
                Text(stringResource(R.string.cardio_title))
            }
            TextButton(
                onClick = onOpenGallery,
                modifier = Modifier.heightIn(min = TouchTargets.Workout),
            ) {
                Text(stringResource(R.string.media_gallery))
            }
        }
    }
}
