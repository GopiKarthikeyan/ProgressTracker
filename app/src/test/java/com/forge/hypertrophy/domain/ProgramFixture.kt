package com.forge.hypertrophy.domain

import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription
import com.forge.hypertrophy.domain.model.TrainingDay
import com.forge.hypertrophy.domain.model.TrainingSlot
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * In-memory week from the v11 program: seven days, Sunday rest, and
 * lateral raise on Monday (12–12) and Friday (10–12). Not a JSON import.
 */
object ProgramFixture {
    const val HANDSTAND = 1L
    const val DEADLIFT = 2L
    const val LATERAL_RAISE = 3L
    const val AB_ROLLOUT = 4L
    const val PALLOF = 5L
    const val BARBELL_ROW = 6L
    const val LAT_PULLDOWN = 7L
    const val HIGH_PULL = 8L
    const val BACK_SQUAT = 9L

    const val MONDAY = 1L
    const val TUESDAY = 2L
    const val WEDNESDAY = 3L
    const val THURSDAY = 4L
    const val FRIDAY = 5L
    const val SATURDAY = 6L
    const val SUNDAY = 7L

    val days: List<TrainingDay> = listOf(
        day(
            MONDAY,
            "Legs, Shoulders & Balance",
            DayOfWeek.MONDAY,
            0,
            slot(11, HANDSTAND, SlotCategory.SKILL, sets = 1, rest = null, asNeeded = true, block = 600, metric = MetricType.TIMED_BLOCK, rule = ProgressionRule.NONE),
            slot(12, DEADLIFT, SlotCategory.COMPOUND, sets = 3, rest = 180, repsLow = 5, repsHigh = 5, increment = 5.0),
            slot(13, LATERAL_RAISE, SlotCategory.ISOLATION, sets = 3, rest = 90, repsLow = 12, repsHigh = 12),
            slot(14, AB_ROLLOUT, SlotCategory.CORE, sets = 3, rest = 60, repsLow = 10, repsHigh = 15, metric = MetricType.REPS, superset = 1),
            slot(15, PALLOF, SlotCategory.CORE, sets = 3, rest = 60, metric = MetricType.HOLD, rule = ProgressionRule.NONE, superset = 1),
        ),
        day(
            TUESDAY,
            "Pull (Vertical), Lever & Scapula",
            DayOfWeek.TUESDAY,
            1,
            slot(21, BARBELL_ROW, SlotCategory.COMPOUND, sets = 3, rest = 180, repsLow = 6, repsHigh = 8),
            slot(22, LAT_PULLDOWN, SlotCategory.COMPOUND, sets = 3, rest = 120, repsLow = 8, repsHigh = 10, optional = true),
            slot(23, HIGH_PULL, SlotCategory.ISOLATION, sets = 3, rest = 90, repsLow = 10, repsHigh = 12),
        ),
        day(
            WEDNESDAY,
            "Push & Planche",
            DayOfWeek.WEDNESDAY,
            2,
            slot(31, DEADLIFT, SlotCategory.COMPOUND, sets = 3, rest = 180, repsLow = 5, repsHigh = 5),
        ),
        day(
            THURSDAY,
            "Pull (Horizontal) & OAP Maintenance",
            DayOfWeek.THURSDAY,
            3,
            slot(41, BARBELL_ROW, SlotCategory.COMPOUND, sets = 3, rest = 180, repsLow = 6, repsHigh = 8),
        ),
        day(
            FRIDAY,
            "Legs, Shoulders & Heavy Hinge",
            DayOfWeek.FRIDAY,
            4,
            slot(51, BACK_SQUAT, SlotCategory.COMPOUND, sets = 3, rest = 180, repsLow = 5, repsHigh = 5),
            slot(52, LATERAL_RAISE, SlotCategory.ISOLATION, sets = 3, rest = 90, repsLow = 10, repsHigh = 12),
        ),
        day(
            SATURDAY,
            "Push & Heavy Dips",
            DayOfWeek.SATURDAY,
            5,
            slot(61, BACK_SQUAT, SlotCategory.COMPOUND, sets = 3, rest = 180, repsLow = 5, repsHigh = 5),
        ),
        day(
            SUNDAY,
            "Active Rest, Deep Stretching & Recovery",
            DayOfWeek.SUNDAY,
            6,
            isRest = true,
        ),
    )

    fun snapshot(
        mode: ScheduleMode,
        rollingIndex: Int = 0,
        lastReconciled: LocalDate? = null,
        explicitCompletions: Set<LocalDate> = emptySet(),
        autoCompletedRests: Set<LocalDate> = emptySet(),
        rollingDayByDate: Map<LocalDate, Long> = emptyMap(),
        fixedSwaps: Map<LocalDate, Long> = emptyMap(),
    ) = ScheduleSnapshot(
        mode = mode,
        days = days,
        rollingIndex = rollingIndex,
        lastReconciled = lastReconciled,
        explicitCompletions = explicitCompletions,
        autoCompletedRests = autoCompletedRests,
        rollingDayByDate = rollingDayByDate,
        fixedSwaps = fixedSwaps,
    )

    private fun day(
        id: Long,
        label: String,
        dayOfWeek: DayOfWeek,
        sequence: Int,
        vararg slots: TrainingSlot,
        isRest: Boolean = false,
    ) = TrainingDay(
        id = id,
        label = label,
        dayOfWeek = dayOfWeek,
        sequenceIndex = sequence,
        isRest = isRest,
        slots = slots.toList(),
    )

    private fun slot(
        id: Long,
        exerciseId: Long,
        category: SlotCategory,
        sets: Int,
        rest: Int?,
        repsLow: Int? = null,
        repsHigh: Int? = null,
        asNeeded: Boolean = false,
        optional: Boolean = false,
        superset: Int? = null,
        block: Int? = null,
        metric: MetricType = MetricType.WEIGHT_REPS,
        rule: ProgressionRule = ProgressionRule.DOUBLE,
        increment: Double? = null,
    ) = TrainingSlot(
        id = id,
        prescription = SlotPrescription(
            exerciseId = exerciseId,
            category = category,
            sortOrder = id.toInt(),
            supersetGroup = superset,
            metricType = metric,
            setsMin = sets,
            setsMax = sets,
            repsLow = repsLow,
            repsHigh = repsHigh,
            restMinSec = rest,
            restMaxSec = rest,
            restAsNeeded = asNeeded,
            isOptional = optional,
            blockDurationSec = block,
            progressionRule = rule,
            incrementOverrideKg = increment,
        ),
    )
}
