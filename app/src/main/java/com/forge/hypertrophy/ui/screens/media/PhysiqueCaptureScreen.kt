package com.forge.hypertrophy.ui.screens.media

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.media.POSE_ORDER
import com.forge.hypertrophy.domain.model.Pose
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.theme.Cream
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.Rose
import java.io.File

@Composable
fun PhysiqueCaptureScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhysiqueCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (granted) viewModel.onEvent(PhysiqueCaptureEvent.Trigger)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Cream)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = TouchTargets.Workout)) {
                Text(stringResource(R.string.builder_back))
            }
            Text(
                state.pose?.let { poseLabel(it) } ?: stringResource(R.string.media_physique_complete),
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            pluralStringResource(R.plurals.media_physique_progress, POSE_ORDER.size, state.done.size, POSE_ORDER.size),
            color = Ink,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (cameraGranted && state.phase != PhotoPhase.COMPLETE) {
                Shutter(state, viewModel::onEvent)
            } else if (!cameraGranted) {
                Text(stringResource(R.string.media_camera_permission), color = Ink)
            }
            val ghost by rememberPhoto(state.ghostPath)
            ghost?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    alpha = GHOST_ALPHA,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (state.phase == PhotoPhase.COUNTDOWN) {
                NumericText(
                    state.countdownLeft.toString(),
                    color = Rose,
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        CountdownBeeps(state.countdownLeft, active = state.phase == PhotoPhase.COUNTDOWN)
        if (state.failed) {
            Text(stringResource(R.string.media_failed), color = Rose)
            MediaButton(stringResource(R.string.dashboard_dismiss)) { viewModel.onEvent(PhysiqueCaptureEvent.DismissFailure) }
        }
        when (state.phase) {
            PhotoPhase.COMPLETE -> {
                MediaButton(stringResource(R.string.media_retake)) {
                    if (cameraGranted) {
                        viewModel.onEvent(PhysiqueCaptureEvent.Retake)
                    } else {
                        permission.launch(Manifest.permission.CAMERA)
                    }
                }
                MediaButton(stringResource(R.string.media_done), onClick = onBack)
            }
            PhotoPhase.COUNTDOWN -> MediaButton(stringResource(R.string.media_cancel_timer)) {
                viewModel.onEvent(PhysiqueCaptureEvent.Cancel)
            }
            PhotoPhase.CAPTURING -> MediaButton(stringResource(R.string.media_capturing), enabled = false) {}
            PhotoPhase.IDLE -> {
                MediaButton(stringResource(R.string.media_start_timer)) {
                    if (cameraGranted) {
                        viewModel.onEvent(PhysiqueCaptureEvent.Trigger)
                    } else {
                        permission.launch(Manifest.permission.CAMERA)
                    }
                }
                if (state.ghostPath != null) {
                    MediaButton(stringResource(R.string.media_retake)) {
                        if (cameraGranted) {
                            viewModel.onEvent(PhysiqueCaptureEvent.Retake)
                        } else {
                            permission.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
                MediaButton(stringResource(R.string.media_skip_pose)) { viewModel.onEvent(PhysiqueCaptureEvent.SkipPose) }
            }
        }
    }
}

@Composable
private fun Shutter(state: PhysiqueCaptureUiState, onEvent: (PhysiqueCaptureEvent) -> Unit) {
    val context = LocalContext.current
    val controller = rememberCameraController(CameraController.IMAGE_CAPTURE)
    CameraPreview(controller, Modifier.fillMaxSize())
    LaunchedEffect(state.phase, state.targetPath) {
        if (state.phase != PhotoPhase.CAPTURING) return@LaunchedEffect
        val path = state.targetPath ?: return@LaunchedEffect
        val options = ImageCapture.OutputFileOptions.Builder(File(path)).build()
        controller.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onEvent(PhysiqueCaptureEvent.Captured(path))
                }

                override fun onError(exception: ImageCaptureException) {
                    onEvent(PhysiqueCaptureEvent.CaptureFailed)
                }
            },
        )
    }
}

@Composable
internal fun poseLabel(pose: Pose): String = when (pose) {
    Pose.FRONT -> stringResource(R.string.media_pose_front)
    Pose.SIDE -> stringResource(R.string.media_pose_side)
    Pose.BACK -> stringResource(R.string.media_pose_back)
    Pose.QUADRICEPS -> stringResource(R.string.media_pose_quadriceps)
    Pose.HAMSTRINGS -> stringResource(R.string.media_pose_hamstrings)
    Pose.CALVES -> stringResource(R.string.media_pose_calves)
}

private const val GHOST_ALPHA = 0.35f
