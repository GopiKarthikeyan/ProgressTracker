package com.forge.hypertrophy.domain.dayplan

import com.forge.hypertrophy.domain.model.ChecklistPhase
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription
import com.forge.hypertrophy.domain.model.TrainingSlot

enum class DayPlanSectionKind {
    PREP,
    MOBILITY,
    SKILL,
    COMPOUND,
    ISOLATION,
    CORE,
    CARDIO,
    COOLDOWN,
    OTHER,
}

data class DayPlanChecklistItem(
    val id: Long,
    val phase: ChecklistPhase,
    val text: String,
    val reps: Int?,
    val seconds: Int?,
)

sealed interface DayPlanRow {
    data class Checklist(
        val item: DayPlanChecklistItem,
        val schemeSummary: String,
    ) : DayPlanRow

    data class Slot(
        val slot: TrainingSlot,
        val exerciseName: String,
        val schemeSummary: String,
        val setupNotes: String?,
        val inSupersetGroup: Boolean,
        val isGroupStart: Boolean,
        val groupLabel: String?,
        val isGroupEnd: Boolean,
    ) : DayPlanRow
}

data class DayPlanSection(
    val kind: DayPlanSectionKind,
    val estimatedSeconds: Int,
    val rows: List<DayPlanRow>,
)

/**
 * Buckets checklist items and slots into category sections.
 * Empty sections are omitted.
 */
object DayPlanSectionBuilder {
    private val orderedKinds = DayPlanSectionKind.entries

    fun build(
        slots: List<TrainingSlot>,
        checklist: List<DayPlanChecklistItem>,
        exerciseNames: Map<Long, String>,
        exerciseNotes: Map<Long, String> = emptyMap(),
        transitionRestSeconds: Int = 0,
        prepDurationMin: Int? = null,
        cooldownDurationMin: Int? = null,
    ): List<DayPlanSection> {
        return orderedKinds.mapNotNull { kind ->
            val checklistRows = checklistRowsFor(kind, checklist)
            val categorySlots = slotsFor(kind, slots)
            if (checklistRows.isEmpty() && categorySlots.isEmpty()) return@mapNotNull null
            val slotRows = slotRows(categorySlots, exerciseNames, exerciseNotes)
            val estimated = estimateSection(
                kind = kind,
                checklist = checklist,
                slots = categorySlots,
                transitionRestSeconds = transitionRestSeconds,
                prepDurationMin = prepDurationMin,
                cooldownDurationMin = cooldownDurationMin,
            )
            DayPlanSection(
                kind = kind,
                estimatedSeconds = estimated,
                rows = checklistRows + slotRows,
            )
        }
    }

    private fun checklistRowsFor(
        kind: DayPlanSectionKind,
        checklist: List<DayPlanChecklistItem>,
    ): List<DayPlanRow.Checklist> {
        val phase = when (kind) {
            DayPlanSectionKind.PREP -> ChecklistPhase.PREP
            DayPlanSectionKind.COOLDOWN -> ChecklistPhase.COOLDOWN
            else -> return emptyList()
        }
        return checklist.filter { it.phase == phase }.map {
            DayPlanRow.Checklist(it, SchemeSummaryFormatter.formatChecklist(it.reps, it.seconds))
        }
    }

    private fun slotsFor(kind: DayPlanSectionKind, slots: List<TrainingSlot>): List<TrainingSlot> {
        val category = when (kind) {
            DayPlanSectionKind.PREP -> SlotCategory.PREP
            DayPlanSectionKind.MOBILITY -> SlotCategory.MOBILITY
            DayPlanSectionKind.SKILL -> SlotCategory.SKILL
            DayPlanSectionKind.COMPOUND -> SlotCategory.COMPOUND
            DayPlanSectionKind.ISOLATION -> SlotCategory.ISOLATION
            DayPlanSectionKind.CORE -> SlotCategory.CORE
            DayPlanSectionKind.CARDIO -> SlotCategory.CARDIO
            DayPlanSectionKind.COOLDOWN -> SlotCategory.COOLDOWN
            DayPlanSectionKind.OTHER -> return slots.filter { it.prescription.category !in knownCategories }
        }
        return slots.filter { it.prescription.category == category }
    }

