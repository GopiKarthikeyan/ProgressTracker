package com.forge.hypertrophy.workout

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.workout.ElapsedRealtimeClock
import com.forge.hypertrophy.domain.workout.TimerCommand
import com.forge.hypertrophy.domain.workout.TimerSnapshot
import com.forge.hypertrophy.domain.workout.TimerSpec
import com.forge.hypertrophy.domain.workout.WorkoutTimer
import com.forge.hypertrophy.domain.workout.projectTimer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ForegroundWorkoutTimer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: TrainingPreferencesRepository,
    private val clock: ElapsedRealtimeClock,
) : WorkoutTimer {
    private val spec = MutableStateFlow<TimerSpec?>(null)
    private val _snapshot = MutableStateFlow(TimerSnapshot.Idle)
    override val snapshot: StateFlow<TimerSnapshot> = _snapshot.asStateFlow()
    private val _commands = MutableSharedFlow<TimerCommand>(extraBufferCapacity = 8)
    override val commands: Flow<TimerCommand> = _commands.asSharedFlow()

    fun currentSpec(): TimerSpec? = spec.value

    val specs: StateFlow<TimerSpec?> = spec.asStateFlow()

    override suspend fun start(spec: TimerSpec) {
        this.spec.value = spec
        preferences.setActiveTimerEndElapsedRealtime(spec.persistedEnd())
        refresh(clock.elapsedRealtime())
        ContextCompat.startForegroundService(context, Intent(context, WorkoutTimerService::class.java))
    }

    override suspend fun adjust(deltaMillis: Long) {
        val current = spec.value ?: return
        val next = current.adjust(deltaMillis)
        spec.value = next
        preferences.setActiveTimerEndElapsedRealtime(next.persistedEnd())
        refresh(clock.elapsedRealtime())
    }

    override suspend fun stop() {
        spec.value = null
        preferences.setActiveTimerEndElapsedRealtime(null)
        _snapshot.value = TimerSnapshot.Idle
        context.startService(Intent(context, WorkoutTimerService::class.java).setAction(WorkoutTimerService.ACTION_STOP))
    }

    override fun refresh(nowElapsedRealtime: Long) {
        _snapshot.value = projectTimer(spec.value, nowElapsedRealtime)
    }

    fun emit(command: TimerCommand) {
        _commands.tryEmit(command)
    }
}
