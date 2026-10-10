package com.forge.hypertrophy.ui.screens.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.media.POSE_ORDER
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.theme.Cream
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.Rose

private const val IMPORT_PICK_MAX = 20

@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onCompare: (leftId: Long, rightId: Long) -> Unit,
    onPhysique: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickClip = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(IMPORT_PICK_MAX),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.onEvent(GalleryEvent.ImportClips(uris))
    }
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(IMPORT_PICK_MAX),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.onEvent(GalleryEvent.ImportPhotos(uris))
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Cream)
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
        if (state.importing) {
            Text(stringResource(R.string.media_importing), color = Ink)
        }
        state.importedCount?.let { count ->
            Text(pluralStringResource(R.plurals.media_imported, count, count), color = Ink)
            MediaButton(stringResource(R.string.dashboard_dismiss)) {
                viewModel.onEvent(GalleryEvent.DismissImportResult)
            }
        }
        if (state.importFailed) {
            Text(stringResource(R.string.media_import_failed), color = Rose)
            MediaButton(stringResource(R.string.dashboard_dismiss)) {
                viewModel.onEvent(GalleryEvent.DismissImportFailure)
            }
        }
        ClipsSection(
            state = state,
            onEvent = viewModel::onEvent,
            onCompare = onCompare,
            onImportClip = {
                pickClip.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
        )
        PhysiqueSection(
            state = state,
            onEvent = viewModel::onEvent,
            onPhysique = onPhysique,
            onImportPhoto = {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}

@Composable
private fun ClipsSection(
    state: GalleryUiState,
    onEvent: (GalleryEvent) -> Unit,
    onCompare: (Long, Long) -> Unit,
    onImportClip: () -> Unit,
) {
    Text(stringResource(R.string.media_clips), color = Rose, style = MaterialTheme.typography.titleMedium)
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
    MediaButton(
        label = stringResource(R.string.media_import_clip),
        enabled = !state.importing,
        onClick = onImportClip,
    )
    if (state.clips.isEmpty()) {
        Text(stringResource(R.string.media_no_clips), color = Ink)
        return
    }
    state.clips.forEach { clip ->
        val chosen = clip.id in state.compareIds
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(clip.label, color = if (chosen) Rose else Ink)
            clip.dateLine?.let { Text(it, color = Ink, style = MaterialTheme.typography.bodyMedium) }
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
    onImportPhoto: () -> Unit,
) {
    Text(stringResource(R.string.media_physique), color = Rose, style = MaterialTheme.typography.titleMedium)
    MediaButton(stringResource(R.string.media_take_photos), onClick = onPhysique)
    MediaButton(
        label = stringResource(R.string.media_import_photo),
        enabled = !state.importing,
        onClick = onImportPhoto,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        POSE_ORDER.forEach { pose ->
            TextButton(
                onClick = { onEvent(GalleryEvent.SelectPose(pose)) },
                enabled = state.sliderPose != pose,
                modifier = Modifier.heightIn(min = TouchTargets.Workout),
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
            Text(
                state.before?.dateLine.orEmpty(),
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                state.after?.dateLine.orEmpty(),
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
            )
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
                if (milestone.photoLine == null) {
                    stringResource(R.string.media_milestone_missing, milestone.dueLine)
                } else {
                    stringResource(R.string.media_milestone_photo, milestone.dueLine, milestone.photoLine)
                },
            )
        }
    }
}
