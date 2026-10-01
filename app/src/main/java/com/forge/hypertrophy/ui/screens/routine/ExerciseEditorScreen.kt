package com.forge.hypertrophy.ui.screens.routine

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.model.Equipment

@Composable
fun ExerciseEditorScreen(
    viewModel: ExerciseEditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }
    BuilderColumn(title = stringResource(R.string.builder_exercises), onBack = onBack, modifier = modifier) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { viewModel.onEvent(ExerciseEditorEvent.Name(it)) },
            label = { Text(stringResource(R.string.builder_exercise)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(stringResource(R.string.builder_equipment))
        FlowRow {
            Equipment.entries.forEach { equipment ->
                EditorButton(
                    label = equipment.name,
                    onClick = { viewModel.onEvent(ExerciseEditorEvent.EquipmentChanged(equipment)) },
                    enabled = state.equipment != equipment,
                )
            }
        }
        KgEntry(
            label = stringResource(R.string.builder_load_increment),
            value = state.loadIncrementKg,
            onValue = { value -> value?.let { viewModel.onEvent(ExerciseEditorEvent.Increment(it)) } },
        )
        KgEntry(
            label = stringResource(R.string.builder_bar_kg),
            value = state.barWeightKg,
            onValue = { viewModel.onEvent(ExerciseEditorEvent.BarWeight(it)) },
        )
        EditorButton(
            label = stringResource(R.string.builder_unilateral),
            onClick = { viewModel.onEvent(ExerciseEditorEvent.Unilateral(!state.isUnilateral)) },
        )
        Text(stringResource(R.string.builder_primary_muscles))
        MusclePicker(
            selected = state.primaryMuscles,
            known = state.knownMuscles,
            draft = state.primaryDraft,
            onDraft = { viewModel.onEvent(ExerciseEditorEvent.PrimaryDraft(it)) },
            onAdd = { viewModel.onEvent(ExerciseEditorEvent.AddPrimary(it)) },
            onRemove = { viewModel.onEvent(ExerciseEditorEvent.RemovePrimary(it)) },
        )
        Text(stringResource(R.string.builder_secondary_muscles))
        MusclePicker(
            selected = state.secondaryMuscles,
            known = state.knownMuscles,
            draft = state.secondaryDraft,
            onDraft = { viewModel.onEvent(ExerciseEditorEvent.SecondaryDraft(it)) },
            onAdd = { viewModel.onEvent(ExerciseEditorEvent.AddSecondary(it)) },
            onRemove = { viewModel.onEvent(ExerciseEditorEvent.RemoveSecondary(it)) },
        )
        OutlinedTextField(
            value = state.setupNotes,
            onValueChange = { viewModel.onEvent(ExerciseEditorEvent.SetupNotes(it)) },
            label = { Text(stringResource(R.string.builder_setup_notes)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.builder_skill))
        EditorButton(
            label = stringResource(R.string.builder_none),
            onClick = { viewModel.onEvent(ExerciseEditorEvent.Skill(null)) },
        )
        state.skills.forEach { skill ->
            EditorButton(
                label = skill.name,
                onClick = { viewModel.onEvent(ExerciseEditorEvent.Skill(skill.id)) },
                enabled = state.skillId != skill.id,
            )
        }
        EditorButton(
            label = stringResource(R.string.builder_save),
            onClick = { viewModel.onEvent(ExerciseEditorEvent.Save) },
        )
    }
}

@Composable
private fun MusclePicker(
    selected: List<String>,
    known: List<String>,
    draft: String,
    onDraft: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    selected.forEach { token ->
        EditorButton(label = token, onClick = { onRemove(token) })
    }
    known.filter { it !in selected }.forEach { token ->
        EditorButton(label = token, onClick = { onAdd(token) })
    }
    OutlinedTextField(
        value = draft,
        onValueChange = onDraft,
        label = { Text(stringResource(R.string.builder_add_muscle)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    EditorButton(label = stringResource(R.string.builder_add), onClick = { onAdd(draft) })
}
