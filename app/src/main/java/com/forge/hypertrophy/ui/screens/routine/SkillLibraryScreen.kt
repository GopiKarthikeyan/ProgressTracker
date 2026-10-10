package com.forge.hypertrophy.ui.screens.routine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R

@Composable
fun SkillLibraryScreen(
    viewModel: SkillLibraryViewModel,
    onBack: () -> Unit,
    onOpenSkill: (Long) -> Unit,
    onAddSkill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryColumn(
        title = stringResource(R.string.builder_skills),
        onBack = onBack,
        rows = state.rows.map { row ->
            LibraryListRow(
                id = row.id,
                name = row.name,
                canHardDelete = row.canHardDelete,
                progress = row.progress,
            )
        },
        error = state.error,
        restrictedMessage = stringResource(R.string.builder_restricted_skill),
        builtinMessage = state.builtinMessage,
        onOpen = onOpenSkill,
        onAdd = onAddSkill,
        onAddBuiltIn = { viewModel.onEvent(SkillLibraryEvent.AddBuiltIn) },
        onArchive = { viewModel.onEvent(SkillLibraryEvent.Archive(it)) },
        onHardDelete = { viewModel.onEvent(SkillLibraryEvent.HardDelete(it)) },
        onDismissError = { viewModel.onEvent(SkillLibraryEvent.DismissError) },
        onDismissBuiltin = { viewModel.onEvent(SkillLibraryEvent.DismissBuiltinMessage) },
        modifier = modifier,
    )
}
