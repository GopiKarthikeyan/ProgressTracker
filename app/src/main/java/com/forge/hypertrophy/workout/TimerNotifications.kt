package com.forge.hypertrophy.workout

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.forge.hypertrophy.MainActivity
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.workout.TimerPhase
import com.forge.hypertrophy.domain.workout.TimerSnapshot

const val WORKOUT_TIMER_CHANNEL_ID = "workout_timer"
private const val REQUEST_PLUS = 1
private const val REQUEST_SKIP = 2
private const val REQUEST_COMPLETE = 3
private const val REQUEST_CONTENT = 4

fun workoutTimerNotification(
    context: Context,
    snapshot: TimerSnapshot,
    whenMillis: Long,
): Notification {
    val countingDown = snapshot.phase == TimerPhase.COUNTDOWN || snapshot.phase == TimerPhase.WARNING
    return NotificationCompat.Builder(context, WORKOUT_TIMER_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(context.getString(R.string.workout_timer_title))
        .setContentText(snapshot.cue ?: context.getString(R.string.workout_timer_running))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setUsesChronometer(snapshot.phase != TimerPhase.IDLE && snapshot.phase != TimerPhase.FINISHED)
        .setChronometerCountDown(countingDown)
        .setWhen(whenMillis)
        .setContentIntent(activityIntent(context))
        .addAction(0, context.getString(R.string.workout_timer_plus), serviceIntent(context, WorkoutTimerService.ACTION_PLUS_30, REQUEST_PLUS))
        .addAction(0, context.getString(R.string.workout_timer_skip), serviceIntent(context, WorkoutTimerService.ACTION_SKIP, REQUEST_SKIP))
        .addAction(0, context.getString(R.string.workout_timer_complete), serviceIntent(context, WorkoutTimerService.ACTION_COMPLETE, REQUEST_COMPLETE))
        .build()
}

private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent {
    val intent = Intent(context, WorkoutTimerService::class.java).setAction(action)
    return PendingIntent.getService(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun activityIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
    return PendingIntent.getActivity(
        context,
        REQUEST_CONTENT,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
