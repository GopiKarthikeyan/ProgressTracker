package com.forge.hypertrophy.cardio

import com.forge.hypertrophy.data.entity.CardioLogEntity
import com.forge.hypertrophy.data.entity.TrackPointEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.CardioRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.weather.WeatherRepository
import com.forge.hypertrophy.domain.engine.FilterState
import com.forge.hypertrophy.domain.engine.KmSplit
import com.forge.hypertrophy.domain.engine.RawFix
import com.forge.hypertrophy.domain.engine.TrackFilter
import com.forge.hypertrophy.domain.engine.TrackPointSample
import com.forge.hypertrophy.domain.model.CardioActivity
import com.forge.hypertrophy.domain.model.CardioSource
import com.forge.hypertrophy.domain.model.CardioStyle
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LiveTrack(
    val active: Boolean = false,
    val distanceM: Double = 0.0,
    val movingSec: Int = 0,
    val paused: Boolean = false,
    val points: List<TrackPointSample> = emptyList(),
    val logId: Long? = null,
    val sessionId: Long? = null,
)

@Singleton
class CardioTracker @Inject constructor(
    private val sessions: SessionRepository,
    private val cardio: CardioRepository,
    private val weather: WeatherRepository,
    private val clock: Clock,
) {
    private val filter = TrackFilter()
    private val gate = Mutex()
    private val _live = MutableStateFlow(LiveTrack())
    val live: StateFlow<LiveTrack> = _live.asStateFlow()
    private val _cues = MutableSharedFlow<KmSplit>(extraBufferCapacity = 8)
    val cues: SharedFlow<KmSplit> = _cues.asSharedFlow()

    private var filtered = FilterState()
    private var sequence = 0
    private var buffer = BatchBuffer<TrackPointEntity>(BATCH_SIZE) { batch ->
        cardio.insertTrackPoints(batch)
    }
    private var style = CardioStyle.JOG
    private var activity = CardioActivity.RUNNING
    private var customName = ""
    private var gearId: Long? = null

    suspend fun begin(
        activity: CardioActivity,
        style: CardioStyle,
        gearId: Long?,
        customName: String = "",
    ): Long? {
        return gate.withLock {
        if (_live.value.active) return null
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        val sessionId = sessions.insert(
            WorkoutSessionEntity(
                date = today,
                dayId = null,
                kind = SessionKind.CARDIO,
                status = SessionStatus.IN_PROGRESS,
                isDeload = false,
                isShortOnTime = false,
                readinessSleep = null,
                readinessSoreness = null,
                readinessEnergy = null,
                startedAt = clock.instant(),
                completedAt = null,
            ),
        )
        val logId = cardio.insert(
            CardioLogEntity(
                sessionId = sessionId,
                distanceM = 0.0,
                durationSec = 0,
                source = CardioSource.GPS,
                gearId = gearId,
                tempC = null,
                uvIndex = null,
                type = style,
                activity = activity,
                customName = customName,
            ),
        )
        filtered = FilterState()
        sequence = 0
        this.style = style
        this.activity = activity
        this.customName = customName
        this.gearId = gearId
        buffer = BatchBuffer(BATCH_SIZE) { batch -> cardio.insertTrackPoints(batch) }
        _live.value = LiveTrack(active = true, logId = logId, sessionId = sessionId)
        logId
        }
    }

    suspend fun accept(fix: LocationFix) {
        gate.withLock {
        if (!_live.value.active) return
        val next = filter.step(
            filtered,
            RawFix(
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyM = fix.accuracyM,
                recordedAt = fix.recordedAt,
                altitudeM = fix.altitudeM,
            ),
        )
        val added = next.points.size - filtered.points.size
        val newSplits = next.splits.drop(filtered.splits.size)
        filtered = next
        if (added > 0) {
            val sample = next.points.last()
            buffer.add(sample.toEntity(_live.value.logId ?: return, sequence++))
        }
        _live.value = _live.value.copy(
            distanceM = next.distanceM,
            movingSec = next.movingSec,
            paused = next.paused,
            points = next.points,
        )
        newSplits.forEach { _cues.tryEmit(it) }
        if (added > 0) persistProgress()
        }
    }

    suspend fun recover(sessionId: Long) {
        gate.withLock {
        if (_live.value.active) return
        val session = sessions.get(sessionId) ?: return
        if (session.kind != SessionKind.CARDIO || session.status != SessionStatus.IN_PROGRESS) return
        val log = cardio.observeLog(sessionId).first()
        if (log == null) {
            sessions.update(session.copy(status = SessionStatus.COMPLETED, completedAt = clock.instant()))
            return
        }
        val stored = cardio.observeTrackPoints(log.id).first()
        val reduced = filter.filter(
            stored.map { point ->
                RawFix(point.latitude, point.longitude, point.accuracyM, point.recordedAt, point.altitudeM)
            },
        )
        val last = reduced.points.lastOrNull()
        val reading = if (last == null) null else runCatching { weather.current(last.latitude, last.longitude) }.getOrNull()
        cardio.update(
            log.copy(
                distanceM = reduced.distanceM,
                durationSec = reduced.movingSec,
                tempC = reading?.tempC ?: log.tempC,
                uvIndex = reading?.uvIndex ?: log.uvIndex,
            ),
        )
        sessions.update(session.copy(status = SessionStatus.COMPLETED, completedAt = clock.instant()))
        }
    }

    suspend fun finish() {
        gate.withLock {
        val live = _live.value
        val sessionId = live.sessionId ?: return
        val logId = live.logId ?: return
        if (!live.active) return
        buffer.drain()
        val last = filtered.points.lastOrNull()
        val reading = if (last == null) {
            null
        } else {
            runCatching { weather.current(last.latitude, last.longitude) }.getOrNull()
        }
        val session = sessions.get(sessionId) ?: return
        sessions.update(
            session.copy(status = SessionStatus.COMPLETED, completedAt = clock.instant()),
        )
        cardio.update(
            CardioLogEntity(
                id = logId,
                sessionId = sessionId,
                distanceM = filtered.distanceM,
                durationSec = filtered.movingSec,
                source = CardioSource.GPS,
                gearId = gearId,
                tempC = reading?.tempC,
                uvIndex = reading?.uvIndex,
                type = style,
                activity = activity,
                customName = customName,
            ),
        )
        _live.value = live.copy(active = false)
        }
    }

    private suspend fun persistProgress() {
        val live = _live.value
        val logId = live.logId ?: return
        val sessionId = live.sessionId ?: return
        cardio.update(
            CardioLogEntity(
                id = logId,
                sessionId = sessionId,
                distanceM = filtered.distanceM,
                durationSec = filtered.movingSec,
                source = CardioSource.GPS,
                gearId = gearId,
                tempC = null,
                uvIndex = null,
                type = style,
                activity = activity,
                customName = customName,
            ),
        )
    }

    private fun TrackPointSample.toEntity(logId: Long, index: Int) = TrackPointEntity(
        cardioLogId = logId,
        latitude = latitude,
        longitude = longitude,
        altitudeM = altitudeM,
        accuracyM = accuracyM,
        recordedAt = recordedAt,
        sequenceIndex = index,
    )

    companion object {
        const val BATCH_SIZE = 20
    }
}
