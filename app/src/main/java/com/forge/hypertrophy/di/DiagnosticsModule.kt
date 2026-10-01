package com.forge.hypertrophy.di

import android.content.Context
import com.forge.hypertrophy.data.diagnostics.Breadcrumbs
import com.forge.hypertrophy.data.diagnostics.CrashLogStore
import com.forge.hypertrophy.data.diagnostics.DiagnosticsRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsModule {
    @Provides
    @Singleton
    fun provideBreadcrumbs(): Breadcrumbs = DiagnosticsRegistry.breadcrumbs

    @Provides
    @Singleton
    fun provideCrashLogStore(
        @ApplicationContext context: Context,
        clock: Clock,
    ): CrashLogStore = DiagnosticsRegistry.store(context.filesDir, clock)
}
