package com.forge.hypertrophy.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreRollTest {
    @Test
    fun countdownCountsWholeSecondsLeft() {
        val countdown = Countdown(totalSeconds = PRE_ROLL_SECONDS, startedAtElapsedMs = 1_000L)
        assertEquals(5, countdown.secondsLeft(1_000L))
        assertEquals(5, countdown.secondsLeft(1_400L))
        assertEquals(4, countdown.secondsLeft(2_000L))
        assertEquals(1, countdown.secondsLeft(5_999L))
        assertEquals(0, countdown.secondsLeft(6_000L))
    }

    @Test
    fun countdownFinishesExactlyAtTheEnd() {
        val countdown = Countdown(totalSeconds = 10, startedAtElapsedMs = 0L)
        assertFalse(countdown.finished(9_999L))
        assertTrue(countdown.finished(10_000L))
        assertEquals(0, countdown.secondsLeft(50_000L))
    }

    @Test
    fun beepsOncePerSecondOfPreRoll() {
        assertEquals(listOf(5, 4, 3, 2, 1), countdownBeeps(PRE_ROLL_SECONDS))
        assertEquals(10, SELF_TIMER_SECONDS)
    }

    @Test
    fun captureKeysCoverVolumeAndShutter() {
        assertTrue(CaptureKeys.isCaptureKey(CaptureKeys.VOLUME_UP))
        assertTrue(CaptureKeys.isCaptureKey(CaptureKeys.VOLUME_DOWN))
        assertTrue(CaptureKeys.isCaptureKey(CaptureKeys.CAMERA))
        assertFalse(CaptureKeys.isCaptureKey(4))
    }

    @Test
    fun physiqueSequenceRunsFrontSideBack() {
        assertEquals(listOf("FRONT", "SIDE", "BACK"), POSE_ORDER.map { it.name })
        assertEquals(POSE_ORDER[0], nextPose(emptySet()))
        assertEquals(POSE_ORDER[1], nextPose(setOf(POSE_ORDER[0])))
        assertEquals(POSE_ORDER[2], nextPose(setOf(POSE_ORDER[0], POSE_ORDER[1])))
        assertEquals(null, nextPose(POSE_ORDER.toSet()))
    }
}
