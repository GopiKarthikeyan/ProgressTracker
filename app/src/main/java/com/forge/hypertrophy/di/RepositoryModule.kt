package com.forge.hypertrophy.di

import com.forge.hypertrophy.data.repository.DataStoreOnboardingRepository
import com.forge.hypertrophy.data.repository.DataStoreTrainingPreferencesRepository
import com.forge.hypertrophy.data.repository.OnboardingRepository
import com.forge.hypertrophy.data.repository.RoomBiometricsRepository
import com.forge.hypertrophy.data.repository.RoomCardioRepository
import com.forge.hypertrophy.data.repository.RoomExerciseRepository
import com.forge.hypertrophy.data.repository.RoomGearRepository
import com.forge.hypertrophy.data.repository.RoomMediaRepository
import com.forge.hypertrophy.data.repository.RoomProgramRepository
import com.forge.hypertrophy.data.repository.RoomRoutineRepository
import com.forge.hypertrophy.data.repository.RoomSessionRepository
import com.forge.hypertrophy.data.repository.RoomSkillRepository
import com.forge.hypertrophy.data.repository.RoomWorkoutRepository
import com.forge.hypertrophy.data.repository.DataStoreScheduleCursorRepository
import com.forge.hypertrophy.data.repository.ScheduleCursorRepository
import com.forge.hypertrophy.data.repository.BaselineRepository
import com.forge.hypertrophy.data.repository.BiometricsRepository
import com.forge.hypertrophy.data.repository.RoomBaselineRepository
import com.forge.hypertrophy.data.repository.CardioRepository
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.GearRepository
import com.forge.hypertrophy.data.repository.MediaRepository
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.repository.WorkoutRepository
import com.forge.hypertrophy.data.weather.OpenMeteoWeatherRepository
import com.forge.hypertrophy.data.weather.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindOnboardingRepository(
        impl: DataStoreOnboardingRepository,
    ): OnboardingRepository

    @Binds
    @Singleton
    abstract fun bindProgramRepository(
        impl: RoomProgramRepository,
    ): ProgramRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(
        impl: RoomExerciseRepository,
    ): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindSkillRepository(
        impl: RoomSkillRepository,
    ): SkillRepository

    @Binds
    @Singleton
    abstract fun bindRoutineRepository(
        impl: RoomRoutineRepository,
    ): RoutineRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        impl: RoomSessionRepository,
    ): SessionRepository

    @Binds
    @Singleton
    abstract fun bindBaselineRepository(
        impl: RoomBaselineRepository,
    ): BaselineRepository

    @Binds
    @Singleton
    abstract fun bindCardioRepository(
        impl: RoomCardioRepository,
    ): CardioRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(
        impl: RoomMediaRepository,
    ): MediaRepository

    @Binds
    @Singleton
    abstract fun bindBiometricsRepository(
        impl: RoomBiometricsRepository,
    ): BiometricsRepository

    @Binds
    @Singleton
    abstract fun bindGearRepository(
        impl: RoomGearRepository,
    ): GearRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(
        impl: RoomWorkoutRepository,
    ): WorkoutRepository

    @Binds
    @Singleton
    abstract fun bindTrainingPreferencesRepository(
        impl: DataStoreTrainingPreferencesRepository,
    ): TrainingPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindScheduleCursorRepository(
        impl: DataStoreScheduleCursorRepository,
    ): ScheduleCursorRepository

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(
        impl: OpenMeteoWeatherRepository,
    ): WeatherRepository
}
