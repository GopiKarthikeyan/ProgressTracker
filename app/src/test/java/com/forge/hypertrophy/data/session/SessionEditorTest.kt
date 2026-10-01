package com.forge.hypertrophy.data.session

import com.forge.hypertrophy.data.dao.DaoFixture
import com.forge.hypertrophy.data.dao.DaoTest
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.entity.SkillProgressEntity
import com.forge.hypertrophy.data.entity.SkillStageEventEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.RoomBaselineRepository
import com.forge.hypertrophy.data.repository.RoomExerciseRepository
import com.forge.hypertrophy.data.repository.RoomRoutineRepository
import com.forge.hypertrophy.data.repository.RoomSessionRepository
import com.forge.hypertrophy.data.repository.RoomSkillRepository
import com.forge.hypertrophy.domain.engine.E1rmCalculator
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.ProgressionAction
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SetType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorTest : DaoTest() {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)
    private val heavyDay = LocalDate.of(2026, 10, 8)
    private val earlier = LocalDate.of(2026, 10, 1)

    @Test
    fun editRecomputesPrsWithoutRevertingAConfirmedStageAdvance() = runBlocking {
        val fixture = DaoFixture(db)
        val skillId = fixture.skill("planche")
        val stepId = fixture.step(skillId)
        val exerciseId = fixture.exercise("press", skillId = skillId)
        val dayId = fixture.day(fixture.program())
        val slotId = fixture.slot(dayId, exerciseId)
        val prescription = fixture.prescription(db.routineDao().getSlot(slotId)!!)
        db.skillDao().upsertProgress(
            SkillProgressEntity(skillId = skillId, currentStepId = stepId, stage = 2, updatedAt = clock.instant()),
        )
        db.skillDao().insertStageEvent(
            SkillStageEventEntity(
                skillId = skillId,
                date = heavyDay,
                fromTier = 0,
                fromStage = 1,
                toTier = 0,
                toStage = 2,
                recordedAt = clock.instant(),
            ),
        )

        val first = session(earlier, dayId)
        val firstSlot = fixture.sessionSlot(first, slotId, prescription)
        set(firstSlot, 100.0, 5)
        val second = session(heavyDay, dayId)
        val secondSlot = fixture.sessionSlot(second, slotId, prescription)
        val heavy = set(secondSlot, 110.0, 5)

        val editor = SessionEditor(
            database = db,
            sessions = RoomSessionRepository(db.sessionDao()),
            routines = RoomRoutineRepository(db.routineDao()),
            exercises = RoomExerciseRepository(db.exerciseDao(), db.routineDao(), db.sessionDao(), db.mediaDao(), clock),
            skills = RoomSkillRepository(db.skillDao(), db.exerciseDao(), db.routineDao(), db.sessionDao(), clock),
            baselines = RoomBaselineRepository(db.baselineDao()),
            clock = clock,
        )
        val report = editor.apply(
            second,
            SessionEdit(
                date = heavyDay,
                status = SessionStatus.COMPLETED,
                sets = listOf(SetEdit(id = heavy, sessionSlotId = secondSlot, weightKg = 90.0, reps = 5)),
            ),
        )

        assertEquals(E1rmCalculator.epley(100.0, 5), report.bestE1rmKg[exerciseId])
        assertTrue(report.stageWarnings.single().contains("planche"))
        assertEquals(2, db.skillDao().getProgress(skillId)!!.stage)
        assertEquals(1, db.skillDao().stageEventsBetween(earlier, heavyDay).size)
        assertNotNull(db.sessionDao().get(second)!!.editedAt)
        val suggestion = report.suggestions.single()
        assertEquals("press", suggestion.exerciseName)
        assertEquals(ProgressionAction.DECREASE, suggestion.suggestion.action)
        assertEquals(87.5, suggestion.suggestion.weightKg!!, 0.0)
    }

    private suspend fun session(date: LocalDate, dayId: Long): Long = db.sessionDao().insert(
        WorkoutSessionEntity(
            date = date,
            dayId = dayId,
            kind = SessionKind.GYM,
            status = SessionStatus.COMPLETED,
            isDeload = false,
            isShortOnTime = false,
            readinessSleep = null,
            readinessSoreness = null,
            readinessEnergy = null,
            startedAt = null,
            completedAt = date.atStartOfDay().toInstant(ZoneOffset.UTC),
        ),
    )

    private suspend fun set(sessionSlotId: Long, weight: Double, reps: Int): Long = db.sessionDao().insertSet(
        SetEntryEntity(
            sessionSlotId = sessionSlotId,
            setNumber = 1,
            side = SetSide.BOTH,
            setType = SetType.WORKING,
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
