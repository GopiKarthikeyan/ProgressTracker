package com.forge.hypertrophy.domain.engine

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackFilterTest {
    private val filter = TrackFilter()
    private val start = Instant.parse("2026-10-01T06:00:00Z")
    private val originLat = 12.0
    private val originLon = 77.0

    @Test
    fun dropsFixesWorseThan20MetresAndMissingAccuracy() {
        val result = filter.filter(
            listOf(
                fix(0, 0.0, accuracy = 21.0),
                fix(1, 0.0, accuracy = null),
                fix(2, 0.0, accuracy = 20.0),
            ),
        )
        assertEquals(1, result.points.size)
        assertEquals(20.0, result.points.single().accuracyM!!, 0.0)
    }

    @Test
    fun dropsStationaryJitterAndPausesAfterTenSeconds() {
        val result = filter.filter(
            listOf(
                fix(0, 0.0),
                fix(20, 4.0),
            ),
        )
        assertEquals(1, result.points.size)
        assertEquals(0.0, result.distanceM, 0.001)
        assertTrue(result.paused)
        assertEquals(0, result.movingSec)
    }

    @Test
    fun dropsASlowWanderEvenWhenTheHopExceedsTenMetres() {
        val result = filter.filter(
            listOf(
                fix(0, 0.0),
                fix(60, 15.0),
            ),
        )
        assertEquals(1, result.points.size)
        assertTrue(result.paused)
    }

    @Test
    fun keepsRealMovementAndCountsMovingTime() {
        val result = filter.filter(
            listOf(
                fix(0, 0.0),
                fix(20, 100.0),
            ),
        )
        assertEquals(2, result.points.size)
        assertEquals(haversineMeters(originLat, originLon, north(100.0), originLon), result.distanceM, 0.05)
        assertEquals(20, result.movingSec)
        assertFalse(result.paused)
    }

    @Test
    fun autoPauseLeavesTheStoppedGapOutOfMovingTime() {
        val result = filter.filter(
            listOf(
                fix(0, 0.0),
                fix(15, 4.0),
                fix(40, 200.0),
            ),
        )
        assertEquals(0, result.movingSec)
        assertEquals(haversineMeters(originLat, originLon, north(200.0), originLon), result.distanceM, 0.05)
        assertFalse(result.paused)
    }

    @Test
    fun recordsASplitForEachKilometre() {
        val fixes = (0..25).map { step -> fix(step * 30L, step * 100.0) }
        val result = filter.filter(fixes)
        assertEquals(2, result.splits.size)
        assertEquals(1, result.splits[0].kilometer)
        assertEquals(2, result.splits[1].kilometer)
        assertTrue(result.splits[0].durationSec > 0)
        assertTrue(result.splits[1].durationSec > 0)
        assertEquals(result.splits[0].durationSec, result.splits[0].paceSecPerKm)
        assertTrue(result.distanceM > 2_000.0)
    }

    private fun fix(seconds: Long, northMeters: Double, accuracy: Double? = 5.0) = RawFix(
        latitude = north(northMeters),
        longitude = originLon,
        accuracyM = accuracy,
        recordedAt = start.plusSeconds(seconds),
    )

    private fun north(meters: Double): Double = originLat + meters / 111_320.0
}
