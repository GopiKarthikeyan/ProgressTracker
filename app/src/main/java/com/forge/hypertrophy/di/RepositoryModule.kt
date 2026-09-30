package com.forge.hypertrophy.di

import com.forge.hypertrophy.data.repository.DataStoreOnboardingRepository
import com.forge.hypertrophy.data.repository.OnboardingRepository
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
}
