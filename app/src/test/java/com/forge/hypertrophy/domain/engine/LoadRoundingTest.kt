package com.forge.hypertrophy.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class LoadRoundingTest {
    @Test
    fun decimalRoundingIsNotThePlateGrid() {
        assertEquals(82.5, LoadRounding.roundToDecimals(82.49999), 0.0)
        assertEquals(82.5, LoadRounding.roundToIncrement(82.49999, 2.5), 0.0)
        assertEquals(116.67, LoadRounding.roundToDecimals(116.666666), 0.0)
        assertEquals(117.5, LoadRounding.roundToIncrement(116.666666, 2.5), 0.0)
    }

    @Test
    fun roundingIsIdempotent() {
        val values = listOf(82.49999, 80.0, 2.5, 116.666666, 0.0)
        for (value in values) {
            val decimals = LoadRounding.roundToDecimals(value)
            assertEquals(decimals, LoadRounding.roundToDecimals(decimals), 0.0)
            val stepped = LoadRounding.roundToIncrement(value, 2.5)
            assertEquals(stepped, LoadRounding.roundToIncrement(stepped, 2.5), 0.0)
        }
    }
}
