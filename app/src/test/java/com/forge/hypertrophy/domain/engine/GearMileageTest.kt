package com.forge.hypertrophy.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GearMileageTest {
    @Test
    fun defaultLimitIs700Kilometres() {
        assertEquals(700_000, GearMileage.limitMeters(null))
        assertFalse(GearMileage.retired(699_999.0, null))
        assertTrue(GearMileage.retired(700_000.0, null))
    }

    @Test
    fun aConfiguredLimitReplacesTheDefault() {
        assertFalse(GearMileage.retired(999.0, 1_000))
        assertTrue(GearMileage.retired(1_000.0, 1_000))
    }
}
