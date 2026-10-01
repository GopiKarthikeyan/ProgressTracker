package com.forge.hypertrophy.cardio

import java.time.Instant

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double?,
    val altitudeM: Double?,
    val recordedAt: Instant,
)

interface LocationSource {
    fun start(onFix: (LocationFix) -> Unit)
    fun stop()
}

fun interface PlayServicesAvailable {
    fun available(): Boolean
}

/** Uses fused location when Play Services is present, and the platform source otherwise. */
class SelectingLocationSource(
    private val playServices: PlayServicesAvailable,
    private val fused: LocationSource,
    private val platform: LocationSource,
) : LocationSource {
    private var active: LocationSource? = null

    override fun start(onFix: (LocationFix) -> Unit) {
        val chosen = if (playServices.available()) fused else platform
        active = chosen
        chosen.start(onFix)
    }

    override fun stop() {
        active?.stop()
        active = null
    }
}
