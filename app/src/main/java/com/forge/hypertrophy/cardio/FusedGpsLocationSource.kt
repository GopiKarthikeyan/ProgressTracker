package com.forge.hypertrophy.cardio

import android.annotation.SuppressLint
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import java.time.Clock

class FusedGpsLocationSource(
    private val client: com.google.android.gms.location.FusedLocationProviderClient,
    private val clock: Clock,
) : LocationSource {
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun start(onFix: (LocationFix) -> Unit) {
        stop()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, PlatformGpsLocationSource.UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(PlatformGpsLocationSource.UPDATE_INTERVAL_MS)
            .build()
        val updates = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onFix(it.toFix(clock)) }
            }
        }
        callback = updates
        client.requestLocationUpdates(request, updates, Looper.getMainLooper())
    }

    override fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }
}
