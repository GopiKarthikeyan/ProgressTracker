package com.forge.hypertrophy.data.weather

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-01T06:30:00Z"), ZoneOffset.UTC)

    @Test
    fun parsesTemperatureAndUv() {
        val reading = parseOpenMeteo(
            """{"current":{"temperature_2m":18.4,"uv_index":3.2}}""",
        )
        assertEquals(18.4, reading!!.tempC!!, 0.001)
        assertEquals(3.2, reading.uvIndex!!, 0.001)
    }

    @Test
    fun aFailedCallReturnsNullAndIsNotCached() = runBlocking {
        var calls = 0
        val repository = CachingWeatherRepository(clock) { _, _ ->
            calls += 1
            throw IllegalStateException("offline")
        }
        assertNull(repository.current(12.9716, 77.5946))
        assertNull(repository.current(12.9716, 77.5946))
        assertEquals(2, calls)
    }

    @Test
    fun aSuccessfulCallIsCachedForTheHour() = runBlocking {
        var calls = 0
        val repository = CachingWeatherRepository(clock) { _, _ ->
            calls += 1
            WeatherReading(tempC = 21.0, uvIndex = 1.0)
        }
        assertEquals(21.0, repository.current(12.971, 77.594)!!.tempC!!, 0.001)
        assertEquals(21.0, repository.current(12.974, 77.591)!!.tempC!!, 0.001)
        assertEquals(1, calls)
    }
}
