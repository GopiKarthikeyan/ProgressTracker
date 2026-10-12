package com.forge.hypertrophy.domain.exercise

import com.forge.hypertrophy.domain.model.SlotCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCategoryAffinityTest {
    @Test
    fun skillMatchesHandstandAndSkillId() {
        assertTrue(
            ExerciseCategoryAffinity.matches(SlotCategory.SKILL, "Handstand Practice", 1L, emptyList()),
        )
        assertTrue(
            ExerciseCategoryAffinity.matches(SlotCategory.SKILL, "Planche", null, listOf("CORE")),
        )
        assertFalse(
            ExerciseCategoryAffinity.matches(SlotCategory.SKILL, "Barbell Deadlift", null, listOf("HAMSTRINGS")),
        )
    }

    @Test
    fun coreMatchesCoreMuscleAndCoreMoves() {
        assertTrue(
            ExerciseCategoryAffinity.matches(SlotCategory.CORE, "Ab Rollout", null, listOf("CORE")),
        )
        assertTrue(
            ExerciseCategoryAffinity.matches(SlotCategory.CORE, "Pallof Press", null, listOf("CORE")),
        )
        assertFalse(
            ExerciseCategoryAffinity.matches(SlotCategory.CORE, "DB Lateral Raise", null, listOf("SHOULDERS_SIDE")),
        )
    }

    @Test
    fun isolationMatchesRaisesAndCurlsNotCompounds() {
        assertTrue(
            ExerciseCategoryAffinity.matches(
                SlotCategory.ISOLATION,
                "DB Lateral Raise",
                null,
                listOf("SHOULDERS_SIDE"),
            ),
        )
        assertTrue(
            ExerciseCategoryAffinity.matches(
                SlotCategory.ISOLATION,
                "Hamstring Curl",
                null,
                listOf("HAMSTRINGS"),
            ),
        )
        assertFalse(
            ExerciseCategoryAffinity.matches(
                SlotCategory.ISOLATION,
                "Barbell Back Squat",
                null,
                listOf("QUADS"),
            ),
        )
    }

    @Test
    fun compoundMatchesSquatsAndPressesNotIsolations() {
        assertTrue(
            ExerciseCategoryAffinity.matches(
                SlotCategory.COMPOUND,
                "Barbell Deadlift",
                null,
                listOf("HAMSTRINGS", "GLUTES"),
            ),
        )
        assertTrue(
            ExerciseCategoryAffinity.matches(
                SlotCategory.COMPOUND,
                "Weighted Pull-up",
                null,
                listOf("BACK_LATS"),
            ),
        )
        assertFalse(
            ExerciseCategoryAffinity.matches(
                SlotCategory.COMPOUND,
                "DB Lateral Raise",
                null,
                listOf("SHOULDERS_SIDE"),
            ),
        )
    }

    @Test
    fun prepAndCooldownMatchMobilityNames() {
        assertTrue(
            ExerciseCategoryAffinity.matches(SlotCategory.PREP, "Wrist Rocks", null, emptyList()),
        )
        assertTrue(
            ExerciseCategoryAffinity.matches(SlotCategory.COOLDOWN, "Couch Stretch", null, emptyList()),
        )
        assertTrue(
            ExerciseCategoryAffinity.matches(SlotCategory.MOBILITY, "90/90 Hip Swaps", null, emptyList()),
        )
    }

    @Test
    fun cardioDoesNotMatchBarbellRow() {
        assertFalse(
            ExerciseCategoryAffinity.matches(
                SlotCategory.CARDIO,
                "Barbell Row",
                null,
                listOf("BACK_UPPER"),
            ),
        )
        assertTrue(
            ExerciseCategoryAffinity.matches(SlotCategory.CARDIO, "Rowing", null, emptyList()),
        )
    }

    @Test
    fun frontLeverAllowedInSkillAndCore() {
        assertTrue(
            ExerciseCategoryAffinity.matches(SlotCategory.SKILL, "Front Lever", 2L, listOf("CORE")),
        )
        assertTrue(
            ExerciseCategoryAffinity.matches(SlotCategory.CORE, "Front Lever", 2L, listOf("CORE")),
        )
    }
}
