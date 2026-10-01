package com.forge.hypertrophy.di

import android.os.SystemClock
import com.forge.hypertrophy.domain.workout.ElapsedRealtimeClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideElapsedRealtime(): ElapsedRealtimeClock = ElapsedRealtimeClock { SystemClock.elapsedRealtime() }
}
