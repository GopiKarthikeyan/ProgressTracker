package com.forge.hypertrophy.ui.screens.diagnostics

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.screens.routine.BuilderColumn
import com.forge.hypertrophy.ui.screens.routine.EditorButton
import com.forge.hypertrophy.ui.theme.Ink

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.shareText) {
        val text = state.shareText ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, null))
        viewModel.onEvent(DiagnosticsEvent.ConsumeShare)
    }
    DiagnosticsContent(state, viewModel::onEvent, onBack, modifier)
}

@Composable
fun DiagnosticsContent(
    state: DiagnosticsUiState,
    onEvent: (DiagnosticsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuilderColumn(title = stringResource(R.string.diagnostics_title), onBack = onBack, modifier = modifier) {
        if (state.files.isEmpty()) Text(stringResource(R.string.diagnostics_empty), color = Ink)
        state.files.forEach { name ->
            EditorButton(label = name, onClick = { onEvent(DiagnosticsEvent.Open(name)) })
        }
        if (state.body.isNotEmpty()) Text(state.body, color = Ink)
        EditorButton(label = stringResource(R.string.diagnostics_share), onClick = { onEvent(DiagnosticsEvent.Share) })
        EditorButton(label = stringResource(R.string.diagnostics_clear), onClick = { onEvent(DiagnosticsEvent.Clear) })
    }
}
