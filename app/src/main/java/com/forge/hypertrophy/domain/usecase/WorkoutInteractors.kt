package com.forge.hypertrophy.domain.usecase

data class WorkoutInteractors(
    val start: StartWorkoutUseCase,
    val logSet: LogSetUseCase,
    val complete: CompleteWorkoutUseCase,
)
