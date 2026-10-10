package com.forge.hypertrophy.data.transfer

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forge.hypertrophy.data.dao.DaoFixture
import com.forge.hypertrophy.data.dao.DaoTest
import com.forge.hypertrophy.data.db.AppDatabase
import com.forge.hypertrophy.domain.model.CardioActivity
import com.forge.hypertrophy.domain.model.CardioStyle
import com.forge.hypertrophy.domain.model.ChecklistPhase
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SlotCategory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

@Config(application = Application::class, sdk = [29])
class ProgramRoundTripTest : DaoTest() {
    @Test
    fun twoMemberSupersetSurvivesImportExportImport() = runBlocking {
        val importer = ProgramImporter(db, CLOCK, ProgramImportPreferences.None)
        val first = importer.import(supersetDocument(), ImportMode.ADD_PROGRAM, applyDefaults = false)
            as ImportResult.Imported
        val firstSlots = slotsOf(db, first.programId)
        val firstGroup = firstSlots.mapNotNull { it.supersetGroup }
        assertEquals(listOf(1, 1), firstGroup)

        val exported = ProgramExporter(db).export(first.programId)
        assertEquals(listOf("A", "A"), exported.days.single().slots.map { it.supersetGroup })

        val other = freshDatabase()
        try {
            val second = ProgramImporter(other, CLOCK, ProgramImportPreferences.None)
                .import(exported, ImportMode.ADD_PROGRAM, applyDefaults = false) as ImportResult.Imported
            val secondGroup = slotsOf(other, second.programId).mapNotNull { it.supersetGroup }
            assertEquals(listOf(1, 1), secondGroup)
            assertEquals(snapshot(db, first.programId), snapshot(other, second.programId))
        } finally {
            other.close()
        }
    }

    @Test
    fun slotEditLeavesExistingSnapshotUnchanged() = runBlocking {
        val fixture = DaoFixture(db)
        val exerciseId = fixture.exercise("press")
        val dayId = fixture.day(fixture.program())
        val slotId = fixture.slot(dayId, exerciseId, setsMax = 8)
        val logged = db.routineDao().getSlot(slotId)!!.copy(notes = "before", holdTargetMaxSec = 12)
        db.routineDao().updateSlot(logged)
        val sessionId = fixture.session(dayId = dayId)
        fixture.sessionSlot(sessionId, slotId, fixture.prescription(logged))

        db.routineDao().updateSlot(logged.copy(notes = "after", holdTargetMaxSec = 40, setsMax = 12))

        val snapshot = first(db.sessionDao().observeSlots(sessionId)).single().prescriptionSnapshot
        assertEquals("before", snapshot.notes)
        assertEquals(12, snapshot.holdTargetMaxSec)
        assertEquals(8, snapshot.setsMax)
        assertEquals(40, db.routineDao().getSlot(slotId)!!.holdTargetMaxSec)
    }

