package com.forge.hypertrophy.workout

import com.forge.hypertrophy.domain.usecase.CompleteWorkoutUseCase
import com.forge.hypertrophy.domain.usecase.LogSetUseCase
import com.forge.hypertrophy.domain.usecase.StartWorkoutUseCase
import com.forge.hypertrophy.domain.usecase.WorkoutInteractors
import com.forge.hypertrophy.domain.workout.WorkoutTimer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutModule {
    @Binds
    abstract fun bindWorkoutTimer(impl: ForegroundWorkoutTimer): WorkoutTimer

    companion object {
        @Provides
        @JvmStatic
        fun provideWorkoutInteractors(
            start: StartWorkoutUseCase,
            logSet: LogSetUseCase,
            complete: CompleteWorkoutUseCase,
        ): WorkoutInteractors = WorkoutInteractors(start, logSet, complete)
    }
}
