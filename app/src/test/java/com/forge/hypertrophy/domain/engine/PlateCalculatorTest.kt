package com.forge.hypertrophy.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PlateCalculatorTest {
    private val calculator = PlateCalculator()
    private val inventory = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    @Test
    fun loadsPairsThatFitTheTarget() {
        val load = calculator.load(100.0, 20.0, inventory)
        assertEquals(20.0, load.barKg, 0.0)
        assertEquals(listOf(25.0, 15.0), load.platesPerSideKg)
        assertEquals(100.0, load.totalKg, 0.0)
        assertEquals(100.0, load.requestedKg, 0.0)
        assertEquals(true, load.exactMatch)
    }

    @Test
    fun leavesRemainderWhenTheInventoryCannotFinishTheTarget() {
        val load = calculator.load(102.5, 20.0, listOf(25.0, 10.0))
        assertEquals(listOf(25.0, 10.0), load.platesPerSideKg)
        assertEquals(90.0, load.totalKg, 0.0)
        assertEquals(102.5, load.requestedKg, 0.0)
        assertEquals(false, load.exactMatch)
    }

    @Test
    fun shortfallIsVisibleWhenTheTargetCannotBeLoaded() {
        val load = calculator.load(100.0, 20.0, listOf(25.0))
        assertEquals(listOf(25.0), load.platesPerSideKg)
        assertEquals(70.0, load.totalKg, 0.0)
        assertEquals(100.0, load.requestedKg, 0.0)
        assertEquals(false, load.exactMatch)
    }

    @Test
    fun usesEachPlateEntryOnce() {
        val load = calculator.load(100.0, 20.0, listOf(20.0, 20.0))
        assertEquals(listOf(20.0, 20.0), load.platesPerSideKg)
        assertEquals(100.0, load.totalKg, 0.0)
        assertEquals(true, load.exactMatch)
    }
}
