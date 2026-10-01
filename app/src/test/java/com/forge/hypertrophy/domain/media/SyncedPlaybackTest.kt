package com.forge.hypertrophy.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncedPlaybackTest {
    private val playback = SyncedPlayback(leftDurationMs = 10_000L, rightDurationMs = 8_000L)

    @Test
    fun lengthIsTheShorterAlignedClip() {
        assertEquals(8_000L, playback.lengthMs)
        assertEquals(8_000L, playback.withLeftOffset(1_000L).lengthMs)
        assertEquals(7_000L, playback.withLeftOffset(3_000L).lengthMs)
        assertEquals(6_000L, playback.withRightOffset(2_000L).lengthMs)
    }

    @Test
    fun seekKeepsBothClipsAlignedWithTheirOffsets() {
        val aligned = playback.withLeftOffset(500L).withRightOffset(1_500L).seekTo(2_000L)
        assertEquals(2_500L, aligned.leftPositionMs)
        assertEquals(3_500L, aligned.rightPositionMs)
    }

    @Test
    fun seekIsClampedToTheSharedTimeline() {
        assertEquals(0L, playback.seekTo(-50L).positionMs)
        assertEquals(playback.lengthMs, playback.seekTo(99_999L).positionMs)
    }

    @Test
    fun frameStepPausesAndMovesOneFrame() {
        val stepped = playback.copy(playing = true).seekTo(1_000L).stepFrame(forward = true)
        assertFalse(stepped.playing)
        assertEquals(1_000L + FRAME_STEP_MS, stepped.positionMs)
        assertEquals(1_000L, stepped.stepFrame(forward = false).positionMs)
        assertEquals(0L, playback.seekTo(0L).stepFrame(forward = false).positionMs)
    }

    @Test
    fun speedTogglesBetweenHalfAndNormal() {
        assertEquals(NORMAL_SPEED, playback.speed)
        assertEquals(SLOW_SPEED, playback.toggleSpeed().speed)
        assertEquals(NORMAL_SPEED, playback.toggleSpeed().toggleSpeed().speed)
    }

    @Test
    fun advanceScalesWithSpeedAndStopsAtTheEnd() {
        val playing = playback.togglePlaying()
        assertTrue(playing.playing)
        assertEquals(1_000L, playing.advance(1_000L).positionMs)
        assertEquals(500L, playing.toggleSpeed().advance(1_000L).positionMs)
        val finished = playing.advance(50_000L)
        assertEquals(playback.lengthMs, finished.positionMs)
        assertFalse(finished.playing)
        assertEquals(0L, playback.advance(1_000L).positionMs)
    }

    @Test
    fun offsetsAreClampedToTheirClip() {
        assertEquals(0L, playback.withLeftOffset(-10L).leftOffsetMs)
        assertEquals(8_000L, playback.withRightOffset(50_000L).rightOffsetMs)
    }
}
