package com.forge.hypertrophy.di

import android.content.Context
import com.forge.hypertrophy.data.media.MediaFiles
import com.forge.hypertrophy.data.repository.DataStoreMediaPreferencesRepository
import com.forge.hypertrophy.data.repository.MediaPreferencesRepository
import com.forge.hypertrophy.media.ClipDurations
import com.forge.hypertrophy.media.ClipProcessor
import com.forge.hypertrophy.media.RetrieverClipDurations
import com.forge.hypertrophy.media.TransformerClipProcessor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds
    @Singleton
    abstract fun bindMediaPreferences(impl: DataStoreMediaPreferencesRepository): MediaPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindClipProcessor(impl: TransformerClipProcessor): ClipProcessor

    @Binds
    @Singleton
    abstract fun bindClipDurations(impl: RetrieverClipDurations): ClipDurations

    companion object {
        @Provides
        @Singleton
        fun provideMediaFiles(@ApplicationContext context: Context): MediaFiles = MediaFiles(context.filesDir)
    }
}