    private val knownCategories = setOf(
        SlotCategory.PREP,
        SlotCategory.MOBILITY,
        SlotCategory.SKILL,
        SlotCategory.COMPOUND,
        SlotCategory.ISOLATION,
        SlotCategory.CORE,
        SlotCategory.CARDIO,
        SlotCategory.COOLDOWN,
    )

    private fun slotRows(
        slots: List<TrainingSlot>,
        exerciseNames: Map<Long, String>,
        exerciseNotes: Map<Long, String>,
    ): List<DayPlanRow.Slot> {
        if (slots.isEmpty()) return emptyList()
        val groups = groupSupersets(slots)
        return groups.flatMap { group ->
            val label = groupLabel(group)
            group.mapIndexed { index, slot ->
                val exerciseId = slot.prescription.exerciseId
                DayPlanRow.Slot(
                    slot = slot,
                    exerciseName = exerciseNames[exerciseId] ?: "Exercise $exerciseId",
                    schemeSummary = SchemeSummaryFormatter.format(slot.prescription),
                    setupNotes = exerciseNotes[exerciseId]?.takeIf { it.isNotBlank() },
                    inSupersetGroup = group.size > 1,
                    isGroupStart = group.size > 1 && index == 0,
                    groupLabel = if (group.size > 1 && index == 0) label else null,
                    isGroupEnd = group.size > 1 && index == group.lastIndex,
                )
            }
        }
    }

    fun groupSupersets(slots: List<TrainingSlot>): List<List<TrainingSlot>> {
        val groups = mutableListOf<MutableList<TrainingSlot>>()
        for (slot in slots) {
            val groupId = slot.prescription.supersetGroup
            val current = groups.lastOrNull()
            if (current != null && groupId != null && current.last().prescription.supersetGroup == groupId) {
                current += slot
            } else {
                groups += mutableListOf(slot)
            }
        }
        return groups
    }

    fun groupLabel(group: List<TrainingSlot>): String {
        return when {
            group.size >= 3 -> "GIANT SET: Follow sequence"
            group.size == 2 -> "SUPERSET"
            else -> ""
        }
    }

    private fun estimateSection(
        kind: DayPlanSectionKind,
        checklist: List<DayPlanChecklistItem>,
        slots: List<TrainingSlot>,
        transitionRestSeconds: Int,
        prepDurationMin: Int?,
        cooldownDurationMin: Int?,
    ): Int {
        val checklistSeconds = when (kind) {
            DayPlanSectionKind.PREP -> checklist.filter { it.phase == ChecklistPhase.PREP }
                .sumOf { it.seconds ?: ((it.reps ?: 0) * 3) }
            DayPlanSectionKind.COOLDOWN -> checklist.filter { it.phase == ChecklistPhase.COOLDOWN }
                .sumOf { it.seconds ?: ((it.reps ?: 0) * 3) }
            else -> 0
        }
        val slotSeconds = slots.sumOf { slotWorkSeconds(it.prescription) }
        val groups = groupSupersets(slots)
        val transitions = (groups.size - 1).coerceAtLeast(0) * transitionRestSeconds.coerceAtLeast(0)
        val computed = checklistSeconds + slotSeconds + transitions
        val declared = when (kind) {
            DayPlanSectionKind.PREP -> prepDurationMin?.times(60)
            DayPlanSectionKind.COOLDOWN -> cooldownDurationMin?.times(60)
            else -> null
        }
        return maxOf(computed, declared ?: 0)
    }

    private fun slotWorkSeconds(prescription: SlotPrescription): Int {
        val rest = if (prescription.restAsNeeded) 0 else prescription.restMinSec ?: 0
        val block = prescription.blockDurationSec ?: 0
        return prescription.setsMax.coerceAtLeast(0) * rest.coerceAtLeast(0) + block.coerceAtLeast(0)
    }
}
