package com.forge.hypertrophy.di

import com.forge.hypertrophy.data.transfer.AssetSampleProgramProvider
import com.forge.hypertrophy.data.transfer.SampleProgramProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SampleProgramModule {
    @Binds
    @Singleton
    abstract fun bindSampleProgramProvider(
        impl: AssetSampleProgramProvider,
    ): SampleProgramProvider
}
