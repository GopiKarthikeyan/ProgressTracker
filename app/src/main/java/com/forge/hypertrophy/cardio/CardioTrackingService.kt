package com.forge.hypertrophy.cardio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.forge.hypertrophy.R
import com.forge.hypertrophy.domain.engine.KmSplit
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Location foreground service. The cardio screen starts it while that screen
 * is visible. There is no background-location permission.
 */
@AndroidEntryPoint
class CardioTrackingService : android.app.Service() {
    @Inject lateinit var tracker: CardioTracker
    @Inject lateinit var locations: LocationSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var speech: TextToSpeech? = null
    private var speechReady = false
    private var audioFocus: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CARDIO_CHANNEL_ID,
                getString(R.string.cardio_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        speech = TextToSpeech(this) { status -> speechReady = status == TextToSpeech.SUCCESS }
        scope.launch {
            tracker.cues.collect { speak(it) }
        }
        scope.launch {
            tracker.live.collect { live ->
                if (live.active) {
                    startCardioForeground(cardioNotification(this@CardioTrackingService, live.distanceM, live.paused))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCardioForeground(cardioNotification(this, tracker.live.value.distanceM, tracker.live.value.paused))
        when (intent?.action) {
            ACTION_STOP -> finishAndStop()
            else -> beginUpdates()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        locations.stop()
        speech?.shutdown()
        abandonFocus()
        scope.cancel()
        super.onDestroy()
    }

    private fun beginUpdates() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED) {
            finishAndStop()
            return
        }
        locations.start { fix ->
            scope.launch { tracker.accept(fix) }
        }
    }

    private fun finishAndStop() {
        locations.stop()
        scope.launch {
            tracker.finish()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startCardioForeground(notification: android.app.Notification) {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    private fun speak(split: KmSplit) {
        if (!speechReady) return
        val minutes = split.paceSecPerKm / 60
        val seconds = split.paceSecPerKm % 60
        val words = getString(
            R.string.cardio_km_cue,
            resources.getQuantityString(R.plurals.cardio_km_distance, split.kilometer, split.kilometer),
            resources.getQuantityString(R.plurals.cardio_km_minutes, minutes, minutes),
            resources.getQuantityString(R.plurals.cardio_km_seconds, seconds, seconds),
        )
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        audioFocus = focus
        audio.requestAudioFocus(focus)
        speech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = abandonFocus()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = abandonFocus()
        })
        speech?.speak(words, TextToSpeech.QUEUE_FLUSH, null, "cardio-km-${split.kilometer}")
    }

    private fun abandonFocus() {
        val focus = audioFocus ?: return
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.abandonAudioFocusRequest(focus)
        audioFocus = null
    }

    companion object {
        const val ACTION_STOP = "com.forge.hypertrophy.cardio.STOP"
        private const val NOTIFICATION_ID = 42
    }
}
