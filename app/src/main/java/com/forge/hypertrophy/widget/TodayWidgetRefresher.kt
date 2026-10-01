package com.forge.hypertrophy.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Asks every placed widget to redraw. Called when a session completes and after the schedule catches up. */
fun interface TodayWidgetRefresher {
    suspend fun refresh()
}

@Singleton
class GlanceTodayWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) : TodayWidgetRefresher {
    override suspend fun refresh() {
        TodayWidget().updateAll(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds
    abstract fun bindTodayWidgetRefresher(impl: GlanceTodayWidgetRefresher): TodayWidgetRefresher
}
