package com.forge.hypertrophy.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class GetPhysiqueMilestonesUseCaseTest {
    private val useCase = GetPhysiqueMilestonesUseCase(
        Clock.fixed(LocalDate.of(2026, 3, 16).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
    )

    @Test
    fun jan31PlusOneMonthClampsAndFourWeeksDoesNot() {
        assertEquals(LocalDate.of(2024, 2, 29), useCase.monthStep(LocalDate.of(2024, 1, 31), 1))
        assertEquals(LocalDate.of(2025, 2, 28), useCase.monthStep(LocalDate.of(2025, 1, 31), 1))
        val leap = useCase.milestones(
            start = LocalDate.of(2024, 1, 31),
            photos = emptyList(),
            through = LocalDate.of(2024, 3, 1),
        )
        assertEquals(LocalDate.of(2024, 2, 28), leap.first().dueOn)
        val fromAugust = useCase.milestones(
            start = LocalDate.of(2025, 8, 31),
            photos = emptyList(),
            through = LocalDate.of(2026, 3, 1),
        )
        assertEquals(LocalDate.of(2026, 2, 28), fromAugust.last().dueOn)
    }

    @Test
    fun sparseLibraryFindsThePhotoOutsideTheFirstWindow() {
        val due = LocalDate.of(2024, 1, 31).plusWeeks(4)
        val photo = due.plusDays(9)
        val milestones = useCase.milestones(
            start = LocalDate.of(2024, 1, 31),
            photos = listOf(photo),
            through = due,
        )
        assertEquals(photo, milestones.first().photoOn)
    }

    @Test
    fun emptyLibraryYieldsMilestonesWithoutPhotos() {
        val milestones = useCase.milestones(
            start = LocalDate.of(2024, 1, 31),
            photos = emptyList(),
            through = LocalDate.of(2024, 8, 1),
        )
        assertTrue(milestones.size >= 2)
        milestones.forEach { assertNull(it.photoOn) }
    }
}
