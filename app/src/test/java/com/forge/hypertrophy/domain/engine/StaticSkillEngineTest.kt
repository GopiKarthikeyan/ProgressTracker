package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.SkillPosition
import com.forge.hypertrophy.domain.model.SkillStageTargets
import org.junit.Assert.assertEquals
import org.junit.Test

class StaticSkillEngineTest {
    private val engine = StaticSkillEngine()
    private val targets = SkillStageTargets()

    @Test
    fun stageTable() {
        cases.forEach { case ->
            val actual = when (case.command) {
                Command.ATTEMPT -> engine.afterAttempt(
                    position = case.start,
                    targets = targets,
                    totalHoldSec = case.totalHoldSec,
                    unbrokenHoldSec = case.unbrokenHoldSec,
                    formConfirmed = case.formConfirmed,
                    tierCount = case.tierCount,
                )
                Command.PROMOTE -> engine.promote(case.start, case.tierCount)
                Command.DEMOTE -> engine.demote(case.start)
            }
            assertEquals(case.name, case.expected, actual)
        }
    }

    private enum class Command { ATTEMPT, PROMOTE, DEMOTE }

    private data class Case(
        val name: String,
        val command: Command,
        val start: SkillPosition,
        val expected: SkillPosition,
        val totalHoldSec: Int = 0,
        val unbrokenHoldSec: Int = 0,
        val formConfirmed: Boolean = false,
        val tierCount: Int = 3,
    )

    private val cases = listOf(
        Case(
            name = "stage 1 to 2",
            command = Command.ATTEMPT,
            start = SkillPosition(0, 1),
            totalHoldSec = 12,
            formConfirmed = true,
            expected = SkillPosition(0, 2),
        ),
        Case(
            name = "stage 2 to 3",
            command = Command.ATTEMPT,
            start = SkillPosition(0, 2),
            totalHoldSec = 18,
            formConfirmed = true,
            expected = SkillPosition(0, 3),
        ),
        Case(
            name = "stage 3 to next tier",
            command = Command.ATTEMPT,
            start = SkillPosition(0, 3),
            unbrokenHoldSec = 10,
            formConfirmed = true,
            expected = SkillPosition(1, 1),
        ),
        Case(
            name = "target met without form tap does not advance",
            command = Command.ATTEMPT,
            start = SkillPosition(0, 1),
            totalHoldSec = 12,
            formConfirmed = false,
            expected = SkillPosition(0, 1),
        ),
        Case(
            name = "form tap without the target does not advance",
            command = Command.ATTEMPT,
            start = SkillPosition(0, 1),
            totalHoldSec = 11,
            formConfirmed = true,
            expected = SkillPosition(0, 1),
        ),
        Case(
            name = "stage 2 low end does not advance",
            command = Command.ATTEMPT,
            start = SkillPosition(0, 2),
            totalHoldSec = 15,
            formConfirmed = true,
            expected = SkillPosition(0, 2),
        ),
        Case(
            name = "stage 2 target without form stays",
            command = Command.ATTEMPT,
            start = SkillPosition(0, 2),
            totalHoldSec = 18,
            formConfirmed = false,
            expected = SkillPosition(0, 2),
        ),
        Case(
            name = "stage 3 target without form stays",
            command = Command.ATTEMPT,
            start = SkillPosition(0, 3),
            unbrokenHoldSec = 10,
            formConfirmed = false,
            expected = SkillPosition(0, 3),
        ),
        Case(
            name = "stage 3 short of unbroken stays",
            command = Command.ATTEMPT,
            start = SkillPosition(0, 3),
            unbrokenHoldSec = 9,
            formConfirmed = true,
            expected = SkillPosition(0, 3),
        ),
        Case(
            name = "manual promote",
            command = Command.PROMOTE,
            start = SkillPosition(0, 1),
            expected = SkillPosition(0, 2),
        ),
        Case(
            name = "manual promote from stage 3",
            command = Command.PROMOTE,
            start = SkillPosition(0, 3),
            expected = SkillPosition(1, 1),
        ),
        Case(
            name = "manual promote clamps on the last tier",
            command = Command.PROMOTE,
            start = SkillPosition(2, 3),
            expected = SkillPosition(2, 3),
        ),
        Case(
            name = "manual demote",
            command = Command.DEMOTE,
            start = SkillPosition(0, 2),
            expected = SkillPosition(0, 1),
        ),
        Case(
            name = "demote from stage 1 drops to the previous tier",
            command = Command.DEMOTE,
            start = SkillPosition(1, 1),
            expected = SkillPosition(0, 3),
        ),
        Case(
            name = "demote from the first stage clamps",
            command = Command.DEMOTE,
            start = SkillPosition(0, 1),
            expected = SkillPosition(0, 1),
        ),
    )
}
