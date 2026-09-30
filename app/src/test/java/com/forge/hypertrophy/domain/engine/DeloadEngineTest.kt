package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.LoggedSet
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionAction
import com.forge.hypertrophy.domain.model.ProgressionInput
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.model.SlotSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeloadEngineTest {
    private val engine = DeloadEngine()

    @Test
    fun prescriptionTable() {
        prescriptions.forEach { case ->
            val actual = engine.prescribe(case.baseline)
            assertEquals(case.name, ProgressionAction.HOLD, actual.progression)
            if (case.weightKg == null) {
                assertNull(case.name, actual.weightKg)
            } else {
                assertEquals(case.name, case.weightKg, actual.weightKg!!, 0.0)
            }
            assertEquals(case.name, case.holdSec, actual.holdSec)
        }
    }

    @Test
    fun lifecycleTable() {
        assertEquals(82.5, engine.restoredWeightKg(82.5), 0.0)
        assertEquals(
            65.0,
            engine.prescribe(
                loaded(Equipment.BARBELL, MetricType.WEIGHT_REPS, 82.5, 2.5),
            ).weightKg!!,
            0.0,
        )

        assertTrue(engine.rotationFinished(completedTrainingDays = 4, daysInRotation = 4))
        assertFalse(engine.rotationFinished(completedTrainingDays = 3, daysInRotation = 4))

        val baseline = engine.lastNonDeload(
            listOf(
                DeloadHistoryEntry(duringDeload = true, weightKg = 65.0, holdSec = null),
                DeloadHistoryEntry(duringDeload = false, weightKg = 82.5, holdSec = null),
                DeloadHistoryEntry(duringDeload = false, weightKg = 80.0, holdSec = null),
            ),
        )
        assertEquals(82.5, baseline!!.weightKg!!, 0.0)
    }

    @Test
    fun noPriorNonDeloadSessionHasNoBaseline() {
        val rows = listOf(
            "only deload sessions" to listOf(
                DeloadHistoryEntry(duringDeload = true, weightKg = 40.0, holdSec = null),
            ),
        )
        rows.forEach { (name, history) ->
            assertNull(name, engine.lastNonDeload(history))
        }
    }

    @Test
    fun endingADeloadDoesNotRefireStall() {
        val progression = DoubleProgressionEngine()
        val flat = List(3) { working(80.0, 8) }
        val before = progression.suggest(input(flat))
        assertEquals(ProgressionAction.STALL, before.action)

        val afterDeload = engine.postDeloadSessions(
            listOf(
                TaggedSlotSession(working(80.0, 8), duringDeload = false),
                TaggedSlotSession(working(65.0, 8), duringDeload = true),
                TaggedSlotSession(working(80.0, 8), duringDeload = false),
                TaggedSlotSession(working(80.0, 8), duringDeload = false),
                TaggedSlotSession(working(80.0, 8), duringDeload = false),
            ),
        )
        val after = progression.suggest(input(afterDeload))
        assertEquals(ProgressionAction.HOLD, after.action)
    }

    private data class PrescribeCase(
        val name: String,
        val baseline: DeloadBaseline,
        val weightKg: Double?,
        val holdSec: Int?,
    )

    private val prescriptions = listOf(
        PrescribeCase(
            name = "loaded compound -20%",
            baseline = loaded(Equipment.BARBELL, MetricType.WEIGHT_REPS, 100.0, 2.5),
            weightKg = 80.0,
            holdSec = null,
        ),
        PrescribeCase(
            name = "loaded weight rounds to the increment",
            baseline = loaded(Equipment.BARBELL, MetricType.WEIGHT_REPS, 82.5, 2.5),
            weightKg = 65.0,
            holdSec = null,
        ),
        PrescribeCase(
            name = "static hold -50%",
            baseline = loaded(Equipment.BODYWEIGHT, MetricType.HOLD, null, 2.5, holdSec = 20),
            weightKg = null,
            holdSec = 10,
        ),
        PrescribeCase(
            name = "static hold half second rounds half up",
            baseline = loaded(Equipment.BODYWEIGHT, MetricType.HOLD, null, 2.5, holdSec = 25),
            weightKg = null,
            holdSec = 13,
        ),
        PrescribeCase(
            name = "weighted bodyweight cuts added load only",
            baseline = loaded(Equipment.WEIGHTED_BODYWEIGHT, MetricType.WEIGHT_REPS, 50.0, 5.0),
            weightKg = 40.0,
            holdSec = null,
        ),
        PrescribeCase(
            name = "progression frozen",
            baseline = loaded(Equipment.BARBELL, MetricType.WEIGHT_REPS, 50.0, 2.5),
            weightKg = 40.0,
            holdSec = null,
        ),
    )

    private fun loaded(
        equipment: Equipment,
        metric: MetricType,
        weightKg: Double?,
        incrementKg: Double,
        holdSec: Int? = null,
    ) = DeloadBaseline(
        equipment = equipment,
        metricType = metric,
        weightKg = weightKg,
        holdSec = holdSec,
        incrementKg = incrementKg,
    )

    private fun working(weightKg: Double, reps: Int) = SlotSession(
        listOf(LoggedSet(weightKg, reps, SetType.WORKING)),
    )

    private fun input(sessions: List<SlotSession>) = ProgressionInput(
        rule = ProgressionRule.DOUBLE,
        equipment = Equipment.BARBELL,
        metricType = MetricType.WEIGHT_REPS,
        repsLow = 6,
        repsHigh = 10,
        exerciseIncrementKg = 2.5,
        incrementOverrideKg = null,
        slotSessions = sessions,
        latestWeightFromAnySlotKg = null,
    )
}
