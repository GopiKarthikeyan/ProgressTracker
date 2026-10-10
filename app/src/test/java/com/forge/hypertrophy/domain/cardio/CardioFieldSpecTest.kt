package com.forge.hypertrophy.domain.cardio

import com.forge.hypertrophy.domain.model.CardioActivity
import com.forge.hypertrophy.domain.model.CardioStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioFieldSpecTest {
    @Test
    fun runningKeepsGpsStyleAndShoe() {
        val spec = cardioFieldSpec(CardioActivity.RUNNING)
        assertTrue(spec.allowsGps)
        assertTrue(spec.showsStyle)
        assertTrue(spec.gearIsShoe)
        assertTrue(spec.distanceRequired)
        assertEquals(CardioDistanceUnit.KM, spec.distanceUnit)
    }

    @Test
    fun swimmingUsesMetersLapsAndNoGps() {
        val spec = cardioFieldSpec(CardioActivity.SWIMMING)
        assertFalse(spec.allowsGps)
        assertTrue(spec.countIsLaps)
        assertEquals(CardioDistanceUnit.M, spec.distanceUnit)
    }

    @Test
    fun validRunningEntryPasses() {
        assertNull(
            validateCardioEntry(
                CardioEntryInput(
                    activity = CardioActivity.RUNNING,
                    style = CardioStyle.SPRINT,
                    customName = "",
                    distanceM = 1000.0,
                    durationSec = 300,
                    elevationM = null,
                    count = null,
                ),
            ),
        )
    }

    @Test
    fun customRequiresName() {
        assertEquals(
            CardioEntryError.CUSTOM_NAME,
            validateCardioEntry(
                CardioEntryInput(
                    activity = CardioActivity.CUSTOM,
                    style = CardioStyle.NONE,
                    customName = "  ",
                    distanceM = null,
                    durationSec = 600,
                    elevationM = null,
                    count = null,
                ),
            ),
        )
    }

    @Test
    fun nonRunningRejectsRunningStyle() {
        assertEquals(
            CardioEntryError.STYLE,
            validateCardioEntry(
                CardioEntryInput(
                    activity = CardioActivity.CYCLING,
                    style = CardioStyle.JOG,
                    customName = "",
                    distanceM = 5000.0,
                    durationSec = 900,
                    elevationM = null,
                    count = null,
                ),
            ),
        )
    }

    @Test
    fun defaultStyleMatchesActivity() {
        assertEquals(CardioStyle.JOG, defaultStyleFor(CardioActivity.RUNNING))
        assertEquals(CardioStyle.NONE, defaultStyleFor(CardioActivity.SWIMMING))
    }
}
