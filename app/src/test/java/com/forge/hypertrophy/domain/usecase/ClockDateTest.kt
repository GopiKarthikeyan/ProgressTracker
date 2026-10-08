package com.forge.hypertrophy.domain.usecase

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockDateTest {
    private val lateEveningUtc = Instant.parse("2026-10-05T22:30:00Z")

    @Test
    fun utcClockReportsTheUtcDate() {
        assertEquals(LocalDate.of(2026, 10, 5), Clock.fixed(lateEveningUtc, ZoneOffset.UTC).localDate())
    }

    @Test
    fun dateFollowsTheClockZoneNotUtc() {
        val kolkata = Clock.fixed(lateEveningUtc, ZoneId.of("Asia/Kolkata"))
        assertEquals(LocalDate.of(2026, 10, 6), kolkata.localDate())

        val losAngeles = Clock.fixed(Instant.parse("2026-10-06T05:00:00Z"), ZoneId.of("America/Los_Angeles"))
        assertEquals(LocalDate.of(2026, 10, 5), losAngeles.localDate())
    }
}
