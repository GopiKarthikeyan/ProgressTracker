package com.forge.hypertrophy.ui.screens.review

import com.forge.hypertrophy.data.dao.DaoFixture
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.entity.SkillStageEventEntity
import com.forge.hypertrophy.data.entity.SkillStepEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.RoomExerciseRepository
import com.forge.hypertrophy.data.repository.RoomSessionRepository
import com.forge.hypertrophy.data.repository.RoomSkillRepository
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.ui.screens.routine.ViewModelDaoTest
import com.forge.hypertrophy.ui.screens.routine.awaitUntil
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReviewViewModelTest : ViewModelDaoTest() {
    // Thursday 2026-10-01; the review week is Mon 28 Sep – Sun 4 Oct.
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC)
    private val monday = LocalDate.of(2026, 9, 28)

    private fun review() = track(
        WeeklyReviewViewModel(
            sessions = RoomSessionRepository(db.sessionDao()),
            exercises = RoomExerciseRepository(db.exerciseDao(), db.routineDao(), db.sessionDao(), db.mediaDao(), clock),
            skills = RoomSkillRepository(db.skillDao(), db.exerciseDao(), db.routineDao(), db.sessionDao(), clock),
            clock = clock,
        ),
    )

    private suspend fun session(date: LocalDate, dayId: Long, kind: SessionKind = SessionKind.GYM): Long = db.sessionDao().insert(
        WorkoutSessionEntity(
            date = date,
            dayId = dayId,
            kind = kind,
            status = SessionStatus.COMPLETED,
            isDeload = false,
            isShortOnTime = false,
            readinessSleep = null,
            readinessSoreness = null,
            readinessEnergy = null,
            startedAt = null,
            completedAt = clock.instant(),
        ),
    )

    private suspend fun set(sessionSlotId: Long, weight: Double, reps: Int, type: SetType = SetType.WORKING) {
        db.sessionDao().insertSet(
            SetEntryEntity(
                sessionSlotId = sessionSlotId,
                setNumber = 1,
                side = SetSide.BOTH,
                setType = type,
                weightKg = weight,
                reps = reps,
                holdSec = null,
                rpe = null,
                jointFlags = emptyList(),
                entryMethod = EntryMethod.SCREEN,
                loggedAt = clock.instant(),
            ),
        )
    }

    @Test
    fun reviewCountsSessionsPrsVolumeAndStageMoves() = runBlocking {
        val fixture = DaoFixture(db)
        val exerciseId = fixture.exercise("deadlift")
        val dayId = fixture.day(fixture.program())
        val slotId = fixture.slot(dayId, exerciseId)
        val prescription = fixture.prescription(db.routineDao().getSlot(slotId)!!)

        // Last week: one session, two hard sets at 100 × 5.
        val lastWeek = session(monday.minusDays(3), dayId)
        val lastSlot = fixture.sessionSlot(lastWeek, slotId, prescription)
        set(lastSlot, 100.0, 5)
        set(lastSlot, 100.0, 5)
        // This week: two sessions, one heavier set that beats the old estimate plus a warm-up.
        val first = session(monday, dayId)
        val firstSlot = fixture.sessionSlot(first, slotId, prescription)
        set(firstSlot, 60.0, 5, SetType.WARMUP)
        set(firstSlot, 110.0, 5)
        val second = session(monday.plusDays(2), dayId)
        fixture.sessionSlot(second, slotId, prescription)
        session(monday.plusDays(3), dayId, SessionKind.REST)

        val skillId = fixture.skill("planche")
        db.skillDao().insertStep(SkillStepEntity(skillId = skillId, sortOrder = 0, name = "tuck"))
        db.skillDao().insertStep(SkillStepEntity(skillId = skillId, sortOrder = 1, name = "advanced tuck"))
        db.skillDao().insertStageEvent(
            SkillStageEventEntity(
                skillId = skillId,
                date = monday.plusDays(1),
                fromTier = 0,
                fromStage = 3,
                toTier = 1,
                toStage = 1,
                recordedAt = clock.instant(),
            ),
        )

        val viewModel = review()
        awaitUntil { viewModel.uiState.value.loaded }
        val state = viewModel.uiState.value

        assertEquals(monday, state.weekStart)
        assertEquals(monday.plusDays(6), state.weekEnd)
        assertTrue(state.isCurrentWeek)
        assertEquals(2, state.sessionsCompleted)
        assertEquals(1, state.previousSessionsCompleted)

        val pr = state.prs.single()
        assertEquals("deadlift", pr.exerciseName)
        assertEquals(110.0, pr.weightKg, 0.0)
        assertEquals(5, pr.reps)
        assertEquals(128.33, pr.e1rmKg, 0.001)

        val move = state.advancements.single()
        assertEquals("planche", move.skillName)
        assertEquals("tuck", move.fromTierName)
        assertEquals(3, move.fromStage)
        assertEquals("advanced tuck", move.toTierName)
        assertEquals(1, move.toStage)

        val back = state.volume.single()
        assertEquals("back", back.muscle)
        assertEquals(1.0, back.thisWeek, 0.0)
        assertEquals(2.0, back.lastWeek, 0.0)
    }

    @Test
    fun weekNavigationStopsAtTheCurrentWeek() = runBlocking {
        val viewModel = review()
        awaitUntil { viewModel.uiState.value.loaded }

        viewModel.onEvent(WeeklyReviewEvent.PreviousWeek)
        awaitUntil { viewModel.uiState.value.weekStart == monday.minusWeeks(1) }
        assertFalse(viewModel.uiState.value.isCurrentWeek)

        viewModel.onEvent(WeeklyReviewEvent.NextWeek)
        awaitUntil { viewModel.uiState.value.weekStart == monday }
        assertTrue(viewModel.uiState.value.isCurrentWeek)

        viewModel.onEvent(WeeklyReviewEvent.NextWeek)
        viewModel.onEvent(WeeklyReviewEvent.Refresh)
        awaitUntil { viewModel.uiState.value.loaded }
        assertEquals(monday, viewModel.uiState.value.weekStart)
    }
}
