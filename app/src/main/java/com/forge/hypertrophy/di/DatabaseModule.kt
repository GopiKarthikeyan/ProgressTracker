package com.forge.hypertrophy.di

import android.content.Context
import androidx.room.Room
import com.forge.hypertrophy.data.dao.BaselineDao
import com.forge.hypertrophy.data.dao.BiometricsDao
import com.forge.hypertrophy.data.dao.CardioDao
import com.forge.hypertrophy.data.dao.ExerciseDao
import com.forge.hypertrophy.data.dao.GearDao
import com.forge.hypertrophy.data.dao.MediaDao
import com.forge.hypertrophy.data.dao.ProgramDao
import com.forge.hypertrophy.data.dao.RoutineDao
import com.forge.hypertrophy.data.dao.SessionDao
import com.forge.hypertrophy.data.dao.SkillDao
import com.forge.hypertrophy.data.backup.SNAPSHOT_DIR
import com.forge.hypertrophy.data.backup.PreMigrationSnapshots
import com.forge.hypertrophy.data.db.AppDatabase
import com.forge.hypertrophy.data.db.DATABASE_NAME
import com.forge.hypertrophy.data.db.DatabaseMigrations
import com.forge.hypertrophy.data.db.SCHEMA_VERSION
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        clock: Clock,
    ): AppDatabase {
        PreMigrationSnapshots(
            databaseFile = context.getDatabasePath(DATABASE_NAME),
            snapshotsDir = File(context.filesDir, SNAPSHOT_DIR),
            currentVersion = SCHEMA_VERSION,
            clock = clock,
        ).captureIfStale()
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(*DatabaseMigrations.ALL).build()
    }

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

    @Provides
    fun provideBaselineDao(database: AppDatabase): BaselineDao = database.baselineDao()
}
