package com.forge.hypertrophy.ui.screens.session

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
import com.forge.hypertrophy.ui.theme.White

@Composable
fun SessionListScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SessionListContent(state, onBack, onOpen, modifier)
}

@Composable
fun SessionListContent(
    state: SessionListUiState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BuilderColumn(title = stringResource(R.string.session_list_title), onBack = onBack, modifier = modifier) {
        if (state.loaded && state.sessions.isEmpty()) {
            Text(stringResource(R.string.session_list_empty), color = White)
        }
        state.sessions.forEach { session ->
            val edited = if (session.edited) stringResource(R.string.session_edited_mark) else ""
            EditorButton(
                label = stringResource(
                    R.string.session_row,
                    session.date.toString(),
                    session.status.name,
                    edited,
                ),
                onClick = { onOpen(session.id) },
            )
        }
    }
}
