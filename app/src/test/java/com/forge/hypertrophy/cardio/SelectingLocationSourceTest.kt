package com.forge.hypertrophy.cardio

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectingLocationSourceTest {
    @Test
    fun usesThePlatformSourceWhenPlayServicesAreAbsent() {
        val fused = FakeSource()
        val platform = FakeSource()
        val source = SelectingLocationSource(
            playServices = PlayServicesAvailable { false },
            fused = fused,
            platform = platform,
        )
        val received = mutableListOf<LocationFix>()
        source.start { received += it }
        assertTrue(platform.started)
        assertFalse(fused.started)
        platform.emit(
            LocationFix(
                latitude = 12.0,
                longitude = 77.0,
                accuracyM = 5.0,
                altitudeM = null,
                recordedAt = Instant.parse("2026-10-01T06:00:00Z"),
            ),
        )
        assertEquals(12.0, received.single().latitude, 0.0)
        source.stop()
        assertFalse(platform.started)
    }

    @Test
    fun usesFusedLocationWhenPlayServicesArePresent() {
        val fused = FakeSource()
        val platform = FakeSource()
        val source = SelectingLocationSource(
            playServices = PlayServicesAvailable { true },
            fused = fused,
            platform = platform,
        )
        source.start { }
        assertTrue(fused.started)
        assertFalse(platform.started)
    }

    private class FakeSource : LocationSource {
        var started = false
        private var onFix: ((LocationFix) -> Unit)? = null

        override fun start(onFix: (LocationFix) -> Unit) {
            started = true
            this.onFix = onFix
        }

        override fun stop() {
            started = false
            onFix = null
        }

        fun emit(fix: LocationFix) {
            onFix?.invoke(fix)
        }
    }
}
