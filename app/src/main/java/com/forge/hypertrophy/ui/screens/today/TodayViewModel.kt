package com.forge.hypertrophy.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.ScheduleCursorRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.schedule.ScheduleLoader
import com.forge.hypertrophy.data.schedule.ScheduleReconciler
import com.forge.hypertrophy.domain.engine.ReadinessCheck
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.WorkoutPlan
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.usecase.GetTodaysWorkoutUseCase
import com.forge.hypertrophy.domain.usecase.StartWorkoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayUiState(
    val dayLabel: String? = null,
    val scheduledDayId: Long? = null,
    val isRestDay: Boolean = false,
    val isInProgress: Boolean = false,
    val completedToday: Boolean = false,
    val activeSessionId: Long? = null,
    val sessionToOpen: Long? = null,
)

sealed interface TodayEvent {
    data object StartWorkout : TodayEvent
    data object OpenedSession : TodayEvent
    data object Refresh : TodayEvent
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val programs: ProgramRepository,
    private val routines: RoutineRepository,
    private val sessions: SessionRepository,
    private val preferences: TrainingPreferencesRepository,
    private val cursor: ScheduleCursorRepository,
    private val clock: Clock,
    private val startWorkout: StartWorkoutUseCase,
    private val reconciler: ScheduleReconciler,
) : ViewModel() {
    private val loader = ScheduleLoader(programs, routines, sessions, preferences, cursor)
    private val getToday = GetTodaysWorkoutUseCase(clock)
    private var startInFlight = false

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessions.observeInProgress().collect { refresh(it) }
        }
    }

    fun onEvent(event: TodayEvent) {
        when (event) {
            TodayEvent.StartWorkout -> {
                val current = _uiState.value
                val dayId = current.scheduledDayId ?: return
                if (startInFlight || current.isRestDay || current.isInProgress || current.sessionToOpen != null) {
                    return
                }
                startInFlight = true
                viewModelScope.launch {
                    val id = try {
                        startWorkout.execute(dayId)
                    } catch (_: IllegalArgumentException) {
                        startInFlight = false
                        return@launch
                    }
                    _uiState.update { it.copy(sessionToOpen = id) }
                }
            }
            TodayEvent.OpenedSession -> {
                startInFlight = false
                _uiState.update { it.copy(sessionToOpen = null) }
            }
            TodayEvent.Refresh -> viewModelScope.launch {
                refresh(sessions.observeInProgress().first())
            }
        }
    }

    private suspend fun refresh(inProgress: List<WorkoutSessionEntity>) {
        runCatching { reconciler.reconcile() }
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val openToday = inProgress.openGymToday(today)
        val program = programs.observeActive().first()
        val loaded = program?.let { loader.load(it) }
        val plan = loaded?.let {
            planFor(
                it.snapshot,
                openToday?.readinessSleep,
                openToday?.readinessSoreness,
                openToday?.readinessEnergy,
            )
        }
        val completedToday = today in (loaded?.snapshot?.explicitCompletions ?: emptySet()) ||
            today in (loaded?.snapshot?.autoCompletedRests ?: emptySet())
        _uiState.update { current ->
            TodayUiState(
                dayLabel = plan?.day?.label,
                scheduledDayId = plan?.day?.id,
                isRestDay = plan?.day?.isRest == true,
                isInProgress = openToday != null,
                completedToday = openToday == null && completedToday,
                activeSessionId = openToday?.id,
                sessionToOpen = current.sessionToOpen,
            )
        }
    }

    /**
     * Rest between exercises comes from settings. Readiness is whatever was
     * saved on the open session. A short day uses half of the full estimate,
     * which is the same budget the workout uses when time is short.
     */
    private suspend fun planFor(
        snapshot: ScheduleSnapshot,
        sleep: Int?,
        soreness: Int?,
        energy: Int?,
    ): WorkoutPlan? {
        val transition = preferences.transitionRestSeconds.first()
        val full = getToday.today(
            snapshot,
            ReadinessCheck(null, null, null),
            transition,
            budgetSeconds = 1,
        )
        if (sleep == null || soreness == null || energy == null) return full
        val budget = ((full?.estimatedSeconds ?: 0) / 2).coerceAtLeast(1)
        return getToday.today(snapshot, ReadinessCheck(sleep, soreness, energy), transition, budget)
    }
}

internal fun List<WorkoutSessionEntity>.openGymToday(today: LocalDate): WorkoutSessionEntity? =
    firstOrNull { it.kind == SessionKind.GYM && it.date == today }
