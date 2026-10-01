package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.SetType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReviewCalculatorTest {
    private val calculator = WeeklyReviewCalculator()
    private val monday = LocalDate.of(2026, 9, 28)

    private fun lift(date: LocalDate, exercise: Long, weight: Double, reps: Int) =
        DatedLift(date, LiftSample(exerciseId = exercise, slotId = 1, weightKg = weight, reps = reps))

    private fun set(date: LocalDate, primary: List<String>, secondary: List<String> = emptyList(), type: SetType = SetType.WORKING) =
        DatedVolumeSet(date, VolumeSet(type, primary, secondary))

    @Test
    fun weekRunsMondayToSundayFromAnyDayInside() {
        val review = calculator.review(monday.plusDays(3), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(monday, review.weekStart)
        assertEquals(LocalDate.of(2026, 10, 4), review.weekEnd)
        assertEquals(monday, reviewWeekStart(LocalDate.of(2026, 10, 4)))
    }

    @Test
    fun sessionsAreCountedForThisWeekAndTheWeekBefore() {
        val dates = listOf(
            monday.minusDays(7), monday.minusDays(3), // last week
            monday, monday.plusDays(2), monday.plusDays(6), // this week
            monday.plusDays(7), // next week
        )
        val review = calculator.review(monday, dates, emptyList(), emptyList(), emptyList())
        assertEquals(3, review.sessionsCompleted)
        assertEquals(2, review.previousSessionsCompleted)
    }

    @Test
    fun firstEverLiftIsNotAPrButBeatingHistoryIs() {
        val lifts = listOf(
            lift(monday.minusDays(10), exercise = 1, weight = 100.0, reps = 5),
            lift(monday.plusDays(1), exercise = 1, weight = 105.0, reps = 5),
            lift(monday.plusDays(1), exercise = 2, weight = 60.0, reps = 8),
        )
        val review = calculator.review(monday, emptyList(), lifts, emptyList(), emptyList())
        val pr = review.prs.single()
        assertEquals(1L, pr.exerciseId)
        assertEquals(105.0, pr.weightKg, 0.0)
        assertEquals(5, pr.reps)
        assertEquals(122.5, pr.e1rmKg, 0.001)
    }

    @Test
    fun onlyTheBestPrOfTheWeekIsKeptPerExercise() {
        val lifts = listOf(
            lift(monday.minusDays(10), exercise = 1, weight = 100.0, reps = 5),
            lift(monday.plusDays(1), exercise = 1, weight = 102.5, reps = 5),
            lift(monday.plusDays(3), exercise = 1, weight = 102.5, reps = 7),
            lift(monday.plusDays(5), exercise = 1, weight = 102.5, reps = 6),
        )
        val review = calculator.review(monday, emptyList(), lifts, emptyList(), emptyList())
        val pr = review.prs.single()
        assertEquals(monday.plusDays(3), pr.date)
        assertEquals(7, pr.reps)
    }

    @Test
    fun liftsAfterTheWeekDoNotCountAndEqualEstimatesAreNotPrs() {
        val lifts = listOf(
            lift(monday.minusDays(10), exercise = 1, weight = 100.0, reps = 5),
            lift(monday.plusDays(2), exercise = 1, weight = 100.0, reps = 5),
            lift(monday.plusDays(8), exercise = 1, weight = 140.0, reps = 5),
        )
        val review = calculator.review(monday, emptyList(), lifts, emptyList(), emptyList())
        assertTrue(review.prs.isEmpty())
    }

    @Test
    fun bodyweightLiftsNeverProducePrs() {
        val lifts = listOf(
            DatedLift(monday.minusDays(7), LiftSample(1, 1, null, 8)),
            DatedLift(monday.plusDays(1), LiftSample(1, 1, null, 12)),
        )
        val review = calculator.review(monday, emptyList(), lifts, emptyList(), emptyList())
        assertTrue(review.prs.isEmpty())
    }

    @Test
    fun volumeComparesBothWeeksAndFillsMissingMusclesWithZero() {
        val sets = listOf(
            set(monday.minusDays(5), listOf("chest"), listOf("triceps")),
            set(monday.minusDays(5), listOf("chest")),
            set(monday.plusDays(1), listOf("chest")),
            set(monday.plusDays(1), listOf("back")),
            set(monday.plusDays(1), listOf("back"), type = SetType.WARMUP),
        )
        val review = calculator.review(monday, emptyList(), emptyList(), sets, emptyList())
        assertEquals(
            listOf(
                MuscleWeekComparison("back", 1.0, 0.0),
                MuscleWeekComparison("chest", 1.0, 2.0),
                MuscleWeekComparison("triceps", 0.0, 0.5),
            ),
            review.volume,
        )
        assertEquals(-1.0, review.volume[1].delta, 0.0)
    }

    @Test
    fun stageAdvancementsAreFilteredToTheWeekAndOrdered() {
        val advancements = listOf(
            StageAdvancement(skillId = 2, date = monday.plusDays(4), fromTier = 0, fromStage = 2, toTier = 0, toStage = 3),
            StageAdvancement(skillId = 1, date = monday.plusDays(1), fromTier = 1, fromStage = 3, toTier = 2, toStage = 1),
            StageAdvancement(skillId = 1, date = monday.minusDays(1), fromTier = 1, fromStage = 2, toTier = 1, toStage = 3),
        )
        val review = calculator.review(monday, emptyList(), emptyList(), emptyList(), advancements)
        assertEquals(listOf(1L, 2L), review.stageAdvancements.map { it.skillId })
        assertEquals(monday.plusDays(1), review.stageAdvancements.first().date)
    }
}
