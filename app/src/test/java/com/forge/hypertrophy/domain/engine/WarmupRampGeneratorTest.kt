package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.SlotCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmupRampGeneratorTest {
    private val generator = WarmupRampGenerator()
    private val inventory = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    @Test
    fun barbellCompoundRampsThroughRoundedPercentages() {
        val steps = generator.ramp(
            equipment = Equipment.BARBELL,
            category = SlotCategory.COMPOUND,
            workingKg = 100.0,
            barKg = 20.0,
            inventoryPerSideKg = inventory,
        )
        assertEquals(listOf(20.0, 50.0, 70.0, 85.0), steps.map { it.weightKg })
        assertEquals(listOf(10, 5, 3, 1), steps.map { it.reps })
        assertEquals(emptyList<Double>(), steps.first().platesPerSideKg)
        assertEquals(listOf(25.0, 5.0, 2.5), steps.last().platesPerSideKg)
    }

    @Test
    fun skipsAPercentageThatCollapsesOntoTheBar() {
        val steps = generator.ramp(
            equipment = Equipment.BARBELL,
            category = SlotCategory.COMPOUND,
            workingKg = 40.0,
            barKg = 20.0,
            inventoryPerSideKg = inventory,
        )
        assertEquals(listOf(20.0, 27.5, 32.5), steps.map { it.weightKg })
        assertEquals(listOf(10, 3, 1), steps.map { it.reps })
    }

    @Test
    fun nonBarbellAndNonCompoundHaveNoRamp() {
        assertTrue(
            generator.ramp(
                Equipment.DUMBBELL,
                SlotCategory.COMPOUND,
                40.0,
                0.0,
                inventory,
            ).isEmpty(),
        )
        assertTrue(
            generator.ramp(
                Equipment.BARBELL,
                SlotCategory.ISOLATION,
                100.0,
                20.0,
                inventory,
            ).isEmpty(),
        )
    }
}
