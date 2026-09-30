package com.forge.hypertrophy.data.transfer

import com.forge.hypertrophy.domain.model.ScheduleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramJsonValidatorTest {
    @Test
    fun eachIssueKindHasOneRow() {
        val rows = issueRows()
        assertEquals(ProgramJsonIssueKind.entries.toSet(), rows.map { it.kind }.toSet())
        assertEquals(rows.size, rows.map { it.kind }.distinct().size)
        assertEquals(17, rows.count { it.kind.severity == ProgramJsonSeverity.ERROR })
        assertEquals(3, rows.count { it.kind.severity == ProgramJsonSeverity.WARNING })

        rows.forEach { row ->
            val report = ProgramJsonValidator.validate(row.document, row.scheduleMode)
            val matched = if (row.kind.severity == ProgramJsonSeverity.ERROR) report.errors else report.warnings
            val other = if (row.kind.severity == ProgramJsonSeverity.ERROR) report.warnings else report.errors
            assertEquals(row.kind.name, listOf(row.kind), matched.map { it.kind })
            assertTrue(row.kind.name, other.isEmpty())
        }
    }

    @Test
    fun rollingModeAllowsARepeatedWeekday() {
        val document = programDocument(
            days = listOf(
                programDay(key = "a", weekday = "MONDAY", sequence = 0),
                programDay(key = "b", weekday = "MONDAY", sequence = 1, slots = listOf(programSlot(key = "s2"))),
            ),
        )

        val report = ProgramJsonValidator.validate(document, ScheduleMode.ROLLING)

        assertTrue(report.errors.isEmpty())
        assertTrue(report.warnings.isEmpty())
    }

    private data class Row(
        val kind: ProgramJsonIssueKind,
        val document: ProgramJson,
        val scheduleMode: ScheduleMode = ScheduleMode.FIXED,
    )

    private fun issueRows(): List<Row> = listOf(
        Row(
            ProgramJsonIssueKind.UNSUPPORTED_SCHEMA_VERSION,
            programDocument(schemaVersion = 2),
        ),
        Row(
            ProgramJsonIssueKind.DUPLICATE_KEY,
            programDocument(exercises = listOf(programExercise("squat"), programExercise("squat", name = "Other"))),
        ),
        Row(
            ProgramJsonIssueKind.DANGLING_EXERCISE,
            programDocument(days = listOf(programDay(slots = listOf(programSlot(exerciseKey = "missing"))))),
        ),
        Row(
            ProgramJsonIssueKind.DANGLING_SKILL,
            programDocument(exercises = listOf(programExercise("squat", skillKey = "missing"))),
        ),
        Row(
            ProgramJsonIssueKind.DANGLING_SKILL_STEP,
            programDocument(days = listOf(programDay(slots = listOf(programSlot(stepKey = "missing"))))),
        ),
        Row(
            ProgramJsonIssueKind.DANGLING_ALTERNATIVE,
            programDocument(days = listOf(programDay(slots = listOf(programSlot(alternatives = listOf("missing")))))),
        ),
        Row(
            ProgramJsonIssueKind.DANGLING_INITIAL_STEP,
            programDocument(skills = listOf(programSkill("lever", stepKey = "tuck", initialStepKey = "missing"))),
        ),
        Row(
            ProgramJsonIssueKind.SETS_RANGE,
            programDocument(days = listOf(programDay(slots = listOf(programSlot(setsMin = 5, setsMax = 2))))),
        ),
        Row(
            ProgramJsonIssueKind.REPS_RANGE,
            programDocument(days = listOf(programDay(slots = listOf(programSlot(repsLow = 12, repsHigh = 8))))),
        ),
        Row(
            ProgramJsonIssueKind.REST_RANGE,
            programDocument(days = listOf(programDay(slots = listOf(programSlot(restMin = 120, restMax = 30))))),
        ),
        Row(
            ProgramJsonIssueKind.HOLD_RANGE,
            programDocument(days = listOf(programDay(slots = listOf(programSlot(hold = 30, holdMax = 10))))),
        ),
        Row(
            ProgramJsonIssueKind.DUPLICATE_WEEKDAY,
            programDocument(
                days = listOf(
                    programDay(key = "a", weekday = "MONDAY", sequence = 0),
                    programDay(key = "b", weekday = "MONDAY", sequence = 1, slots = listOf(programSlot(key = "s2"))),
                ),
            ),
        ),
        Row(
            ProgramJsonIssueKind.DUPLICATE_SEQUENCE,
            programDocument(
                days = listOf(
                    programDay(key = "a", weekday = "MONDAY", sequence = 0),
                    programDay(key = "b", weekday = "TUESDAY", sequence = 0, slots = listOf(programSlot(key = "s2"))),
                ),
            ),
        ),
        Row(
            ProgramJsonIssueKind.UNKNOWN_ENUM,
            programDocument(exercises = listOf(programExercise("squat", equipment = "SPOON"))),
        ),
        Row(
            ProgramJsonIssueKind.MISSING_WEEKDAY,
            programDocument(days = listOf(programDay(weekday = null))),
        ),
        Row(
            ProgramJsonIssueKind.MULTIPLE_CARDIO,
            programDocument(
                days = listOf(
                    programDay(
                        cardio = listOf(
                            ProgramJsonCardio(type = "JOG", label = "Jog"),
                            ProgramJsonCardio(type = "WALK", label = "Walk"),
                        ),
                    ),
                ),
            ),
        ),
        Row(
            ProgramJsonIssueKind.UNKNOWN_SUPERSET,
            programDocument(days = listOf(programDay(slots = listOf(programSlot(superset = "AB"))))),
        ),
        Row(
            ProgramJsonIssueKind.SUPERSET_SINGLE_MEMBER,
            programDocument(days = listOf(programDay(slots = listOf(programSlot(superset = "A"))))),
        ),
        Row(
            ProgramJsonIssueKind.REST_DAY_HAS_SLOTS,
            programDocument(days = listOf(programDay(rest = true))),
        ),
        Row(
            ProgramJsonIssueKind.ALTERNATIVE_IS_SELF,
            programDocument(days = listOf(programDay(slots = listOf(programSlot(alternatives = listOf("squat")))))),
        ),
    )
}
