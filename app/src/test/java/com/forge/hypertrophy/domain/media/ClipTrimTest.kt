package com.forge.hypertrophy.domain.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipTrimTest {
    @Test
    fun defaultsTrimOnlyTheTail() {
        assertEquals(0L, DEFAULT_LEAD_TRIM_MS)
        assertEquals(3_000L, DEFAULT_TAIL_TRIM_MS)
        assertEquals(TrimWindow(0L, 17_000L), trimWindow(20_000L))
    }

    @Test
    fun leadAndTailAreBothApplied() {
        assertEquals(TrimWindow(2_000L, 17_000L), trimWindow(20_000L, leadMs = 2_000L, tailMs = 3_000L))
    }

    @Test
    fun clipShorterThanTheTrimIsKeptWhole() {
        assertEquals(TrimWindow(0L, 2_000L), trimWindow(2_000L, leadMs = 0L, tailMs = 3_000L))
        assertEquals(TrimWindow(0L, 4_000L), trimWindow(4_000L, leadMs = 2_000L, tailMs = 2_000L))
    }

    @Test
    fun negativeTrimsAreClampedToZero() {
        assertEquals(TrimWindow(0L, 10_000L), trimWindow(10_000L, leadMs = -5L, tailMs = -5L))
    }

    @Test
    fun zeroLengthClipYieldsEmptyWindow() {
        assertEquals(TrimWindow(0L, 0L), trimWindow(0L))
    }
}
