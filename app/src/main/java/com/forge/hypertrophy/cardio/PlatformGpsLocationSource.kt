package com.forge.hypertrophy.cardio

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import java.time.Clock
import java.time.Instant

class PlatformGpsLocationSource(
    private val manager: LocationManager,
    private val clock: Clock,
) : LocationSource {
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    override fun start(onFix: (LocationFix) -> Unit) {
        stop()
        val updates = LocationListener { location -> onFix(location.toFix(clock)) }
        listener = updates
        manager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            UPDATE_INTERVAL_MS,
            0f,
            updates,
            Looper.getMainLooper(),
        )
    }

    override fun stop() {
        listener?.let { manager.removeUpdates(it) }
        listener = null
    }

    companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
    }
}

internal fun Location.toFix(clock: Clock): LocationFix {
    val recorded = if (time > 0) Instant.ofEpochMilli(time) else clock.instant()
    return LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
        altitudeM = if (hasAltitude()) altitude else null,
        recordedAt = recorded,
    )
}
