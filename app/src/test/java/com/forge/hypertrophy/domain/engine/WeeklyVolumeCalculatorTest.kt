package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyVolumeCalculatorTest {
    @Test
    fun table() {
        val standard = WeeklyVolumeCalculator()
        cases.forEach { case ->
            val calculator = if (case.secondaryWeight == null) {
                standard
            } else {
                WeeklyVolumeCalculator(secondaryWeight = case.secondaryWeight)
            }
            assertEquals(case.name, case.expected, calculator.volume(case.sets))
        }
    }

    private data class Case(
        val name: String,
        val sets: List<VolumeSet>,
        val expected: Map<String, Double>,
        val secondaryWeight: Double? = null,
    )

    private val cases = listOf(
        Case(
            name = "primary only",
            sets = listOf(VolumeSet(SetType.WORKING, listOf("chest"), emptyList())),
            expected = mapOf("chest" to 1.0),
        ),
        Case(
            name = "secondary only",
            sets = listOf(VolumeSet(SetType.WORKING, emptyList(), listOf("triceps"))),
            expected = mapOf("triceps" to 0.5),
        ),
        Case(
            name = "primary and secondary",
            sets = listOf(VolumeSet(SetType.WORKING, listOf("back"), listOf("biceps"))),
            expected = mapOf("back" to 1.0, "biceps" to 0.5),
        ),
        Case(
            name = "custom secondary weight",
            sets = listOf(VolumeSet(SetType.WORKING, emptyList(), listOf("triceps"))),
            expected = mapOf("triceps" to 0.25),
            secondaryWeight = 0.25,
        ),
        Case(
            name = "AMRAP counts as a hard set",
            sets = listOf(VolumeSet(SetType.AMRAP, listOf("chest"), emptyList())),
            expected = mapOf("chest" to 1.0),
        ),
        Case(
            name = "warmup is not volume",
            sets = listOf(VolumeSet(SetType.WARMUP, listOf("chest"), listOf("triceps"))),
            expected = emptyMap(),
        ),
        Case(
            name = "zero sets",
            sets = emptyList(),
            expected = emptyMap(),
        ),
    )
}
