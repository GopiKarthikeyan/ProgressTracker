package com.forge.hypertrophy.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.ScheduleCursorRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.data.schedule.ScheduleLoader
import com.forge.hypertrophy.domain.engine.ReadinessCheck
import com.forge.hypertrophy.domain.usecase.GetTodaysWorkoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TodayUiState(
    val dayLabel: String? = null,
    val isInProgress: Boolean = false,
    val activeSessionId: Long? = null,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val programs: ProgramRepository,
    private val routines: RoutineRepository,
    private val sessions: SessionRepository,
    private val preferences: TrainingPreferencesRepository,
    private val cursor: ScheduleCursorRepository,
    private val clock: Clock,
) : ViewModel() {
    private val loader = ScheduleLoader(programs, routines, sessions, preferences, cursor)
    private val getToday = GetTodaysWorkoutUseCase(clock)

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessions.observeInProgress().collect { inProgress ->
                val program = programs.observeActive().first()
                val day = if (program != null) {
                    val loaded = loader.load(program)
                    loaded?.let {
                        getToday.today(
                            it.snapshot,
                            ReadinessCheck(null, null, null),
                            60,
                            3600
                        )?.day?.label
                    }
                } else null
                
                _uiState.value = TodayUiState(
                    dayLabel = day,
                    isInProgress = inProgress.isNotEmpty(),
                    activeSessionId = inProgress.firstOrNull()?.id
                )
            }
        }
    }
}
