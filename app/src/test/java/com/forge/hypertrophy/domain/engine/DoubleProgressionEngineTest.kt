package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.LoggedSet
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionAction
import com.forge.hypertrophy.domain.model.ProgressionInput
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleProgressionEngineTest {
    private val engine = DoubleProgressionEngine()

    @Test
    fun checklistCases() {
        cases.forEach { case ->
            val suggestion = engine.suggest(case.input)
            assertEquals(case.name, case.action, suggestion.action)
            if (case.weightKg == null) {
                assertNull(case.name, suggestion.weightKg)
            } else {
                assertEquals(case.name, case.weightKg, suggestion.weightKg!!, 0.0)
            }
        }
    }

    @Test
    fun noReturnedKilogramExceedsTwoDecimalPlaces() {
        val kilograms = mutableListOf<Double>()
        kilograms += LoadRounding.roundToDecimals(82.49999)
        kilograms += LoadRounding.roundToIncrement(82.49999, 2.5)
        kilograms += E1rmCalculator.epley(100.0, 5)!!
        val plates = PlateCalculator().load(100.0, 20.0, listOf(25.0, 15.0, 2.5))
        kilograms += plates.barKg
        kilograms += plates.totalKg
        kilograms += plates.platesPerSideKg
        WarmupRampGenerator().ramp(
            Equipment.BARBELL,
            SlotCategory.COMPOUND,
            100.0,
            20.0,
            listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
        ).forEach { step ->
            kilograms += step.weightKg
            kilograms += step.platesPerSideKg
        }
        cases.mapNotNull { engine.suggest(it.input).weightKg }.forEach { kilograms += it }
        assertTrue(kilograms.isNotEmpty())
        for (kg in kilograms) {
            assertEquals(LoadRounding.roundToDecimals(kg), kg, 0.0)
        }
        assertEquals(82.5, LoadRounding.roundToDecimals(82.49999), 0.0)
    }

    private data class Case(
        val name: String,
        val action: ProgressionAction,
        val weightKg: Double?,
        val input: ProgressionInput,
    )

    private val cases = listOf(
        Case(
            name = "top-of-range bump",
            action = ProgressionAction.INCREASE,
            weightKg = 82.5,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(session(80.0 to 10, 80.0 to 10)),
            ),
        ),
        Case(
            name = "fixed-target bump",
            action = ProgressionAction.INCREASE,
            weightKg = 82.5,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                repsLow = 8,
                repsHigh = 8,
                sessions = listOf(session(80.0 to 8, 80.0 to 8)),
            ),
        ),
        Case(
            name = "progressionRule LINEAR",
            action = ProgressionAction.INCREASE,
            weightKg = 82.5,
            input = loaded(
                rule = ProgressionRule.LINEAR,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(session(80.0 to 10, 80.0 to 10)),
            ),
        ),
        Case(
            name = "LINEAR between repsLow and repsHigh holds",
            action = ProgressionAction.HOLD,
            weightKg = null,
            input = loaded(
                rule = ProgressionRule.LINEAR,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(session(80.0 to 8, 80.0 to 8)),
            ),
        ),
        Case(
            name = "double progression inside the range holds",
            action = ProgressionAction.HOLD,
            weightKg = null,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(session(80.0 to 6, 80.0 to 6)),
            ),
        ),
        Case(
            name = "downward suggestion",
            action = ProgressionAction.DECREASE,
            weightKg = 77.5,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(session(80.0 to 4, 80.0 to 5)),
            ),
        ),
        Case(
            name = "zero-set session",
            action = ProgressionAction.INCREASE,
            weightKg = 82.5,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(
                    SlotSession(
                        listOf(LoggedSet(80.0, 10, SetType.WARMUP)),
                    ),
                    session(80.0 to 10),
                ),
            ),
        ),
        Case(
            name = "stall after 3",
            action = ProgressionAction.STALL,
            weightKg = null,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                repsLow = 6,
                repsHigh = 10,
                sessions = List(3) { session(80.0 to 8, 80.0 to 8) },
            ),
        ),
        Case(
            name = "stall when a dip is recovered",
            action = ProgressionAction.STALL,
            weightKg = null,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(
                    session(80.0 to 8),
                    session(77.5 to 8),
                    session(80.0 to 8),
                ),
            ),
        ),
        Case(
            name = "net progress does not stall",
            action = ProgressionAction.HOLD,
            weightKg = null,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(
                    session(82.5 to 8),
                    session(80.0 to 8),
                    session(80.0 to 8),
                ),
            ),
        ),
        Case(
            name = "exactly 3 sessions with progress does not stall",
            action = ProgressionAction.HOLD,
            weightKg = null,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(
                    session(85.0 to 8),
                    session(82.5 to 8),
                    session(80.0 to 8),
                ),
            ),
        ),
        Case(
            name = "back-off set uses the heaviest working set",
            action = ProgressionAction.INCREASE,
            weightKg = 82.5,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(session(80.0 to 10, 80.0 to 10, 70.0 to 8)),
            ),
        ),
        Case(
            name = "AMRAP finisher does not drag the load down",
            action = ProgressionAction.INCREASE,
            weightKg = 82.5,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(
                    SlotSession(
                        listOf(
                            LoggedSet(80.0, 10, SetType.WORKING),
                            LoggedSet(80.0, 10, SetType.WORKING),
                            LoggedSet(60.0, 12, SetType.AMRAP),
                        ),
                    ),
                ),
            ),
        ),
        Case(
            name = "cold start from another slot",
            action = ProgressionAction.COLD_START,
            weightKg = 60.0,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                repsLow = 6,
                repsHigh = 10,
                sessions = emptyList(),
                latestFromAnySlot = 60.0,
            ),
        ),
        Case(
            name = "progressionRule NONE",
            action = ProgressionAction.HOLD,
            weightKg = null,
            input = loaded(
                rule = ProgressionRule.NONE,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(session(80.0 to 10, 80.0 to 10)),
            ),
        ),
        Case(
            name = "incrementOverrideKg",
            action = ProgressionAction.INCREASE,
            weightKg = 85.0,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(session(80.0 to 10)),
                overrideKg = 5.0,
            ),
        ),
        Case(
            name = "bodyweight REPS",
            action = ProgressionAction.VARIATION_OR_ADDED_LOAD,
            weightKg = null,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                equipment = Equipment.BODYWEIGHT,
                metric = MetricType.REPS,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(session(null to 10, null to 12)),
            ),
        ),
        Case(
            name = "weighted bodyweight",
            action = ProgressionAction.INCREASE,
            weightKg = 12.5,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                equipment = Equipment.WEIGHTED_BODYWEIGHT,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(session(10.0 to 10, 10.0 to 10)),
            ),
        ),
        Case(
            name = "weighted bodyweight added load steps at top of range",
            action = ProgressionAction.INCREASE,
            weightKg = 17.5,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                equipment = Equipment.WEIGHTED_BODYWEIGHT,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(session(15.0 to 10)),
            ),
        ),
        Case(
            name = "weighted bodyweight decrease floors at 0",
            action = ProgressionAction.DECREASE,
            weightKg = 0.0,
            input = loaded(
                rule = ProgressionRule.DOUBLE,
                equipment = Equipment.WEIGHTED_BODYWEIGHT,
                repsLow = 6,
                repsHigh = 10,
                sessions = listOf(session(1.25 to 4)),
            ),
        ),
    )

    private fun loaded(
        rule: ProgressionRule,
        repsLow: Int,
        repsHigh: Int,
        sessions: List<SlotSession>,
        equipment: Equipment = Equipment.BARBELL,
        metric: MetricType = MetricType.WEIGHT_REPS,
        overrideKg: Double? = null,
        latestFromAnySlot: Double? = null,
    ) = ProgressionInput(
        rule = rule,
        equipment = equipment,
        metricType = metric,
        repsLow = repsLow,
        repsHigh = repsHigh,
        exerciseIncrementKg = 2.5,
        incrementOverrideKg = overrideKg,
        slotSessions = sessions,
        latestWeightFromAnySlotKg = latestFromAnySlot,
    )

    private fun session(vararg sets: Pair<Double?, Int>) = SlotSession(
        sets.map { (weight, reps) -> LoggedSet(weight, reps, SetType.WORKING) },
    )
}
