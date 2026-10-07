package com.forge.hypertrophy.ui.screens.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.components.ReorderableColumn

@Composable
fun ProgramEditorScreen(
    viewModel: ProgramEditorViewModel,
    onBack: () -> Unit,
    onOpenDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BuilderColumn(title = stringResource(R.string.builder_days), onBack = onBack, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.onEvent(ProgramEditorEvent.Name(it)) },
                label = { Text(stringResource(R.string.builder_program_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            EditorButton(
                label = stringResource(R.string.builder_save),
                onClick = { viewModel.onEvent(ProgramEditorEvent.SaveName) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        EditorButton(
            label = stringResource(R.string.builder_add),
            onClick = { viewModel.onEvent(ProgramEditorEvent.AddDay) },
            modifier = Modifier.fillMaxWidth()
        )

        ReorderableColumn(
            items = state.days,
            itemKey = { it.id },
            onMove = { from, to -> viewModel.onEvent(ProgramEditorEvent.MoveDay(from, to)) },
        ) { day ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorButton(
                    label = day.label.ifBlank { stringResource(R.string.builder_days) },
                    onClick = { onOpenDay(day.id) },
                    modifier = Modifier.weight(1f)
                )
                EditorButton(
                    label = stringResource(R.string.builder_delete),
                    onClick = { viewModel.onEvent(ProgramEditorEvent.DeleteDay(day.id)) },
                )
            }
        }
    }
}
