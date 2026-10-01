package com.forge.hypertrophy.ui.screens.routine

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R

@Composable
fun SkillEditorScreen(
    viewModel: SkillEditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BuilderColumn(title = stringResource(R.string.builder_skills), onBack = onBack, modifier = modifier) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { viewModel.onEvent(SkillEditorEvent.Name(it)) },
            label = { Text(stringResource(R.string.builder_skill)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        EditorButton(
            label = stringResource(R.string.builder_save),
            onClick = { viewModel.onEvent(SkillEditorEvent.SaveName) },
        )
        EditorButton(
            label = stringResource(R.string.builder_add),
            onClick = { viewModel.onEvent(SkillEditorEvent.AddStep) },
        )
        state.steps.forEach { step ->
            OutlinedTextField(
                value = step.name,
                onValueChange = { viewModel.onEvent(SkillEditorEvent.StepName(step.id, it)) },
                label = { Text(stringResource(R.string.builder_step_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            NumericEntry(stringResource(R.string.builder_stage1), step.stage1TotalSec) {
                viewModel.onEvent(SkillEditorEvent.Stage1(step.id, it ?: 0))
            }
            NumericEntry(stringResource(R.string.builder_stage2_low), step.stage2TotalLowSec) {
                viewModel.onEvent(SkillEditorEvent.Stage2Low(step.id, it ?: 0))
            }
            NumericEntry(stringResource(R.string.builder_stage2_high), step.stage2TotalHighSec) {
                viewModel.onEvent(SkillEditorEvent.Stage2High(step.id, it ?: 0))
            }
            NumericEntry(stringResource(R.string.builder_stage3), step.stage3UnbrokenSec) {
                viewModel.onEvent(SkillEditorEvent.Stage3(step.id, it ?: 0))
            }
            EditorButton(
                label = stringResource(R.string.builder_delete),
                onClick = { viewModel.onEvent(SkillEditorEvent.DeleteStep(step.id)) },
            )
        }
    }
}
