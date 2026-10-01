package com.forge.hypertrophy.workout

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.forge.hypertrophy.R
import com.forge.hypertrophy.data.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.workout.ElapsedRealtimeClock
import com.forge.hypertrophy.domain.workout.TimerKind
import com.forge.hypertrophy.domain.workout.TimerSpec
import com.forge.hypertrophy.domain.workout.countdownSpec
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile. One tap starts a rest timer with the default duration
 * through [ForegroundWorkoutTimer], which brings up [WorkoutTimerService] as a
 * foreground service; a tap while a rest is running stops it. Nothing here
 * needs the device unlocked, so it works from the lock screen and from a cold
 * process.
 */
@AndroidEntryPoint
class RestTimerTileService : TileService() {
    @Inject lateinit var timer: ForegroundWorkoutTimer
    @Inject lateinit var preferences: TrainingPreferencesRepository
    @Inject lateinit var clock: ElapsedRealtimeClock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            timer.specs.collect { spec -> render(spec) }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val running = timer.currentSpec()
            if (running != null && running.kind == TimerKind.COUNTDOWN) {
                timer.stop()
            } else {
                val seconds = preferences.defaultRestSeconds.first()
                timer.start(
                    countdownSpec(
                        nowElapsedRealtime = clock.elapsedRealtime(),
                        minimumSeconds = seconds,
                        maximumSeconds = seconds,
                        cue = getString(R.string.tile_rest_cue),
                    ),
                )
            }
            render(timer.currentSpec())
        }
    }

    override fun onDestroy() {
        listening?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun render(spec: TimerSpec?) {
        val tile = qsTile ?: return
        val running = spec != null && spec.kind == TimerKind.COUNTDOWN
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_rest_label)
        tile.subtitle = getString(if (running) R.string.tile_rest_running else R.string.tile_rest_idle)
        tile.updateTile()
    }
}
