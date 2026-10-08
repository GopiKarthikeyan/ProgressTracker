package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.LoggedSet
import com.forge.hypertrophy.domain.model.SetType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartingWeightTest {
    private val setAt = Instant.parse("2026-10-01T10:00:00Z")
    private val baseline = StartingWeight(weightKg = 60.0, repsHint = 8, setAt = setAt)

    @Test
    fun nullWeightMeansTheFirstSessionCalibrates() {
        assertTrue(StartingWeight(null, null, setAt).awaitingCalibration)
        assertFalse(baseline.awaitingCalibration)
    }

    @Test
    fun onlyTheSessionThatWroteTheBaselineIsCalibration() {
        assertTrue(isCalibrationSession(setAt, baseline))
        assertFalse(isCalibrationSession(setAt.plusSeconds(1), baseline))
        assertFalse(isCalibrationSession(null, baseline))
        assertFalse(isCalibrationSession(setAt, null))
        assertFalse(isCalibrationSession(setAt, StartingWeight(null, null, setAt)))
    }

    @Test
    fun sessionsAreMostRecentFirstWithCalibrationFlagged() {
        val older = listOf(LoggedSet(50.0, 8, SetType.WORKING))
        val calibration = listOf(LoggedSet(60.0, 8, SetType.WORKING))
        val newest = listOf(LoggedSet(62.5, 8, SetType.WORKING))
        val groups = listOf(
            null to older,
            setAt to calibration,
            setAt.plusSeconds(86_400) to newest,
        )

        val sessions = slotSessionsForProgression(groups, baseline)

        assertEquals(listOf(newest, calibration, older), sessions.map { it.sets })
        assertEquals(listOf(false, true, false), sessions.map { it.calibration })
    }

    @Test
    fun noBaselineLeavesEverySessionAsHistory() {
        val groups = listOf(setAt to listOf(LoggedSet(60.0, 8, SetType.WORKING)))
        assertFalse(slotSessionsForProgression(groups, null).single().calibration)
    }
}
