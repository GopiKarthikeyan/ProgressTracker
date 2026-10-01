package com.forge.hypertrophy.ui.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.MediaItemEntity
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.MediaRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.domain.media.setLabel
import com.forge.hypertrophy.domain.model.MediaType
import com.forge.hypertrophy.domain.model.PhysiqueMilestone
import com.forge.hypertrophy.domain.model.Pose
import com.forge.hypertrophy.domain.usecase.GetPhysiqueMilestonesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryExercise(val id: Long, val name: String)

data class GalleryClip(
    val id: Long,
    val label: String,
    val capturedOn: LocalDate?,
    val path: String,
    val setEntryId: Long?,
)

data class GallerySet(val id: Long, val label: String)

data class GalleryPhoto(
    val id: Long,
    val pose: Pose,
    val capturedOn: LocalDate?,
    val path: String,
)

data class GalleryUiState(
    val exercises: List<GalleryExercise> = emptyList(),
    val selectedExerciseId: Long? = null,
    val clips: List<GalleryClip> = emptyList(),
    /** Recent sets for the selected exercise, so a new clip can be attached to one. */
    val recentSets: List<GallerySet> = emptyList(),
    val compareIds: List<Long> = emptyList(),
    val photos: List<GalleryPhoto> = emptyList(),
    val milestones: List<PhysiqueMilestone> = emptyList(),
    val sliderPose: Pose = Pose.FRONT,
    val before: GalleryPhoto? = null,
    val after: GalleryPhoto? = null,
    val beforeDate: LocalDate? = null,
)

sealed interface GalleryEvent {
    data class SelectExercise(val exerciseId: Long?) : GalleryEvent
    data class ToggleCompare(val mediaId: Long) : GalleryEvent
    data class Delete(val mediaId: Long) : GalleryEvent
    data class SelectPose(val pose: Pose) : GalleryEvent
    data class SelectBefore(val date: LocalDate?) : GalleryEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val media: MediaRepository,
    private val exercises: ExerciseRepository,
    private val sessions: SessionRepository,
    clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()
    private val selected = MutableStateFlow<Long?>(null)
    private val milestonesUseCase = GetPhysiqueMilestonesUseCase(clock)
    private val zone = clock.zone
    private var names: Map<Long, String> = emptyMap()

    init {
        viewModelScope.launch {
            media.reconcile()
        }
        viewModelScope.launch {
            exercises.observeActive().collect { rows ->
                names = rows.associate { it.id to it.name }
                _uiState.update { it.copy(exercises = rows.map { row -> GalleryExercise(row.id, row.name) }) }
            }
        }
        viewModelScope.launch {
            combine(
                selected.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else media.observeForExercise(id) },
                selected,
            ) { items, id -> items to id }.collect { (items, id) ->
                val sets = if (id == null) emptyList() else recentSets(id)
                _uiState.update {
                    it.copy(
                        selectedExerciseId = id,
                        clips = items.filter { item -> item.type == MediaType.VIDEO }.map(::clipRow),
                        recentSets = sets,
                        compareIds = it.compareIds.filter { chosen -> items.any { item -> item.id == chosen } },
                    )
                }
            }
        }
        viewModelScope.launch {
            media.observePhotos().collect { rows ->
                val photos = rows.mapNotNull(::photoRow)
                val start = sessions.earliestCompletedDate() ?: photos.mapNotNull { it.capturedOn }.minOrNull()
                val dates = photos.mapNotNull { it.capturedOn }.distinct()
                val milestones = if (start == null) emptyList() else milestonesUseCase.milestones(start, dates)
                _uiState.update { state ->
                    state.copy(photos = photos, milestones = milestones).withSlider()
                }
            }
        }
    }

    fun onEvent(event: GalleryEvent) {
        when (event) {
            is GalleryEvent.SelectExercise -> selected.value = event.exerciseId
            is GalleryEvent.ToggleCompare -> _uiState.update { state ->
                val chosen = state.compareIds
                val next = when {
                    event.mediaId in chosen -> chosen - event.mediaId
                    chosen.size >= 2 -> chosen.drop(1) + event.mediaId
                    else -> chosen + event.mediaId
                }
                state.copy(compareIds = next)
            }
            is GalleryEvent.Delete -> viewModelScope.launch { media.delete(event.mediaId) }
            is GalleryEvent.SelectPose -> _uiState.update { it.copy(sliderPose = event.pose).withSlider() }
            is GalleryEvent.SelectBefore -> _uiState.update { it.copy(beforeDate = event.date).withSlider() }
        }
    }

    private suspend fun recentSets(exerciseId: Long): List<GallerySet> {
        val name = names[exerciseId] ?: exercises.get(exerciseId)?.name ?: return emptyList()
        return sessions.recentSetsForExercise(exerciseId, RECENT_SETS).map { set ->
            GallerySet(set.id, setLabel(name, set.weightKg, set.reps, set.holdSec))
        }
    }

    private fun clipRow(item: MediaItemEntity): GalleryClip = GalleryClip(
        id = item.id,
        label = item.label ?: item.exerciseId?.let { names[it] } ?: "",
        capturedOn = item.capturedAt?.atZone(zone)?.toLocalDate(),
        path = media.file(item).path,
        setEntryId = item.setEntryId,
    )

    private fun photoRow(item: MediaItemEntity): GalleryPhoto? {
        val pose = item.pose ?: return null
        return GalleryPhoto(
            id = item.id,
            pose = pose,
            capturedOn = item.capturedAt?.atZone(zone)?.toLocalDate(),
            path = media.file(item).path,
        )
    }

    private fun GalleryUiState.withSlider(): GalleryUiState {
        val forPose = photos.filter { it.pose == sliderPose }
        val after = forPose.lastOrNull()
        val before = beforeDate?.let { date -> forPose.lastOrNull { it.capturedOn == date } }
            ?: forPose.firstOrNull()
        return copy(before = before, after = after)
    }

    private companion object {
        const val RECENT_SETS = 10
    }
}
