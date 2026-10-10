package com.forge.hypertrophy.ui.screens.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.skill.SkillHoldTargetKind
import com.forge.hypertrophy.domain.skill.SkillProgressSummary
import com.forge.hypertrophy.ui.theme.Muted

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
        rows = state.rows.map {
            LibraryListRow(id = it.id, name = it.name, canHardDelete = it.canHardDelete)
        },
        error = state.error,
        restrictedMessage = stringResource(R.string.builder_restricted_exercise),
        builtinMessage = state.builtinMessage,
        onOpen = onOpenExercise,
        onAdd = onAddExercise,
        onAddBuiltIn = { viewModel.onEvent(ExerciseLibraryEvent.AddBuiltIn) },
        onArchive = { viewModel.onEvent(ExerciseLibraryEvent.Archive(it)) },
        onHardDelete = { viewModel.onEvent(ExerciseLibraryEvent.HardDelete(it)) },
        onDismissError = { viewModel.onEvent(ExerciseLibraryEvent.DismissError) },
        onDismissBuiltin = { viewModel.onEvent(ExerciseLibraryEvent.DismissBuiltinMessage) },
        modifier = modifier,
    )
}

internal data class LibraryListRow(
    val id: Long,
    val name: String,
    val canHardDelete: Boolean,
    val progress: SkillProgressSummary? = null,
)

@Composable
internal fun LibraryColumn(
    title: String,
    onBack: () -> Unit,
    rows: List<LibraryListRow>,
    error: LibraryError?,
    restrictedMessage: String,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onArchive: (Long) -> Unit,
    onHardDelete: (Long) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    builtinMessage: BuiltinCatalogMessage? = null,
    onAddBuiltIn: (() -> Unit)? = null,
    onDismissBuiltin: (() -> Unit)? = null,
) {
    BuilderColumn(title = title, onBack = onBack, modifier = modifier) {
        EditorButton(
            label = stringResource(R.string.builder_add),
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onAddBuiltIn != null) {
            EditorButton(
                label = stringResource(R.string.builder_add_builtin),
                onClick = onAddBuiltIn,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error == LibraryError.RESTRICTED) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(restrictedMessage)
                EditorButton(label = stringResource(R.string.builder_dismiss), onClick = onDismissError)
            }
        }
        if (builtinMessage != null && onDismissBuiltin != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (builtinMessage.addedExercises == 0 && builtinMessage.addedSkills == 0) {
                        stringResource(R.string.builder_builtin_already_added)
                    } else {
                        stringResource(
                            R.string.builder_builtin_added,
                            builtinMessage.addedExercises,
                            builtinMessage.addedSkills,
                        )
                    },
                )
                EditorButton(label = stringResource(R.string.builder_dismiss), onClick = onDismissBuiltin)
            }
        }
        rows.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    EditorButton(
                        label = row.name,
                        onClick = { onOpen(row.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    row.progress?.let { progress ->
                        Text(
                            text = skillProgressLine(progress),
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                EditorButton(label = stringResource(R.string.builder_archive), onClick = { onArchive(row.id) })
                if (row.canHardDelete) {
                    EditorButton(label = stringResource(R.string.builder_delete), onClick = { onHardDelete(row.id) })
                }
            }
        }
    }
}

@Composable
internal fun skillProgressLine(summary: SkillProgressSummary): String {
    val step = summary.stepName.ifBlank { stringResource(R.string.builder_none) }
    return when (summary.targetKind) {
        SkillHoldTargetKind.UNBROKEN -> stringResource(
            R.string.builder_skill_progress_unbroken,
            step,
            summary.stage,
            summary.targetSec,
        )
        SkillHoldTargetKind.TOTAL -> stringResource(
            R.string.builder_skill_progress_total,
            step,
            summary.stage,
            summary.targetSec,
        )
    }
}
