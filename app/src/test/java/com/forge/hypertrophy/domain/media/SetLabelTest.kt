package com.forge.hypertrophy.domain.media

import org.junit.Assert.assertEquals
import org.junit.Test

class SetLabelTest {
    @Test
    fun holdUsesSeconds() {
        assertEquals("Tuck Planche: 15s", setLabel("Tuck Planche", weightKg = null, reps = null, holdSec = 15))
    }

    @Test
    fun weightedSetUsesKgAndReps() {
        assertEquals("Deadlift 140kg × 5", setLabel("Deadlift", weightKg = 140.0, reps = 5, holdSec = null))
    }

    @Test
    fun fractionalKgKeepsOneDecimal() {
        assertEquals("Press 62.5kg × 8", setLabel("Press", weightKg = 62.5, reps = 8, holdSec = null))
    }

    @Test
    fun bodyweightSetShowsRepsOnly() {
        assertEquals("Pull-up × 8", setLabel("Pull-up", weightKg = null, reps = 8, holdSec = null))
    }

    @Test
    fun nothingLoggedFallsBackToTheName() {
        assertEquals("Row", setLabel("Row", weightKg = null, reps = null, holdSec = null))
    }
}
