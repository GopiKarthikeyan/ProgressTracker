package com.forge.hypertrophy.domain.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleGroupOrderTest {
    @Test
    fun primarySortKeyUsesFirstKnownMuscle() {
        assertEquals("CHEST", MuscleGroupOrder.primarySortKey(listOf("CHEST", "TRICEPS")))
        assertEquals(MuscleGroupOrder.OTHER, MuscleGroupOrder.primarySortKey(emptyList()))
        assertEquals(MuscleGroupOrder.OTHER, MuscleGroupOrder.primarySortKey(listOf("UNKNOWN")))
    }

    @Test
    fun compareOrdersChestBeforeBiceps() {
        assertTrue(
            MuscleGroupOrder.compare(listOf("CHEST"), listOf("BICEPS")) < 0,
        )
        assertTrue(
            MuscleGroupOrder.compare(listOf("CALVES"), listOf("QUADS")) > 0,
        )
    }
}
