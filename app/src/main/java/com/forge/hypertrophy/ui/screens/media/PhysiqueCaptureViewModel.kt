package com.forge.hypertrophy.ui.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.MediaItemEntity
import com.forge.hypertrophy.data.media.MediaFiles
import com.forge.hypertrophy.data.repository.MediaRepository
import com.forge.hypertrophy.domain.media.Countdown
import com.forge.hypertrophy.domain.media.POSE_ORDER
import com.forge.hypertrophy.domain.media.SELF_TIMER_SECONDS
import com.forge.hypertrophy.domain.media.nextPose
import com.forge.hypertrophy.domain.model.MediaType
import com.forge.hypertrophy.domain.model.Pose
import com.forge.hypertrophy.domain.workout.ElapsedRealtimeClock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PhotoPhase {
    IDLE,
    COUNTDOWN,
    CAPTURING,
    COMPLETE,
}

data class PhysiqueCaptureUiState(
    val pose: Pose? = Pose.FRONT,
    val done: Set<Pose> = emptySet(),
    val phase: PhotoPhase = PhotoPhase.IDLE,
    val countdownLeft: Int = 0,
    /** Last photo for the current pose, drawn translucent over the preview. */
    val ghostPath: String? = null,
    /** Where the camera writes the next photo. */
    val targetPath: String? = null,
    val failed: Boolean = false,
)

sealed interface PhysiqueCaptureEvent {
    data object Trigger : PhysiqueCaptureEvent
    data object Cancel : PhysiqueCaptureEvent
    data class Captured(val path: String) : PhysiqueCaptureEvent
    data object CaptureFailed : PhysiqueCaptureEvent
    data object SkipPose : PhysiqueCaptureEvent
    data object Retake : PhysiqueCaptureEvent
    data object DismissFailure : PhysiqueCaptureEvent
}

@HiltViewModel
class PhysiqueCaptureViewModel @Inject constructor(
    private val files: MediaFiles,
    private val media: MediaRepository,
    private val elapsed: ElapsedRealtimeClock,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PhysiqueCaptureUiState())
    val uiState: StateFlow<PhysiqueCaptureUiState> = _uiState.asStateFlow()
    private var countdown: Job? = null

    init {
        viewModelScope.launch { loadGhost(Pose.FRONT) }
    }

    fun onEvent(event: PhysiqueCaptureEvent) {
        when (event) {
            PhysiqueCaptureEvent.Trigger -> trigger()
            PhysiqueCaptureEvent.Cancel -> cancel()
            is PhysiqueCaptureEvent.Captured -> viewModelScope.launch { store(File(event.path)) }
            PhysiqueCaptureEvent.CaptureFailed -> {
                _uiState.value.targetPath?.let { File(it).delete() }
                _uiState.update { it.copy(phase = PhotoPhase.IDLE, targetPath = null, failed = true) }
            }
            PhysiqueCaptureEvent.SkipPose -> viewModelScope.launch { advance() }
            PhysiqueCaptureEvent.Retake -> viewModelScope.launch { retake() }
            PhysiqueCaptureEvent.DismissFailure -> _uiState.update { it.copy(failed = false) }
        }
    }

    private fun trigger() {
        when (_uiState.value.phase) {
            PhotoPhase.IDLE -> startTimer()
            PhotoPhase.COUNTDOWN -> cancel()
            PhotoPhase.CAPTURING, PhotoPhase.COMPLETE -> Unit
        }
    }

    private fun startTimer() {
        val pose = _uiState.value.pose ?: return
        val timer = Countdown(SELF_TIMER_SECONDS, elapsed.elapsedRealtime())
        _uiState.update { it.copy(phase = PhotoPhase.COUNTDOWN, countdownLeft = SELF_TIMER_SECONDS) }
        countdown = viewModelScope.launch {
            while (true) {
                val left = timer.secondsLeft(elapsed.elapsedRealtime())
                _uiState.update { it.copy(countdownLeft = left) }
                if (left == 0) break
                delay(TICK_MS)
            }
            val target = files.newPhotoFile(pose, clock.instant())
            _uiState.update { it.copy(phase = PhotoPhase.CAPTURING, targetPath = target.path) }
        }
    }

    private fun cancel() {
        countdown?.cancel()
        countdown = null
        if (_uiState.value.phase == PhotoPhase.COUNTDOWN) {
            _uiState.update { it.copy(phase = PhotoPhase.IDLE, countdownLeft = 0) }
        }
    }

    private suspend fun store(file: File) {
        val pose = _uiState.value.pose ?: return
        replaceLatestPhoto(pose)
        media.insert(
            MediaItemEntity(
                type = MediaType.PHOTO,
                pose = pose,
                exerciseId = null,
                setEntryId = null,
                uri = files.relative(file),
                trimStartMs = null,
                trimEndMs = null,
                capturedAt = clock.instant(),
                label = null,
            ),
        )
        advance()
    }

    private suspend fun retake() {
        when (_uiState.value.phase) {
            PhotoPhase.COMPLETE -> {
                val pose = POSE_ORDER.lastOrNull { it in _uiState.value.done } ?: POSE_ORDER.first()
                _uiState.update {
                    it.copy(
                        pose = pose,
                        done = it.done - pose,
                        phase = PhotoPhase.IDLE,
                        targetPath = null,
                        countdownLeft = 0,
                        failed = false,
                    )
                }
                loadGhost(pose)
                startTimer()
            }
            PhotoPhase.IDLE -> {
                if (_uiState.value.pose == null) return
                startTimer()
            }
            else -> Unit
        }
    }

    private suspend fun replaceLatestPhoto(pose: Pose) {
        val previous = media.observePhotos().first().lastOrNull { it.pose == pose } ?: return
        media.delete(previous.id)
    }

    private suspend fun advance() {
        val current = _uiState.value.pose ?: return
        val done = _uiState.value.done + current
        val next = nextPose(done)
        _uiState.update {
            it.copy(
                pose = next,
                done = done,
                phase = if (next == null) PhotoPhase.COMPLETE else PhotoPhase.IDLE,
                targetPath = null,
                countdownLeft = 0,
                ghostPath = null,
            )
        }
        if (next != null) loadGhost(next)
    }

    private suspend fun loadGhost(pose: Pose) {
        val last = media.observePhotos().first().lastOrNull { it.pose == pose }
        val path = last?.let { media.file(it) }?.takeIf { it.isFile }?.path
        _uiState.update { if (it.pose == pose) it.copy(ghostPath = path) else it }
    }

    private companion object {
        const val TICK_MS = 100L
    }
}
