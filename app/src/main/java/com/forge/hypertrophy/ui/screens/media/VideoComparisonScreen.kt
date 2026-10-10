package com.forge.hypertrophy.ui.screens.media

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.media.SLOW_SPEED
import com.forge.hypertrophy.ui.components.NumericText
import com.forge.hypertrophy.ui.components.TouchTargets
import com.forge.hypertrophy.ui.theme.Black
import com.forge.hypertrophy.ui.theme.NeonAccent
import com.forge.hypertrophy.ui.theme.Ink
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoComparisonScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoComparisonViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val left = remember { ExoPlayer.Builder(context).build().apply { setSeekParameters(SeekParameters.EXACT) } }
    val right = remember { ExoPlayer.Builder(context).build().apply { setSeekParameters(SeekParameters.EXACT) } }
    DisposableEffect(Unit) {
        onDispose {
            left.release()
            right.release()
        }
    }
    LaunchedEffect(state.leftPath, state.rightPath) {
        val leftPath = state.leftPath ?: return@LaunchedEffect
        val rightPath = state.rightPath ?: return@LaunchedEffect
        left.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(leftPath))))
        right.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(rightPath))))
        left.volume = 0f
        right.volume = 0f
        left.prepare()
        right.prepare()
    }
    val playback = state.playback
    LaunchedEffect(state.seekSerial, state.loaded) {
        if (!state.loaded) return@LaunchedEffect
        left.seekTo(playback.leftPositionMs)
        right.seekTo(playback.rightPositionMs)
    }
    LaunchedEffect(playback.playing, playback.speed) {
        left.setPlaybackSpeed(playback.speed)
        right.setPlaybackSpeed(playback.speed)
        left.playWhenReady = playback.playing
        right.playWhenReady = playback.playing
    }
    LaunchedEffect(playback.playing, state.loaded) {
        while (playback.playing && state.loaded) {
            delay(POLL_MS)
            val master = (left.currentPosition - playback.leftOffsetMs).coerceAtLeast(0)
            val expectedRight = master + playback.rightOffsetMs
            if (abs(right.currentPosition - expectedRight) > DRIFT_MS) right.seekTo(expectedRight)
            if (master >= playback.lengthMs || left.playbackState == Player.STATE_ENDED) {
                viewModel.onEvent(VideoComparisonEvent.ReachedEnd)
            } else {
                viewModel.onEvent(VideoComparisonEvent.Progress(master))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = TouchTargets.Workout)) {
                Text(stringResource(R.string.builder_back))
            }
            Text(stringResource(R.string.media_compare), color = Ink, style = MaterialTheme.typography.titleMedium)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClipPane(left, state.leftLabel, state.leftDateLine, Modifier.weight(1f))
            ClipPane(right, state.rightLabel, state.rightDateLine, Modifier.weight(1f))
        }
        state.apartLine?.let { apart ->
            Text(
                apart,
                color = Ink,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            NumericText(formatMs(playback.positionMs), color = NeonAccent, modifier = Modifier.weight(1f))
            NumericText(formatMs(playback.lengthMs), color = Ink)
        }
        Slider(
            value = if (playback.lengthMs == 0L) 0f else playback.positionMs.toFloat() / playback.lengthMs,
            onValueChange = { fraction -> viewModel.onEvent(VideoComparisonEvent.Seek((fraction * playback.lengthMs).toLong())) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTargets.Workout),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlButton(stringResource(R.string.media_frame_back), Modifier.weight(1f)) {
                viewModel.onEvent(VideoComparisonEvent.StepFrame(forward = false))
            }
            ControlButton(
                stringResource(if (playback.playing) R.string.media_pause else R.string.media_play),
                Modifier.weight(1f),
            ) {
                viewModel.onEvent(VideoComparisonEvent.TogglePlay)
            }
            ControlButton(stringResource(R.string.media_frame_forward), Modifier.weight(1f)) {
                viewModel.onEvent(VideoComparisonEvent.StepFrame(forward = true))
            }
        }
        ControlButton(
            stringResource(if (playback.speed == SLOW_SPEED) R.string.media_speed_normal else R.string.media_speed_half),
            Modifier.fillMaxWidth(),
        ) {
            viewModel.onEvent(VideoComparisonEvent.ToggleSpeed)
        }
        OffsetRow(stringResource(R.string.media_left_offset), playback.leftOffsetMs) { ms ->
            viewModel.onEvent(VideoComparisonEvent.LeftOffset(ms))
        }
        OffsetRow(stringResource(R.string.media_right_offset), playback.rightOffsetMs) { ms ->
            viewModel.onEvent(VideoComparisonEvent.RightOffset(ms))
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ClipPane(player: ExoPlayer, label: String, dateLine: String?, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, color = Ink)
        dateLine?.let {
            Text(it, color = Ink, style = MaterialTheme.typography.bodyMedium)
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    this.player = player
                }
            },
            update = { view -> view.player = player },
        )
    }
}

@Composable
private fun OffsetRow(label: String, offsetMs: Long, onOffset: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Ink, modifier = Modifier.weight(1f))
        ControlButton("-", Modifier.heightIn(min = TouchTargets.Workout)) { onOffset(offsetMs - OFFSET_STEP_MS) }
        NumericText(formatMs(offsetMs), color = NeonAccent)
        ControlButton("+", Modifier.heightIn(min = TouchTargets.Workout)) { onOffset(offsetMs + OFFSET_STEP_MS) }
    }
}

@Composable
private fun ControlButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = TouchTargets.Workout)) {
        Text(label)
    }
}

private fun formatMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d.%03d", safe / 60_000, (safe / 1_000) % 60, safe % 1_000)
}

private const val POLL_MS = 100L
private const val DRIFT_MS = 80L
private const val OFFSET_STEP_MS = 100L
