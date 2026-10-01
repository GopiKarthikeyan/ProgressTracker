package com.forge.hypertrophy.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.theme.NeonAccent

/**
 * Reorder by dragging the handle, or with the up and down buttons.
 * The buttons are the path that does not require a drag.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemKey: (T) -> Any,
    itemContent: @Composable (T) -> Unit,
) {
    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                DragHandle(
                    itemKey = itemKey(item),
                    index = index,
                    lastIndex = items.lastIndex,
                    onMove = onMove,
                )
                Row(modifier = Modifier.weight(1f)) {
                    itemContent(item)
                }
                TextButton(
                    onClick = { onMove(index, index - 1) },
                    enabled = index > 0,
                    modifier = Modifier.sizeIn(minWidth = TouchTargets.Editor, minHeight = TouchTargets.Editor),
                ) {
                    Text(stringResource(R.string.builder_up))
                }
                TextButton(
                    onClick = { onMove(index, index + 1) },
                    enabled = index < items.lastIndex,
                    modifier = Modifier.sizeIn(minWidth = TouchTargets.Editor, minHeight = TouchTargets.Editor),
                ) {
                    Text(stringResource(R.string.builder_down))
                }
            }
        }
    }
}

@Composable
private fun DragHandle(
    itemKey: Any,
    index: Int,
    lastIndex: Int,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    var accumulated by remember(itemKey) { mutableFloatStateOf(0f) }
    Text(
        text = stringResource(R.string.builder_drag_handle),
        color = NeonAccent,
        modifier = Modifier
            .sizeIn(minWidth = TouchTargets.Editor, minHeight = TouchTargets.Editor)
            .heightIn(min = TouchTargets.Editor)
            .padding(horizontal = 4.dp)
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
