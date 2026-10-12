package com.forge.hypertrophy.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.exercise.MuscleGroupOrder
import com.forge.hypertrophy.domain.model.SlotCategory

data class ExercisePickItem(
    val id: Long,
    val name: String,
    val primaryMuscles: List<String>,
)

@Composable
fun ExercisePickerList(
    items: List<ExercisePickItem>,
    selectedId: Long,
    category: SlotCategory,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    selectedIds: Set<Long> = emptySet(),
    multiSelect: Boolean = false,
) {
    val grouped = remember(items) {
        items
            .sortedWith(
                compareBy<ExercisePickItem> { MuscleGroupOrder.primarySortKey(it.primaryMuscles) }
                    .thenBy { it.name.lowercase() },
            )
            .groupBy { MuscleGroupOrder.primarySortKey(it.primaryMuscles) }
            .toList()
            .sortedBy { (key, _) -> MuscleGroupOrder.orderedKeys().indexOf(key).takeIf { it >= 0 } ?: Int.MAX_VALUE }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.builder_exercise_picker_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            return
        }
        grouped.forEach { (muscleKey, rows) ->
            Text(
                text = muscleLabel(muscleKey),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            rows.forEach { item ->
                val selected = if (multiSelect) item.id in selectedIds else item.id == selectedId
                ExercisePickerRow(
                    item = item,
                    category = category,
                    selected = selected,
                    onClick = { onSelect(item.id) },
                )
            }
        }
    }
}

@Composable
private fun ExercisePickerRow(
    item: ExercisePickItem,
    category: SlotCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTargets.Workout)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExerciseSilhouette(
            exerciseName = item.name,
            category = category,
            size = 48.dp,
        )
        Text(
            text = item.name,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun muscleLabel(key: String): String = stringResource(
    when (key) {
        "CHEST" -> R.string.muscle_chest
        "BACK_LATS" -> R.string.muscle_back_lats
        "BACK_UPPER" -> R.string.muscle_back_upper
        "SHOULDERS_FRONT" -> R.string.muscle_shoulders_front
        "SHOULDERS_SIDE" -> R.string.muscle_shoulders_side
        "SHOULDERS_REAR" -> R.string.muscle_shoulders_rear
        "BICEPS" -> R.string.muscle_biceps
        "TRICEPS" -> R.string.muscle_triceps
        "FOREARMS" -> R.string.muscle_forearms
        "CORE" -> R.string.muscle_core
        "QUADS" -> R.string.muscle_quads
        "HAMSTRINGS" -> R.string.muscle_hamstrings
        "GLUTES" -> R.string.muscle_glutes
        "ADDUCTORS" -> R.string.muscle_adductors
        "CALVES" -> R.string.muscle_calves
        "LOWER_BACK" -> R.string.muscle_lower_back
        else -> R.string.muscle_other
    },
)
