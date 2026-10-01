package com.forge.hypertrophy.ui.screens.snapshots

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.screens.routine.BuilderColumn
import com.forge.hypertrophy.ui.screens.routine.EditorButton
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.White

@Composable
fun SnapshotsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SnapshotsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SnapshotsContent(state, viewModel::onEvent, onBack, modifier)
}

@Composable
fun SnapshotsContent(
    state: SnapshotsUiState,
    onEvent: (SnapshotsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuilderColumn(title = stringResource(R.string.snapshots_title), onBack = onBack, modifier = modifier) {
        Text(stringResource(R.string.snapshots_hint), color = White)
        if (state.snapshots.isEmpty()) Text(stringResource(R.string.snapshots_empty), color = White)
        state.snapshots.forEach { snapshot ->
            EditorButton(
                label = stringResource(R.string.snapshots_restore, snapshot.schemaVersion, snapshot.label),
                onClick = { onEvent(SnapshotsEvent.Restore(snapshot.name)) },
            )
        }
        state.notice?.let { Text(it, color = NeonAccent) }
    }
}
