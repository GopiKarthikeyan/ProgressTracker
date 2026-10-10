package com.forge.hypertrophy.ui.screens.dashboard

import com.forge.hypertrophy.domain.model.CardioActivity
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SessionKind
import java.time.LocalDate

data class TodayCard(
    val label: String,
    val estimatedSeconds: Int,
    val shortEstimatedSeconds: Int,
    val mode: ScheduleMode,
    val isRest: Boolean,
    val takeRestEnabled: Boolean,
    val skipEnabled: Boolean,
    val swapEnabled: Boolean,
    val startEnabled: Boolean,
    val activeSessionId: Long? = null,
)

data class HeatmapCell(
    val date: LocalDate,
    val kind: SessionKind?,
)

data class ExerciseRecordUi(
    val name: String,
    val bestE1rmKg: Double?,
    val bestWeightKg: Double?,
    val bestReps: Int?,
)

data class SkillLadderUi(
    val name: String,
    val tierNames: List<String>,
    val currentTier: Int,
    val stage: Int,
    val maxHoldSec: Int?,
)

data class ChartPoint(
    val date: LocalDate,
    val value: Double,
)

data class ChartSeries(
    val samples: List<ChartPoint>,
    val average: List<ChartPoint>,
)

data class MuscleVolumeUi(
    val muscle: String,
    val sets: Double,
)

data class CardioActivityUi(
    val activity: CardioActivity,
    val sessions: Int,
    val durationSec: Int,
    val distanceM: Double,
)

data class WeeklyCardioUi(
    val sessions: Int = 0,
    val durationSec: Int = 0,
    val distanceM: Double = 0.0,
    val byActivity: List<CardioActivityUi> = emptyList(),
)

data class DeloadStatus(
    val startedOn: LocalDate?,
    val rotationComplete: Boolean,
)

enum class DashboardNotice {
    WORKOUT_STARTED,
    ALREADY_IN_PROGRESS,
    WEIGH_IN_SAVED,
    WEIGH_IN_INVALID,
    SCHEDULE_REJECTED,
}

data class DashboardUiState(
    val today: TodayCard? = null,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val heatmap: List<HeatmapCell> = emptyList(),
    val records: List<ExerciseRecordUi> = emptyList(),
    val skills: List<SkillLadderUi> = emptyList(),
    val weight: ChartSeries = ChartSeries(emptyList(), emptyList()),
    val bodyFat: ChartSeries = ChartSeries(emptyList(), emptyList()),
    val volume: List<MuscleVolumeUi> = emptyList(),
    val cardioWeek: WeeklyCardioUi = WeeklyCardioUi(),
    val stalls: List<String> = emptyList(),
    val deload: DeloadStatus? = null,
    val weightDraft: String = "",
    val bodyFatDraft: String = "",
    val notice: DashboardNotice? = null,
    val activeSessionId: Long? = null,
)

sealed interface DashboardEvent {
    data object TakeRestNow : DashboardEvent
    data object SkipToNext : DashboardEvent
    data object SwapWithTomorrow : DashboardEvent
    data class Start(val shortOnTime: Boolean) : DashboardEvent
    data class WeightDraft(val text: String) : DashboardEvent
    data class BodyFatDraft(val text: String) : DashboardEvent
    data object SaveWeighIn : DashboardEvent
    data object DismissNotice : DashboardEvent
}
