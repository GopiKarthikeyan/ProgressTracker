package com.forge.hypertrophy.ui.screens.routine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R

private const val DOCUMENT_MIME = "application/json"
private val OpenableMimeTypes = arrayOf(DOCUMENT_MIME, "text/plain", "application/octet-stream")

/**
 * Import and export use one-shot document pickers. The returned URIs are read
 * or written once; no persistable permission is requested.
 */
@Composable
fun ProgramListScreen(
    viewModel: ProgramListViewModel,
    onOpenProgram: (Long) -> Unit,
    onOpenExercises: () -> Unit,
    onOpenSkills: () -> Unit,
    onImport: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var exportingProgramId by rememberSaveable { mutableLongStateOf(0L) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri.toString())
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DOCUMENT_MIME),
    ) { uri ->
        val programId = exportingProgramId
        if (uri != null && programId != 0L) {
            viewModel.onEvent(ProgramListEvent.Export(programId, uri.toString()))
        }
        exportingProgramId = 0L
    }
    BuilderColumn(title = stringResource(R.string.builder_programs), onBack = null, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorButton(
                    label = stringResource(R.string.builder_exercises),
                    onClick = onOpenExercises,
                    modifier = Modifier.weight(1f)
                )
                EditorButton(
                    label = stringResource(R.string.builder_skills),
                    onClick = onOpenSkills,
                    modifier = Modifier.weight(1f)
                )
            }
            EditorButton(
                label = stringResource(R.string.builder_import),
                onClick = { importLauncher.launch(OpenableMimeTypes) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        state.notice?.let { notice ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        when (notice) {
                            ProgramListNotice.EXPORTED -> R.string.builder_exported
                            ProgramListNotice.EXPORT_FAILED -> R.string.builder_export_failed
                        },
                    ),
                )
                EditorButton(
                    label = stringResource(R.string.builder_dismiss),
                    onClick = { viewModel.onEvent(ProgramListEvent.DismissNotice) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.draftName,
                onValueChange = { viewModel.onEvent(ProgramListEvent.DraftName(it)) },
                label = { Text(stringResource(R.string.builder_program_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            EditorButton(
                label = stringResource(R.string.builder_add),
                onClick = { viewModel.onEvent(ProgramListEvent.Add) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        state.programs.forEach { program ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    EditorButton(
                        label = program.name,
                        onClick = { onOpenProgram(program.id) },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.builder_active),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Switch(
                        checked = program.isActive,
                        onCheckedChange = { checked ->
                            if (checked) viewModel.onEvent(ProgramListEvent.SetActive(program.id))
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EditorButton(
                        label = stringResource(R.string.builder_export),
                        onClick = {
                            exportingProgramId = program.id
                            exportLauncher.launch(exportFileName(program.name))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    EditorButton(
                        label = stringResource(R.string.builder_delete),
                        onClick = { viewModel.onEvent(ProgramListEvent.Delete(program.id)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

internal fun exportFileName(programName: String): String {
    val slug = programName.lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifEmpty { "program" }
    return "$slug.json"
}
