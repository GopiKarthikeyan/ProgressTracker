package com.forge.hypertrophy.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.workout.ElapsedRealtimeClock
import com.forge.hypertrophy.domain.workout.TimerCommand
import com.forge.hypertrophy.domain.workout.TimerKind
import com.forge.hypertrophy.domain.workout.TimerPhase
import com.forge.hypertrophy.domain.workout.TimerSpec
import com.forge.hypertrophy.domain.workout.projectTimer
import com.forge.hypertrophy.domain.workout.wakeLockTimeoutMillis
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground workout clock. The end timestamp lives on [ForegroundWorkoutTimer];
 * this service keeps the process up, holds a partial wake lock with a timeout,
 * and posts the chronometer notification.
 */
@AndroidEntryPoint
class WorkoutTimerService : Service() {
    @Inject lateinit var timer: ForegroundWorkoutTimer
    @Inject lateinit var clock: ElapsedRealtimeClock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var warnedForAnchor: Long? = null
    private var spokenForAnchor: Long? = null
    private var speech: TextToSpeech? = null
    private var speechReady = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        speech = TextToSpeech(this) { status -> speechReady = status == TextToSpeech.SUCCESS }
        scope.launch {
            timer.specs.collect { spec ->
                if (spec == null) {
                    stopClock()
                } else {
                    show(spec)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLUS_30 -> scope.launch { timer.adjust(PLUS_MILLIS) }
            ACTION_SKIP -> timer.emit(TimerCommand.SKIP)
            ACTION_COMPLETE -> timer.emit(TimerCommand.COMPLETE_SET)
            ACTION_STOP -> stopClock()
            else -> timer.currentSpec()?.let(::show) ?: stopClock()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickJob?.cancel()
        releaseWakeLock()
        speech?.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    private fun show(spec: TimerSpec) {
        val now = clock.elapsedRealtime()
        timer.refresh(now)
        val snapshot = projectTimer(spec, now)
        val notification = workoutTimerNotification(this, snapshot, wallClockWhen(spec, now))
        startWorkoutForeground(notification)
        acquireWakeLock(wakeLockTimeoutMillis(spec, now))
        if (snapshot.phase == TimerPhase.WARNING && warnedForAnchor != spec.anchorElapsedRealtime) {
            warnedForAnchor = spec.anchorElapsedRealtime
            dualPulse()
            chime()
        }
        // Wait for the engine instead of marking the cue spoken while TTS is still initialising.
        if (speechReady && spec.speak && !spec.cue.isNullOrBlank() && spokenForAnchor != spec.anchorElapsedRealtime) {
            spokenForAnchor = spec.anchorElapsedRealtime
            speech?.speak(spec.cue, TextToSpeech.QUEUE_FLUSH, null, "workout-cue")
        }
        if (tickJob == null) {
            tickJob = scope.launch {
                while (isActive) {
                    delay(TICK_MILLIS)
                    timer.currentSpec()?.let(::show)
                }
            }
        }
    }

    private fun startWorkoutForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopClock() {
        tickJob?.cancel()
        tickJob = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock(timeoutMillis: Long) {
        val lock = wakeLock ?: (
            getSystemService(POWER_SERVICE) as PowerManager
            ).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).also { created ->
            created.setReferenceCounted(false)
            wakeLock = created
        }
        if (lock.isHeld) lock.release()
        lock.acquire(timeoutMillis.coerceAtLeast(1))
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) lock.release()
    }

    private fun dualPulse() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80), -1))
    }

    private fun chime() {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        audio.requestAudioFocus(focus)
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_MILLIS)
        scope.launch {
            delay(TONE_MILLIS.toLong())
            tone.release()
            audio.abandonAudioFocusRequest(focus)
        }
    }

    private fun wallClockWhen(spec: TimerSpec, nowElapsed: Long): Long {
        val delta = when (spec.kind) {
            TimerKind.COUNTDOWN -> spec.anchorElapsedRealtime - nowElapsed
            TimerKind.STOPWATCH -> nowElapsed - spec.anchorElapsedRealtime
        }
        return System.currentTimeMillis() + if (spec.kind == TimerKind.COUNTDOWN) delta else -delta
    }

    private fun createChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            WORKOUT_TIMER_CHANNEL_ID,
            getString(R.string.workout_timer_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_PLUS_30 = "com.forge.hypertrophy.timer.PLUS_30"
        const val ACTION_SKIP = "com.forge.hypertrophy.timer.SKIP"
        const val ACTION_COMPLETE = "com.forge.hypertrophy.timer.COMPLETE"
        const val ACTION_STOP = "com.forge.hypertrophy.timer.STOP"
        private const val NOTIFICATION_ID = 41
        private const val WAKE_LOCK_TAG = "hypertrophy:workout"
        private const val PLUS_MILLIS = 30_000L
        private const val TICK_MILLIS = 1_000L
        private const val TONE_MILLIS = 180
        private const val TONE_VOLUME = 80
    }
}
