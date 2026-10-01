package com.forge.hypertrophy.cardio

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface CardioServiceController {
    fun start()
    fun stop()
}

@Singleton
class ContextCardioServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : CardioServiceController {
    override fun start() {
        val intent = Intent(context, CardioTrackingService::class.java)
        context.startForegroundService(intent)
    }

    override fun stop() {
        val intent = Intent(context, CardioTrackingService::class.java).setAction(CardioTrackingService.ACTION_STOP)
        context.startForegroundService(intent)
    }
}
