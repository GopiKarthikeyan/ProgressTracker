package com.forge.hypertrophy.ui.screens.media

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.forge.hypertrophy.data.media.MediaFiles
import com.forge.hypertrophy.data.media.SetLabelResolver
import com.forge.hypertrophy.domain.media.CaptureKeys
import com.forge.hypertrophy.domain.media.Countdown
import com.forge.hypertrophy.domain.media.PRE_ROLL_SECONDS
import com.forge.hypertrophy.domain.workout.ElapsedRealtimeClock
import com.forge.hypertrophy.domain.workout.HandsFreeGate
import com.forge.hypertrophy.media.ClipPipeline
import com.forge.hypertrophy.ui.navigation.VideoCaptureRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CapturePhase {
    IDLE,
    PRE_ROLL,
    RECORDING,
    STOPPING,
    PROCESSING,
    SAVED,
}

data class VideoCaptureUiState(
    val label: String = "",
    val preRollEnabled: Boolean = true,
    val phase: CapturePhase = CapturePhase.IDLE,
    val countdownLeft: Int = 0,
    val processingProgress: Float = 0f,
    /** Where the camera writes while recording. */
    val rawPath: String? = null,
    val savedMediaId: Long? = null,
    val failed: Boolean = false,
)

sealed interface VideoCaptureEvent {
    /** Tap on the big button, a volume key, or a shutter remote. */
    data object Trigger : VideoCaptureEvent
    data class HardwareKey(val keyCode: Int) : VideoCaptureEvent
    data object TogglePreRoll : VideoCaptureEvent
    data object CancelPreRoll : VideoCaptureEvent
    data class RecordingFinished(val path: String) : VideoCaptureEvent
    data object RecordingFailed : VideoCaptureEvent
    data object DismissFailure : VideoCaptureEvent
}

@HiltViewModel
class VideoCaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val files: MediaFiles,
    private val labels: SetLabelResolver,
    private val pipeline: ClipPipeline,
    private val elapsed: ElapsedRealtimeClock,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<VideoCaptureRoute>()
    private val _uiState = MutableStateFlow(VideoCaptureUiState())
    val uiState: StateFlow<VideoCaptureUiState> = _uiState.asStateFlow()
    private val gate = HandsFreeGate()
    private var countdown: Job? = null

    init {
        viewModelScope.launch {
            val label = route.setEntryId?.let { labels.forSet(it) } ?: labels.forExercise(route.exerciseId) ?: ""
            _uiState.update { it.copy(label = label) }
        }
    }

    fun onEvent(event: VideoCaptureEvent) {
        when (event) {
            VideoCaptureEvent.Trigger -> trigger()
            is VideoCaptureEvent.HardwareKey -> {
                if (CaptureKeys.isCaptureKey(event.keyCode) && gate.accept(elapsed.elapsedRealtime())) trigger()
            }
            VideoCaptureEvent.TogglePreRoll -> _uiState.update { it.copy(preRollEnabled = !it.preRollEnabled) }
            VideoCaptureEvent.CancelPreRoll -> cancelPreRoll()
            is VideoCaptureEvent.RecordingFinished -> finish(File(event.path))
            VideoCaptureEvent.RecordingFailed -> {
                _uiState.value.rawPath?.let { File(it).delete() }
                _uiState.update { it.copy(phase = CapturePhase.IDLE, rawPath = null, failed = true) }
            }
            VideoCaptureEvent.DismissFailure -> _uiState.update { it.copy(failed = false) }
        }
    }

    private fun trigger() {
        when (_uiState.value.phase) {
            CapturePhase.IDLE, CapturePhase.SAVED -> if (_uiState.value.preRollEnabled) startPreRoll() else startRecording()
            CapturePhase.PRE_ROLL -> cancelPreRoll()
            CapturePhase.RECORDING -> _uiState.update { it.copy(phase = CapturePhase.STOPPING) }
            CapturePhase.STOPPING, CapturePhase.PROCESSING -> Unit
        }
    }

    private fun startPreRoll() {
        val timer = Countdown(PRE_ROLL_SECONDS, elapsed.elapsedRealtime())
        _uiState.update { it.copy(phase = CapturePhase.PRE_ROLL, countdownLeft = PRE_ROLL_SECONDS, savedMediaId = null) }
        countdown = viewModelScope.launch {
            while (true) {
                val left = timer.secondsLeft(elapsed.elapsedRealtime())
                _uiState.update { it.copy(countdownLeft = left) }
                if (left == 0) break
                delay(TICK_MS)
            }
            startRecording()
        }
    }

    private fun cancelPreRoll() {
        countdown?.cancel()
        countdown = null
        if (_uiState.value.phase == CapturePhase.PRE_ROLL) {
            _uiState.update { it.copy(phase = CapturePhase.IDLE, countdownLeft = 0) }
        }
    }

    private fun startRecording() {
        val raw = files.newRawVideoFile(clock.instant())
        _uiState.update {
            it.copy(phase = CapturePhase.RECORDING, countdownLeft = 0, rawPath = raw.path, savedMediaId = null)
        }
    }

    private fun finish(raw: File) {
        _uiState.update { it.copy(phase = CapturePhase.PROCESSING, processingProgress = 0f) }
        viewModelScope.launch {
            runCatching {
                pipeline.finish(raw, route.exerciseId, route.setEntryId) { progress ->
                    _uiState.update { it.copy(processingProgress = progress) }
                }
            }.onSuccess { id ->
                _uiState.update { it.copy(phase = CapturePhase.SAVED, rawPath = null, savedMediaId = id, processingProgress = 1f) }
            }.onFailure {
                raw.delete()
                _uiState.update { it.copy(phase = CapturePhase.IDLE, rawPath = null, failed = true) }
            }
        }
    }

    private companion object {
        const val TICK_MS = 100L
    }
}
