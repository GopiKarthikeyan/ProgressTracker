package com.forge.hypertrophy.workout

import com.forge.hypertrophy.domain.workout.WorkoutTimer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutModule {
    @Binds
    abstract fun bindWorkoutTimer(impl: ForegroundWorkoutTimer): WorkoutTimer
}
