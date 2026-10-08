package com.forge.hypertrophy.domain.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandsFreeKeysTest {
    @Test
    fun volumeShutterAndMediaKeysLogASet() {
        listOf(
            HandsFreeKeys.VOLUME_UP,
            HandsFreeKeys.VOLUME_DOWN,
            HandsFreeKeys.CAMERA,
            HandsFreeKeys.HEADSETHOOK,
            HandsFreeKeys.MEDIA_PLAY_PAUSE,
            HandsFreeKeys.MEDIA_PLAY,
        ).forEach { code ->
            assertTrue(HandsFreeKeys.isWorkoutKey(code))
        }
    }

    @Test
    fun unrelatedKeysAreIgnored() {
        assertFalse(HandsFreeKeys.isWorkoutKey(4))
        assertFalse(HandsFreeKeys.isWorkoutKey(HandsFreeKeys.VOLUME_UP + 100))
    }

    @Test
    fun onlyHeadsetAndMediaKeysYieldWhileMusicPlays() {
        assertTrue(HandsFreeKeys.yieldsToActiveMedia(HandsFreeKeys.HEADSETHOOK))
        assertTrue(HandsFreeKeys.yieldsToActiveMedia(HandsFreeKeys.MEDIA_PLAY))
        assertTrue(HandsFreeKeys.yieldsToActiveMedia(HandsFreeKeys.MEDIA_PLAY_PAUSE))
        assertFalse(HandsFreeKeys.yieldsToActiveMedia(HandsFreeKeys.VOLUME_UP))
        assertFalse(HandsFreeKeys.yieldsToActiveMedia(HandsFreeKeys.VOLUME_DOWN))
        assertFalse(HandsFreeKeys.yieldsToActiveMedia(HandsFreeKeys.CAMERA))
    }
}