    private fun freshDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java,
    ).allowMainThreadQueries().build()

    private suspend fun slotsOf(database: AppDatabase, programId: Long) =
        database.routineDao().slotsForDays(database.routineDao().days(programId).map { it.id })

    private suspend fun snapshot(database: AppDatabase, programId: Long): ProgramSnap {
        val program = database.programDao().getById(programId)!!
        val days = database.routineDao().days(programId)
        val dayIds = days.map { it.id }
        val slots = database.routineDao().slotsForDays(dayIds)
        val alternatives = if (slots.isEmpty()) {
            emptyList()
        } else {
            database.routineDao().alternativesForSlots(slots.map { it.id })
        }
        val checklist = if (dayIds.isEmpty()) emptyList() else database.routineDao().checklistForDays(dayIds)
        val cardio = if (dayIds.isEmpty()) emptyList() else database.routineDao().cardioForDays(dayIds)
        val exercises = database.exerciseDao().all().filter { it.archivedAt == null }
        val skills = database.skillDao().all().filter { it.archivedAt == null }
        val exerciseName = exercises.associate { it.id to it.name }
        val skillName = skills.associate { it.id to it.name }
        val steps = skills.flatMap { skill -> database.skillDao().getSteps(skill.id) }
        val stepName = steps.associate { it.id to it.name }
        return ProgramSnap(
            name = program.name,
            active = program.isActive,
            mode = program.scheduleMode,
            exercises = exercises.sortedBy { it.name }.map { exercise ->
                ExerciseSnap(
                    name = exercise.name,
                    equipment = exercise.equipment,
                    barWeightKg = exercise.barWeightKg,
                    loadIncrementKg = exercise.loadIncrementKg,
                    unilateral = exercise.isUnilateral,
                    skill = exercise.skillId?.let { skillName[it] },
                    primary = exercise.primaryMuscleGroups,
                    secondary = exercise.secondaryMuscleGroups,
                    setupNotes = exercise.setupNotes,
                )
            },
            skills = skills.sortedBy { it.name }.map { skill ->
                SkillSnap(
                    name = skill.name,
                    steps = database.skillDao().getSteps(skill.id).sortedBy { it.sortOrder }.map { step ->
                        StepSnap(
                            name = step.name,
                            order = step.sortOrder,
                            stage1 = step.stage1TotalSec,
                            stage2Low = step.stage2TotalLowSec,
                            stage2High = step.stage2TotalHighSec,
                            stage3 = step.stage3UnbrokenSec,
                        )
                    },
                )
            },
            days = days.map { day ->
                DaySnap(
                    label = day.label,
                    weekday = day.dayOfWeek,
                    sequence = day.sequenceIndex,
                    rest = day.isRest,
                    prepMinutes = day.prepDurationMin,
                    cooldownMinutes = day.cooldownDurationMin,
                    checklist = checklist.filter { it.dayId == day.id }.map { item ->
                        CheckSnap(item.phase, item.text, item.reps, item.seconds)
                    },
                    slots = slots.filter { it.dayId == day.id }.map { slot ->
                        SlotSnap(
                            exercise = exerciseName.getValue(slot.exerciseId),
                            category = slot.category,
                            order = slot.sortOrder,
                            supersetGroup = slot.supersetGroup,
                            metric = slot.metricType,
                            setsMin = slot.setsMin,
                            setsMax = slot.setsMax,
                            repsLow = slot.repsLow,
                            repsHigh = slot.repsHigh,
                            amrap = slot.isAmrap,
                            holdTargetSec = slot.holdTargetSec,
                            holdTargetMaxSec = slot.holdTargetMaxSec,
                            blockDurationSec = slot.blockDurationSec,
                            restMinSec = slot.restMinSec,
                            restMaxSec = slot.restMaxSec,
                            restAsNeeded = slot.restAsNeeded,
                            optional = slot.isOptional,
                            skipReason = slot.skipReasonLabel,
                            step = slot.targetSkillStepId?.let { stepName[it] },
                            progression = slot.progressionRule,
                            increment = slot.incrementOverrideKg,
                            notes = slot.notes,
                            alternatives = alternatives.filter { it.slotId == slot.id }
                                .map { exerciseName.getValue(it.exerciseId) }
                                .sorted(),
                        )
                    },
                    cardio = cardio.filter { it.dayId == day.id }.sortedBy { it.label }.map { plan ->
                        CardioSnap(
                            plan.activity,
                            plan.type,
                            plan.label,
                            plan.targetDistanceM,
                            plan.isOptional,
                        )
                    },
                )
            },
        )
    }

    private fun supersetDocument() = programDocument(
        name = "Core",
        skills = listOf(
            ProgramJsonSkill(
                key = "lever",
                name = "Front Lever",
                initialStepKey = "tuck",
                steps = listOf(
                    ProgramJsonSkillStep(key = "tuck", name = "Tuck", order = 0),
                    ProgramJsonSkillStep(key = "full", name = "Full", order = 1),
                ),
            ),
        ),
        exercises = listOf(
            programExercise("roll", name = "Ab Rollout", skillKey = "lever"),
            programExercise("pallof", name = "Pallof Press"),
            programExercise("dip", name = "Dip"),
        ),
        days = listOf(
            programDay(
                slots = listOf(
                    programSlot(
                        key = "a1",
                        exerciseKey = "roll",
                        superset = "A",
                        order = 0,
                        notes = "brace",
                        hold = 15,
                        holdMax = 20,
                        stepKey = "tuck",
                    ),
                    programSlot(
                        key = "a2",
                        exerciseKey = "pallof",
                        superset = "A",
                        order = 1,
                        alternatives = listOf("dip"),
                    ),
                ),
                cardio = listOf(
                    ProgramJsonCardio(
                        activity = "RUNNING",
                        style = "JOG",
                        label = "Easy jog",
                        targetDistanceM = 2000,
                    ),
                ),
                prep = ProgramJsonChecklist(5, listOf(ProgramJsonChecklistItem("Wrist Rocks", 10, null))),
                cooldown = ProgramJsonChecklist(3, listOf(ProgramJsonChecklistItem("Breathe", null, 60))),
            ),
        ),
    )

    private data class ProgramSnap(
        val name: String,
        val active: Boolean,
        val mode: ScheduleMode,
        val exercises: List<ExerciseSnap>,
        val skills: List<SkillSnap>,
        val days: List<DaySnap>,
    )

    private data class ExerciseSnap(
        val name: String,
        val equipment: Equipment,
        val barWeightKg: Double?,
        val loadIncrementKg: Double,
        val unilateral: Boolean,
        val skill: String?,
        val primary: List<String>,
        val secondary: List<String>,
        val setupNotes: String,
    )

    private data class SkillSnap(val name: String, val steps: List<StepSnap>)

    private data class StepSnap(
        val name: String,
        val order: Int,
        val stage1: Int,
        val stage2Low: Int,
        val stage2High: Int,
        val stage3: Int,
    )

    private data class DaySnap(
        val label: String,
        val weekday: Int?,
        val sequence: Int,
        val rest: Boolean,
        val prepMinutes: Int?,
        val cooldownMinutes: Int?,
        val checklist: List<CheckSnap>,
        val slots: List<SlotSnap>,
        val cardio: List<CardioSnap>,
    )

    private data class CheckSnap(
        val phase: ChecklistPhase,
        val text: String,
        val reps: Int?,
        val seconds: Int?,
    )

    private data class SlotSnap(
        val exercise: String,
        val category: SlotCategory,
        val order: Int,
        val supersetGroup: Int?,
        val metric: MetricType,
        val setsMin: Int,
        val setsMax: Int,
        val repsLow: Int?,
        val repsHigh: Int?,
        val amrap: Boolean,
        val holdTargetSec: Int?,
        val holdTargetMaxSec: Int?,
        val blockDurationSec: Int?,
        val restMinSec: Int?,
        val restMaxSec: Int?,
        val restAsNeeded: Boolean,
        val optional: Boolean,
        val skipReason: String?,
        val step: String?,
        val progression: ProgressionRule,
        val increment: Double?,
        val notes: String?,
        val alternatives: List<String>,
    )

    private data class CardioSnap(
        val activity: CardioActivity,
        val type: CardioStyle,
        val label: String,
        val distanceM: Int?,
        val optional: Boolean,
    )

    private companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC)
    }
}
