package com.forge.hypertrophy.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.dao.CompletedSetRow
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.domain.engine.DatedLift
import com.forge.hypertrophy.domain.engine.DatedVolumeSet
import com.forge.hypertrophy.domain.engine.LiftSample
import com.forge.hypertrophy.domain.engine.MuscleWeekComparison
import com.forge.hypertrophy.domain.engine.StageAdvancement
import com.forge.hypertrophy.domain.engine.VolumeSet
import com.forge.hypertrophy.domain.engine.WeeklyReviewCalculator
import com.forge.hypertrophy.domain.engine.reviewWeekStart
import com.forge.hypertrophy.domain.model.SessionKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewPr(
    val exerciseName: String,
    val date: LocalDate,
    val e1rmKg: Double,
    val weightKg: Double,
    val reps: Int,
)

data class ReviewAdvancement(
    val skillName: String,
    val date: LocalDate,
    val fromTierName: String,
    val fromStage: Int,
    val toTierName: String,
    val toStage: Int,
)

data class WeeklyReviewUiState(
    val weekStart: LocalDate? = null,
    val weekEnd: LocalDate? = null,
    val isCurrentWeek: Boolean = true,
    val sessionsCompleted: Int = 0,
    val previousSessionsCompleted: Int = 0,
    val prs: List<ReviewPr> = emptyList(),
    val advancements: List<ReviewAdvancement> = emptyList(),
    val volume: List<MuscleWeekComparison> = emptyList(),
    val loaded: Boolean = false,
)

sealed interface WeeklyReviewEvent {
    data object PreviousWeek : WeeklyReviewEvent
    data object NextWeek : WeeklyReviewEvent
    data object Refresh : WeeklyReviewEvent
}

@HiltViewModel
class WeeklyReviewViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val exercises: ExerciseRepository,
    private val skills: SkillRepository,
    private val clock: Clock,
) : ViewModel() {
    private val calculator = WeeklyReviewCalculator()
    private val _uiState = MutableStateFlow(WeeklyReviewUiState())
    val uiState: StateFlow<WeeklyReviewUiState> = _uiState.asStateFlow()
    private val today = clock.instant().atZone(clock.zone).toLocalDate()
    private var weekStart: LocalDate = reviewWeekStart(today)

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: WeeklyReviewEvent) {
        when (event) {
            WeeklyReviewEvent.PreviousWeek -> weekStart = weekStart.minusWeeks(1)
            WeeklyReviewEvent.NextWeek -> {
                val next = weekStart.plusWeeks(1)
                if (next.isAfter(today)) return
                weekStart = next
            }
            WeeklyReviewEvent.Refresh -> Unit
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val exerciseById = exercises.all().associateBy { it.id }
        val sets = sessions.completedSets()
        val sessionDates = sessions.completedDays().filter { it.kind != SessionKind.REST }.map { it.date }
        val lifts = sets.map { row ->
            DatedLift(row.sessionDate, LiftSample(exerciseId(row), row.slotId ?: 0L, row.weightKg, row.reps))
        }
        val volumeSets = sets.mapNotNull { row ->
            val exercise = exerciseById[exerciseId(row)] ?: return@mapNotNull null
            DatedVolumeSet(row.sessionDate, VolumeSet(row.setType, exercise.primaryMuscleGroups, exercise.secondaryMuscleGroups))
        }
        val start = reviewWeekStart(weekStart)
        val events = skills.stageEventsBetween(start, start.plusDays(6)).map { event ->
            StageAdvancement(event.skillId, event.date, event.fromTier, event.fromStage, event.toTier, event.toStage)
        }
        val review = calculator.review(start, sessionDates, lifts, volumeSets, events)
        val stepsBySkill = skills.allSteps().groupBy { it.skillId }
        _uiState.update {
            it.copy(
                weekStart = review.weekStart,
                weekEnd = review.weekEnd,
                isCurrentWeek = review.weekStart == reviewWeekStart(today),
                sessionsCompleted = review.sessionsCompleted,
                previousSessionsCompleted = review.previousSessionsCompleted,
                prs = review.prs.mapNotNull { pr ->
                    val name = exerciseById[pr.exerciseId]?.name ?: return@mapNotNull null
                    ReviewPr(name, pr.date, pr.e1rmKg, pr.weightKg, pr.reps)
                },
                advancements = review.stageAdvancements.mapNotNull { advancement ->
                    val skill = skills.get(advancement.skillId) ?: return@mapNotNull null
                    val ladder = stepsBySkill[advancement.skillId].orEmpty().sortedBy { step -> step.sortOrder }
                    ReviewAdvancement(
                        skillName = skill.name,
                        date = advancement.date,
                        fromTierName = ladder.getOrNull(advancement.fromTier)?.name ?: "",
                        fromStage = advancement.fromStage,
                        toTierName = ladder.getOrNull(advancement.toTier)?.name ?: "",
                        toStage = advancement.toStage,
                    )
                },
                volume = review.volume,
                loaded = true,
            )
        }
    }

    private fun exerciseId(row: CompletedSetRow): Long = row.chosenAlternativeExerciseId ?: row.prescriptionSnapshot.exerciseId
}
