package com.forge.hypertrophy.cardio

import com.forge.hypertrophy.data.dao.DaoTest
import com.forge.hypertrophy.data.repository.RoomCardioRepository
import com.forge.hypertrophy.data.repository.RoomSessionRepository
import com.forge.hypertrophy.data.weather.WeatherRepository
import com.forge.hypertrophy.data.weather.WeatherReading
import com.forge.hypertrophy.domain.model.CardioActivity
import com.forge.hypertrophy.domain.model.CardioStyle
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CardioTrackerTest : DaoTest() {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-01T06:00:00Z"), ZoneOffset.UTC)
    private val start: Instant = Instant.parse("2026-10-01T06:00:00Z")

    @Test
    fun trackPointsFlushInBatchesOfTwenty() = runBlocking {
        val tracker = CardioTracker(
            sessions = RoomSessionRepository(db.sessionDao()),
            cardio = RoomCardioRepository(db.cardioDao()),
            weather = QuietWeather(),
            clock = clock,
        )
        val logId = tracker.begin(
            activity = CardioActivity.RUNNING,
            style = CardioStyle.JOG,
            gearId = null,
        )!!
        repeat(20) { index -> tracker.accept(fix(index)) }
        assertEquals(20, first(db.cardioDao().observeTrackPoints(logId)).size)
        repeat(5) { index -> tracker.accept(fix(20 + index)) }
        assertEquals(20, first(db.cardioDao().observeTrackPoints(logId)).size)
        tracker.finish()
        assertEquals(25, first(db.cardioDao().observeTrackPoints(logId)).size)
    }

    private fun fix(index: Int) = LocationFix(
        latitude = 12.0 + index * 15.0 / 111_320.0,
        longitude = 77.0,
        accuracyM = 5.0,
        altitudeM = null,
        recordedAt = start.plusSeconds(index * 2L),
    )
}

private class QuietWeather : WeatherRepository {
    override suspend fun current(latitude: Double, longitude: Double): WeatherReading? = null
}
