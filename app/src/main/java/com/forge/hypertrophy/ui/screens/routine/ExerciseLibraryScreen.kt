package com.forge.hypertrophy.ui.screens.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R

@Composable
fun ExerciseLibraryScreen(
    viewModel: ExerciseLibraryViewModel,
    onBack: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryColumn(
        title = stringResource(R.string.builder_exercises),
        onBack = onBack,
        rows = state.rows,
        error = state.error,
        restrictedMessage = stringResource(R.string.builder_restricted_exercise),
        onOpen = onOpenExercise,
        onAdd = onAddExercise,
        onArchive = { viewModel.onEvent(ExerciseLibraryEvent.Archive(it)) },
        onHardDelete = { viewModel.onEvent(ExerciseLibraryEvent.HardDelete(it)) },
        onDismissError = { viewModel.onEvent(ExerciseLibraryEvent.DismissError) },
        modifier = modifier,
    )
}

@Composable
internal fun LibraryColumn(
    title: String,
    onBack: () -> Unit,
    rows: List<LibraryRow>,
    error: LibraryError?,
    restrictedMessage: String,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onArchive: (Long) -> Unit,
    onHardDelete: (Long) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuilderColumn(title = title, onBack = onBack, modifier = modifier) {
        EditorButton(
            label = stringResource(R.string.builder_add),
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        )
        if (error == LibraryError.RESTRICTED) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(restrictedMessage)
                EditorButton(label = stringResource(R.string.builder_dismiss), onClick = onDismissError)
            }
        }
        rows.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorButton(
                    label = row.name,
                    onClick = { onOpen(row.id) },
                    modifier = Modifier.weight(1f)
                )
                EditorButton(label = stringResource(R.string.builder_archive), onClick = { onArchive(row.id) })
                if (row.canHardDelete) {
                    EditorButton(label = stringResource(R.string.builder_delete), onClick = { onHardDelete(row.id) })
                }
            }
        }
    }
}
