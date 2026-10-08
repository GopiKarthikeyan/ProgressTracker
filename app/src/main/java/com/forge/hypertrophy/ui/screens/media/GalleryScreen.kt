package com.forge.hypertrophy.ui.screens.media

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.model.Pose
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.theme.Black
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.Ink

@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onRecord: (exerciseId: Long, setEntryId: Long?) -> Unit,
    onCompare: (leftId: Long, rightId: Long) -> Unit,
    onPhysique: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = TouchTargets.Workout)) {
                Text(stringResource(R.string.builder_back))
            }
            Text(stringResource(R.string.media_gallery), color = Ink, style = MaterialTheme.typography.headlineSmall)
        }
        ClipsSection(state, viewModel::onEvent, onRecord, onCompare)
        PhysiqueSection(state, viewModel::onEvent, onPhysique)
    }
}

@Composable
private fun ClipsSection(
    state: GalleryUiState,
    onEvent: (GalleryEvent) -> Unit,
    onRecord: (Long, Long?) -> Unit,
    onCompare: (Long, Long) -> Unit,
) {
    Text(stringResource(R.string.media_clips), color = NeonAccent, style = MaterialTheme.typography.titleMedium)
    if (state.exercises.isEmpty()) {
        Text(stringResource(R.string.media_no_exercises), color = Ink)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.exercises.forEach { exercise ->
            TextButton(
                onClick = { onEvent(GalleryEvent.SelectExercise(exercise.id)) },
                enabled = state.selectedExerciseId != exercise.id,
                modifier = Modifier.heightIn(min = TouchTargets.Workout),
            ) {
                Text(exercise.name)
            }
        }
    }
    val exerciseId = state.selectedExerciseId
    if (exerciseId == null) {
        Text(stringResource(R.string.media_pick_exercise), color = Ink)
        return
    }
    MediaButton(stringResource(R.string.media_record)) { onRecord(exerciseId, null) }
    if (state.recentSets.isNotEmpty()) {
        Text(stringResource(R.string.media_attach_to_set), color = Ink)
        state.recentSets.forEach { set ->
            MediaButton(set.label) { onRecord(exerciseId, set.id) }
        }
    }
    if (state.clips.isEmpty()) {
        Text(stringResource(R.string.media_no_clips), color = Ink)
        return
    }
    state.clips.forEach { clip ->
        val chosen = clip.id in state.compareIds
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(clip.label, color = if (chosen) NeonAccent else Ink)
            clip.capturedOn?.let { NumericText(it.toString(), color = Ink) }
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { onEvent(GalleryEvent.ToggleCompare(clip.id)) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = TouchTargets.Workout),
                ) {
                    Text(stringResource(if (chosen) R.string.media_deselect else R.string.media_select))
                }
                TextButton(
                    onClick = { onCompare(clip.id, clip.id) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = TouchTargets.Workout),
                ) {
                    Text(stringResource(R.string.media_play))
                }
                TextButton(
                    onClick = { onEvent(GalleryEvent.Delete(clip.id)) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = TouchTargets.Workout),
                ) {
                    Text(stringResource(R.string.media_delete))
                }
            }
        }
    }
    if (state.compareIds.size == 2) {
        MediaButton(stringResource(R.string.media_compare)) { onCompare(state.compareIds[0], state.compareIds[1]) }
    }
}

@Composable
private fun PhysiqueSection(
    state: GalleryUiState,
    onEvent: (GalleryEvent) -> Unit,
    onPhysique: () -> Unit,
) {
    Text(stringResource(R.string.media_physique), color = NeonAccent, style = MaterialTheme.typography.titleMedium)
    MediaButton(stringResource(R.string.media_take_photos), onClick = onPhysique)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pose.entries.forEach { pose ->
            TextButton(
                onClick = { onEvent(GalleryEvent.SelectPose(pose)) },
                enabled = state.sliderPose != pose,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = TouchTargets.Workout),
            ) {
                Text(poseLabel(pose))
            }
        }
    }
    val before by rememberPhoto(state.before?.path)
    val after by rememberPhoto(state.after?.path)
    val beforeBitmap = before
    val afterBitmap = after
    if (beforeBitmap != null && afterBitmap != null) {
        PhysiqueSlider(beforeBitmap, afterBitmap)
        Row(modifier = Modifier.fillMaxWidth()) {
            NumericText(state.before?.capturedOn?.toString() ?: "", color = Ink, modifier = Modifier.weight(1f))
            NumericText(state.after?.capturedOn?.toString() ?: "", color = Ink)
        }
    } else {
        Text(stringResource(R.string.media_no_photos), color = Ink)
    }
    Text(stringResource(R.string.media_milestones), color = Ink)
    if (state.milestones.isEmpty()) {
        Text(stringResource(R.string.media_no_milestones), color = Ink)
    }
    state.milestones.forEach { milestone ->
        val photoOn = milestone.photoOn
        TextButton(
            onClick = { onEvent(GalleryEvent.SelectBefore(photoOn)) },
            enabled = photoOn != null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTargets.Workout),
        ) {
            Text(
                if (photoOn == null) {
                    stringResource(R.string.media_milestone_missing, milestone.dueOn.toString())
                } else {
                    stringResource(R.string.media_milestone_photo, milestone.dueOn.toString(), photoOn.toString())
                },
            )
        }
    }
}
