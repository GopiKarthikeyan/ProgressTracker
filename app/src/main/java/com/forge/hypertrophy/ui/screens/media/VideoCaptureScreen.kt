package com.forge.hypertrophy.ui.screens.media

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.media.CaptureKeys
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.theme.Black
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.Ink
import java.io.File

@Composable
fun VideoCaptureScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] == true || cameraGranted
        audioGranted = result[Manifest.permission.RECORD_AUDIO] == true || audioGranted
        if (cameraGranted) viewModel.onEvent(VideoCaptureEvent.Trigger)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val code = event.key.nativeKeyCode
                if (!CaptureKeys.isCaptureKey(code)) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown && cameraGranted) {
                    viewModel.onEvent(VideoCaptureEvent.HardwareKey(code))
                }
                true
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = TouchTargets.Workout)) {
                Text(stringResource(R.string.builder_back))
            }
            Text(state.label, color = Ink, style = MaterialTheme.typography.titleMedium)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (cameraGranted) {
                Recorder(state, audioGranted, viewModel::onEvent)
            } else {
                Text(stringResource(R.string.media_camera_permission), color = Ink)
            }
            if (state.phase == CapturePhase.PRE_ROLL) {
                NumericText(
                    state.countdownLeft.toString(),
                    color = NeonAccent,
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (state.phase == CapturePhase.RECORDING) {
                Text(stringResource(R.string.media_recording), color = NeonAccent, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        CountdownBeeps(state.countdownLeft, active = state.phase == CapturePhase.PRE_ROLL)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.media_pre_roll), color = Ink, modifier = Modifier.weight(1f))
            Switch(
                checked = state.preRollEnabled,
                onCheckedChange = { viewModel.onEvent(VideoCaptureEvent.TogglePreRoll) },
                enabled = state.phase == CapturePhase.IDLE || state.phase == CapturePhase.SAVED,
            )
        }
        when (state.phase) {
            CapturePhase.PROCESSING -> {
                Text(stringResource(R.string.media_processing), color = Ink)
                LinearProgressIndicator(
                    progress = { state.processingProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = NeonAccent,
                )
            }
            CapturePhase.SAVED -> Text(stringResource(R.string.media_saved), color = NeonAccent)
            else -> Unit
        }
        if (state.failed) {
            Text(stringResource(R.string.media_failed), color = NeonAccent)
            MediaButton(stringResource(R.string.dashboard_dismiss)) { viewModel.onEvent(VideoCaptureEvent.DismissFailure) }
        }
        val mainLabel = when (state.phase) {
            CapturePhase.IDLE, CapturePhase.SAVED -> R.string.media_start
            CapturePhase.PRE_ROLL -> R.string.media_cancel_pre_roll
            CapturePhase.RECORDING -> R.string.media_stop
            CapturePhase.STOPPING, CapturePhase.PROCESSING -> R.string.media_processing
        }
        MediaButton(
            stringResource(mainLabel),
            enabled = state.phase != CapturePhase.STOPPING && state.phase != CapturePhase.PROCESSING,
        ) {
            if (cameraGranted) {
                viewModel.onEvent(VideoCaptureEvent.Trigger)
            } else {
                permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            }
        }
    }
}

@Composable
private fun Recorder(
    state: VideoCaptureUiState,
    audioGranted: Boolean,
    onEvent: (VideoCaptureEvent) -> Unit,
) {
    val context = LocalContext.current
    val controller = rememberCameraController(CameraController.VIDEO_CAPTURE)
    var recording by remember { mutableStateOf<Recording?>(null) }
    CameraPreview(controller, Modifier.fillMaxSize())

    LaunchedEffect(state.phase, state.rawPath) {
        when (state.phase) {
            CapturePhase.RECORDING -> {
                val path = state.rawPath ?: return@LaunchedEffect
                if (recording != null) return@LaunchedEffect
                val options = FileOutputOptions.Builder(File(path)).build()
                val micAllowed = audioGranted &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                val audio = if (micAllowed) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED
                recording = controller.startRecording(options, audio, ContextCompat.getMainExecutor(context)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        recording = null
                        if (event.hasError()) {
                            onEvent(VideoCaptureEvent.RecordingFailed)
                        } else {
                            onEvent(VideoCaptureEvent.RecordingFinished(path))
                        }
                    }
                }
            }
            CapturePhase.STOPPING -> recording?.stop()
            else -> Unit
        }
    }
    DisposableEffect(Unit) {
        onDispose { recording?.stop() }
    }
}
