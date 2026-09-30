package com.forge.hypertrophy.di

import android.content.Context
import androidx.room.Room
import com.forge.hypertrophy.data.dao.BiometricsDao
import com.forge.hypertrophy.data.dao.CardioDao
import com.forge.hypertrophy.data.dao.ExerciseDao
import com.forge.hypertrophy.data.dao.GearDao
import com.forge.hypertrophy.data.dao.MediaDao
import com.forge.hypertrophy.data.dao.ProgramDao
import com.forge.hypertrophy.data.dao.RoutineDao
import com.forge.hypertrophy.data.dao.SessionDao
import com.forge.hypertrophy.data.dao.SkillDao
import com.forge.hypertrophy.data.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        DATABASE_NAME,
    ).build()

    @Provides
    fun provideProgramDao(database: AppDatabase): ProgramDao = database.programDao()

    @Provides
    fun provideExerciseDao(database: AppDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun provideSkillDao(database: AppDatabase): SkillDao = database.skillDao()

    @Provides
    fun provideRoutineDao(database: AppDatabase): RoutineDao = database.routineDao()

    @Provides
    fun provideSessionDao(database: AppDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideCardioDao(database: AppDatabase): CardioDao = database.cardioDao()

    @Provides
    fun provideMediaDao(database: AppDatabase): MediaDao = database.mediaDao()

    @Provides
    fun provideBiometricsDao(database: AppDatabase): BiometricsDao = database.biometricsDao()

    @Provides
    fun provideGearDao(database: AppDatabase): GearDao = database.gearDao()

    private const val DATABASE_NAME = "hypertrophy.db"
}
