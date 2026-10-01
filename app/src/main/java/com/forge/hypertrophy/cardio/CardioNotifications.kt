package com.forge.hypertrophy.cardio

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.forge.hypertrophy.MainActivity
import com.forge.hypertrophy.R

const val CARDIO_CHANNEL_ID = "cardio_tracking"

fun cardioNotification(context: Context, distanceM: Double, paused: Boolean): Notification {
    val kilometres = distanceM / 1_000.0
    val status = if (paused) {
        context.getString(R.string.cardio_notification_paused)
    } else {
        context.getString(R.string.cardio_notification_running)
    }
    return NotificationCompat.Builder(context, CARDIO_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle(context.getString(R.string.cardio_notification_title))
        .setContentText(context.getString(R.string.cardio_notification_body, kilometres, status))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openApp(context))
        .addAction(0, context.getString(R.string.cardio_stop), stopIntent(context))
        .build()
}

private fun stopIntent(context: Context): PendingIntent {
    val intent = Intent(context, CardioTrackingService::class.java).setAction(CardioTrackingService.ACTION_STOP)
    return PendingIntent.getService(
        context,
        1,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun openApp(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
    return PendingIntent.getActivity(
        context,
        2,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
