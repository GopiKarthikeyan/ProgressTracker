package com.forge.hypertrophy.ui.screens.media

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.forge.hypertrophy.data.repository.MediaRepository
import com.forge.hypertrophy.domain.media.SyncedPlayback
import com.forge.hypertrophy.media.ClipDurations
import com.forge.hypertrophy.ui.navigation.VideoComparisonRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoComparisonUiState(
    val leftPath: String? = null,
    val rightPath: String? = null,
    val leftLabel: String = "",
    val rightLabel: String = "",
    val playback: SyncedPlayback = SyncedPlayback(0, 0),
    /** Increments on every explicit seek so the screen re-positions both players once. */
    val seekSerial: Int = 0,
    val loaded: Boolean = false,
)

sealed interface VideoComparisonEvent {
    data object TogglePlay : VideoComparisonEvent
    data class Seek(val positionMs: Long) : VideoComparisonEvent
    data class StepFrame(val forward: Boolean) : VideoComparisonEvent
    data object ToggleSpeed : VideoComparisonEvent
    data class LeftOffset(val ms: Long) : VideoComparisonEvent
    data class RightOffset(val ms: Long) : VideoComparisonEvent
    /** Reported by the screen while the players run. */
    data class Progress(val positionMs: Long) : VideoComparisonEvent
    data object ReachedEnd : VideoComparisonEvent
}

@HiltViewModel
class VideoComparisonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val media: MediaRepository,
    private val durations: ClipDurations,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<VideoComparisonRoute>()
    private val _uiState = MutableStateFlow(VideoComparisonUiState())
    val uiState: StateFlow<VideoComparisonUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val left = media.get(route.leftId) ?: return@launch
            val right = media.get(route.rightId) ?: return@launch
            val leftFile = media.file(left)
            val rightFile = media.file(right)
            val leftMs = runCatching { durations.durationMs(leftFile) }.getOrDefault(0L)
            val rightMs = runCatching { durations.durationMs(rightFile) }.getOrDefault(0L)
            _uiState.update {
                it.copy(
                    leftPath = leftFile.path,
                    rightPath = rightFile.path,
                    leftLabel = left.label ?: "",
                    rightLabel = right.label ?: "",
                    playback = SyncedPlayback(leftMs, rightMs),
                    loaded = true,
                )
            }
        }
    }

    fun onEvent(event: VideoComparisonEvent) {
        _uiState.update { state ->
            val playback = state.playback
            when (event) {
                VideoComparisonEvent.TogglePlay -> state.copy(playback = playback.togglePlaying())
                is VideoComparisonEvent.Seek -> state.copy(playback = playback.seekTo(event.positionMs), seekSerial = state.seekSerial + 1)
                is VideoComparisonEvent.StepFrame -> state.copy(playback = playback.stepFrame(event.forward), seekSerial = state.seekSerial + 1)
                VideoComparisonEvent.ToggleSpeed -> state.copy(playback = playback.toggleSpeed())
                is VideoComparisonEvent.LeftOffset -> state.copy(playback = playback.withLeftOffset(event.ms), seekSerial = state.seekSerial + 1)
                is VideoComparisonEvent.RightOffset -> state.copy(playback = playback.withRightOffset(event.ms), seekSerial = state.seekSerial + 1)
                is VideoComparisonEvent.Progress -> state.copy(playback = playback.copy(positionMs = event.positionMs.coerceIn(0, playback.lengthMs)))
                VideoComparisonEvent.ReachedEnd -> state.copy(playback = playback.copy(positionMs = playback.lengthMs, playing = false))
            }
        }
    }
}
