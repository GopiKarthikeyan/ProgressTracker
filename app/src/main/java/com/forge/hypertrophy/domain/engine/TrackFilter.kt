package com.forge.hypertrophy.domain.engine

import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RawFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double?,
    val recordedAt: Instant,
    val altitudeM: Double? = null,
)

data class TrackPointSample(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val accuracyM: Double?,
    val recordedAt: Instant,
)

data class KmSplit(
    val kilometer: Int,
    val durationSec: Int,
    val paceSecPerKm: Int,
)

data class FilterState(
    val points: List<TrackPointSample> = emptyList(),
    val distanceM: Double = 0.0,
    val movingSec: Int = 0,
    val paused: Boolean = false,
    val splits: List<KmSplit> = emptyList(),
)

/**
 * GPS track reducer.
 *
 * A fix is dropped when its accuracy is missing or worse than [MAX_ACCURACY_M].
 * Stationary jitter is a hop shorter than [MIN_MOVEMENT_M] or slower than
 * [MIN_SPEED_MPS]; it does not add distance. After [AUTO_PAUSE_SEC] of that
 * jitter the track pauses, and the paused gap is left out of moving time.
 * Each 1 km of accepted distance records a split from moving time.
 */
class TrackFilter {
    fun filter(fixes: List<RawFix>): FilterState {
        return fixes.sortedBy { it.recordedAt }.fold(FilterState(), ::step)
    }

    fun step(state: FilterState, fix: RawFix): FilterState {
        val accuracy = fix.accuracyM
        if (accuracy == null || accuracy > MAX_ACCURACY_M) return state
        val last = state.points.lastOrNull() ?: return state.copy(
            points = listOf(fix.toSample()),
            paused = false,
        )
        val delta = haversineMeters(last.latitude, last.longitude, fix.latitude, fix.longitude)
        val gap = java.time.Duration.between(last.recordedAt, fix.recordedAt).seconds.coerceAtLeast(0)
        val speed = if (gap == 0L) Double.POSITIVE_INFINITY else delta / gap.toDouble()
        val jitter = delta < MIN_MOVEMENT_M || speed < MIN_SPEED_MPS
        if (jitter) {
            return if (gap >= AUTO_PAUSE_SEC || state.paused) state.copy(paused = true) else state
        }
        val movingAdded = if (state.paused) 0 else gap.toInt()
        val moving = state.movingSec + movingAdded
        val distance = state.distanceM + delta
        return state.copy(
            points = state.points + fix.toSample(),
            distanceM = LoadRounding.roundToDecimals(distance),
            movingSec = moving,
            paused = false,
            splits = appendSplits(state.distanceM, distance, state.movingSec, moving, state.splits),
        )
    }

    private fun appendSplits(
        previousDistance: Double,
        distance: Double,
        previousMoving: Int,
        moving: Int,
        existing: List<KmSplit>,
    ): List<KmSplit> {
        val delta = (distance - previousDistance).coerceAtLeast(0.0)
        val added = existing.toMutableList()
        var nextKm = existing.size + 1
        var kmStart = existing.lastOrNull()?.let { previousMoving } ?: 0
        if (existing.isNotEmpty()) {
            kmStart = existing.sumOf { it.durationSec }
        }
        while (distance >= nextKm * METERS_PER_KM) {
            val boundary = nextKm * METERS_PER_KM
            val fraction = if (delta == 0.0) 1.0 else ((boundary - previousDistance) / delta).coerceIn(0.0, 1.0)
            val at = previousMoving + ((moving - previousMoving) * fraction).toInt()
            val duration = (at - kmStart).coerceAtLeast(0)
            added += KmSplit(kilometer = nextKm, durationSec = duration, paceSecPerKm = duration)
            kmStart = at
            nextKm += 1
        }
        return added
    }

    private fun RawFix.toSample() = TrackPointSample(
        latitude = latitude,
        longitude = longitude,
        altitudeM = altitudeM,
        accuracyM = accuracyM,
        recordedAt = recordedAt,
    )

    companion object {
        const val MAX_ACCURACY_M = 20.0
        const val MIN_MOVEMENT_M = 10.0
        const val MIN_SPEED_MPS = 0.5
        const val AUTO_PAUSE_SEC = 10L
        const val METERS_PER_KM = 1_000.0
    }
}

fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earth = 6_371_000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(p1) * cos(p2) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earth * c
}
