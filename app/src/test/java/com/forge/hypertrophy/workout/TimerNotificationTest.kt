package com.forge.hypertrophy.workout

import android.app.Application
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import com.forge.hypertrophy.domain.workout.TimerPhase
import com.forge.hypertrophy.domain.workout.TimerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class TimerNotificationTest {
    @Test
    fun notificationCountsDownAndExposesTheThreeActions() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notification = workoutTimerNotification(
            context,
            TimerSnapshot(phase = TimerPhase.COUNTDOWN, remainingMillis = 45_000, cue = "rest"),
            whenMillis = 1_000,
        )
        assertEquals(3, notification.actions.size)
        assertEquals("+30s", notification.actions[0].title.toString())
        assertEquals("Skip", notification.actions[1].title.toString())
        assertEquals("Complete Set", notification.actions[2].title.toString())
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.extras.getBoolean("android.chronometerCountDown"))
    }
}
