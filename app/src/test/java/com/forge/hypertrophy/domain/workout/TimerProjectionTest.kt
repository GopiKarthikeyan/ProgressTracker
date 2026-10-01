package com.forge.hypertrophy.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerProjectionTest {
    @Test
    fun countdownWarnsAtFifteenSecondsThenCountsOvertimeUpToTheMax() {
        val spec = countdownSpec(nowElapsedRealtime = 1_000, minimumSeconds = 60, maximumSeconds = 90)
        assertEquals(TimerPhase.COUNTDOWN, projectTimer(spec, 1_000).phase)
        assertEquals(60_000, projectTimer(spec, 1_000).remainingMillis)
        assertEquals(TimerPhase.WARNING, projectTimer(spec, 1_000 + 45_000).phase)
        assertEquals(TimerPhase.OVERTIME, projectTimer(spec, 1_000 + 60_000).phase)
        assertEquals(10_000, projectTimer(spec, 1_000 + 70_000).elapsedMillis)
        assertEquals(TimerPhase.FINISHED, projectTimer(spec, 1_000 + 90_000).phase)
    }

    @Test
    fun plusThirtyMovesTheEndAndTheOvertimeCeiling() {
        val adjusted = countdownSpec(0, 60, 90).adjust(TIMER_STEP_MILLIS)
        assertEquals(90_000L, adjusted.anchorElapsedRealtime)
        assertEquals(120_000L, adjusted.overtimeEndElapsedRealtime)
        assertEquals(TimerPhase.COUNTDOWN, projectTimer(adjusted, 0).phase)
    }

    @Test
    fun asNeededIsAStopwatchAndIgnoresAdjust() {
        val spec = stopwatchSpec(500)
        assertEquals(TimerPhase.STOPWATCH, projectTimer(spec, 2_500).phase)
        assertEquals(2_000, projectTimer(spec, 2_500).elapsedMillis)
        assertEquals(spec, spec.adjust(TIMER_STEP_MILLIS))
    }

    @Test
    fun wakeLockTimeoutCoversTheOvertimeWindowAndIsNeverZero() {
        val spec = countdownSpec(0, 60, 90)
        assertEquals(90_000, wakeLockTimeoutMillis(spec, 0))
        assertEquals(1, wakeLockTimeoutMillis(spec, 90_000))
        assertEquals(30L * 60L * 1000L, wakeLockTimeoutMillis(stopwatchSpec(0), 0))
    }

    @Test
    fun handsFreeDebounceAndUndoWindow() {
        val gate = HandsFreeGate()
        assertTrue(gate.accept(0))
        assertFalse(gate.accept(499))
        assertTrue(gate.accept(500))
        gate.armUndo(setId = 7, nowElapsedRealtime = 500)
        assertEquals(7L, gate.takeUndo(500 + HANDS_FREE_UNDO_MILLIS))
        gate.armUndo(setId = 8, nowElapsedRealtime = 0)
        assertEquals(null, gate.takeUndo(HANDS_FREE_UNDO_MILLIS + 1))
    }
}
