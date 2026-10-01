package com.forge.hypertrophy.di

import com.forge.hypertrophy.data.db.AppDatabase
import com.forge.hypertrophy.data.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.data.storage.ContentResolverProgramDocumentStore
import com.forge.hypertrophy.data.storage.ProgramDocumentStore
import com.forge.hypertrophy.data.transfer.ProgramExportPreferences
import com.forge.hypertrophy.data.transfer.ProgramExporter
import com.forge.hypertrophy.data.transfer.ProgramImporter
import com.forge.hypertrophy.data.transfer.ProgramJsonDefaults
import com.forge.hypertrophy.data.transfer.TrainingPreferencesImportDefaults
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Module
@InstallIn(SingletonComponent::class)
abstract class TransferModule {
    @Binds
    @Singleton
    abstract fun bindProgramDocumentStore(
        impl: ContentResolverProgramDocumentStore,
    ): ProgramDocumentStore

    companion object {
        private const val EXPORT_BAR_WEIGHT_KG = 20.0

        @Provides
        @Singleton
        fun provideProgramImporter(
            database: AppDatabase,
            clock: Clock,
            preferences: TrainingPreferencesRepository,
        ): ProgramImporter = ProgramImporter(
            database = database,
            clock = clock,
            preferences = TrainingPreferencesImportDefaults(preferences),
        )

        @Provides
        @Singleton
        fun provideProgramExporter(
            database: AppDatabase,
            preferences: TrainingPreferencesRepository,
        ): ProgramExporter = ProgramExporter(
            database = database,
            preferences = ProgramExportPreferences {
                ProgramJsonDefaults(
                    transitionRestSec = preferences.transitionRestSeconds.first(),
                    barWeightKg = EXPORT_BAR_WEIGHT_KG,
                    plateInventoryKg = preferences.plateInventoryKg.first(),
                )
            },
        )
    }
}
