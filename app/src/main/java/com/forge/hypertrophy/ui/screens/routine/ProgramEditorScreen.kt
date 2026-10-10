package com.forge.hypertrophy.ui.screens.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.components.SecondaryButton
import com.forge.hypertrophy.ui.components.SurfaceCard
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.Rose

@Composable
fun ProgramEditorScreen(
    viewModel: ProgramEditorViewModel,
    onBack: () -> Unit,
    onOpenDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(ProgramEditorEvent.Refresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BuilderColumn(title = stringResource(R.string.builder_days), onBack = onBack, modifier = modifier) {
        SurfaceCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SecondaryButton(
            label = stringResource(R.string.builder_add_day),
            onClick = { viewModel.onEvent(ProgramEditorEvent.AddDay) },
            modifier = Modifier.fillMaxWidth(),
        )

        DaysList(
            days = state.days,
            onOpen = onOpenDay,
            onDelete = { viewModel.onEvent(ProgramEditorEvent.DeleteDay(it)) },
            onMove = { from, to -> viewModel.onEvent(ProgramEditorEvent.MoveDay(from, to)) },
        )
    }
}

@Composable
private fun DaysList(
    days: List<DayRow>,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        days.forEachIndexed { index, day ->
            DayCard(
                day = day,
                index = index,
                lastIndex = days.lastIndex,
                onOpen = { onOpen(day.id) },
                onDelete = { onDelete(day.id) },
                onMove = onMove,
            )
        }
    }
}

@Composable
private fun DayCard(
    day: DayRow,
    index: Int,
    lastIndex: Int,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val weekday = day.dayOfWeek?.minus(1)?.let { weekdayIndex ->
        stringArrayResource(R.array.builder_weekdays).getOrNull(weekdayIndex)
    }
    val exerciseCount = if (day.isRest) {
        null
    } else {
        pluralStringResource(R.plurals.builder_exercise_count, day.slotCount, day.slotCount)
    }
    val meta = when {
        day.isRest && weekday != null -> stringResource(R.string.today_rest_day) + " · " + weekday
        day.isRest -> stringResource(R.string.today_rest_day)
        weekday != null && exerciseCount != null -> "$weekday · $exerciseCount"
        weekday != null -> weekday
        exerciseCount != null -> exerciseCount
        else -> null
    }
    val upDescription = stringResource(R.string.builder_up)
    val downDescription = stringResource(R.string.builder_down)
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DayDragHandle(
                itemKey = day.id,
                index = index,
                lastIndex = lastIndex,
                onMove = onMove,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = TouchTargets.Workout)
                    .clickable(onClickLabel = stringResource(R.string.builder_open_day), onClick = onOpen),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (meta != null) {
                    Text(
                        text = meta,
                        color = Rose,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = day.label.ifBlank { stringResource(R.string.builder_untitled_day) },
                    color = Ink,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = { onMove(index, index - 1) },
                enabled = index > 0,
                modifier = Modifier
                    .sizeIn(minWidth = TouchTargets.Editor, minHeight = TouchTargets.Editor)
                    .semantics { contentDescription = upDescription },
            ) {
                Text("▲")
            }
            TextButton(
                onClick = { onMove(index, index + 1) },
                enabled = index < lastIndex,
                modifier = Modifier
                    .sizeIn(minWidth = TouchTargets.Editor, minHeight = TouchTargets.Editor)
                    .semantics { contentDescription = downDescription },
            ) {
                Text("▼")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            EditorButton(
                label = stringResource(R.string.builder_delete),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun DayDragHandle(
    itemKey: Any,
    index: Int,
    lastIndex: Int,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    val description = stringResource(R.string.builder_drag_handle)
    var accumulated by remember(itemKey) { mutableFloatStateOf(0f) }
    Text(
        text = "☰",
        color = Rose,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .sizeIn(minWidth = TouchTargets.Editor, minHeight = TouchTargets.Editor)
            .semantics { contentDescription = description }
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .pointerInput(itemKey, index, lastIndex) {
                detectDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDragCancel = { accumulated = 0f },
                    onDragEnd = { accumulated = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += dragAmount.y
                        val threshold = TouchTargets.Editor.toPx()
                        when {
                            accumulated > threshold && index < lastIndex -> {
                                onMove(index, index + 1)
                                accumulated = 0f
                            }
                            accumulated < -threshold && index > 0 -> {
                                onMove(index, index - 1)
                                accumulated = 0f
                            }
                        }
                    },
                )
            },
    )
}
