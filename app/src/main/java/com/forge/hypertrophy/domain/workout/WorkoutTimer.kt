package com.forge.hypertrophy.domain.workout

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class TimerCommand {
    SKIP,
    COMPLETE_SET,
}

/** What the foreground service displays and what the workout ViewModel drives. */
interface WorkoutTimer {
    val snapshot: StateFlow<TimerSnapshot>

    val commands: Flow<TimerCommand>

    suspend fun start(spec: TimerSpec)

    suspend fun adjust(deltaMillis: Long)

    suspend fun stop()

    fun refresh(nowElapsedRealtime: Long)
}
