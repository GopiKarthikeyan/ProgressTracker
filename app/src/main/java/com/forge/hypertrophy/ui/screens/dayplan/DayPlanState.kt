package com.forge.hypertrophy.ui.screens.dayplan

import com.forge.hypertrophy.domain.dayplan.DayPlanCta
import com.forge.hypertrophy.domain.dayplan.DayPlanCtaKind
import com.forge.hypertrophy.domain.dayplan.DayPlanSection
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.usecase.DayPlanCommitment
import java.time.LocalDate

data class DayPlanDateChip(
    val date: LocalDate,
    val isToday: Boolean,
    val isSelected: Boolean,
)

data class DayPlanUiState(
    val programName: String = "",
    val scheduleMode: ScheduleMode? = null,
    val trainingDayCount: Int = 0,
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val dateStrip: List<DayPlanDateChip> = emptyList(),
    val dayLabel: String? = null,
    val isRestDay: Boolean = false,
    val commitment: DayPlanCommitment = DayPlanCommitment.UNRESOLVED,
    val estimatedSeconds: Int = 0,
    val sections: List<DayPlanSection> = emptyList(),
    val collapsedSections: Set<String> = setOf("PREP"),
    val cta: DayPlanCta = DayPlanCta(DayPlanCtaKind.NONE),
    val editable: Boolean = false,
    val dayId: Long? = null,
    val activeSessionId: Long? = null,
    val sessionToOpen: Long? = null,
    val slotToOpen: Long? = null,
    val loading: Boolean = true,
)

sealed interface DayPlanEvent {
    data class SelectDate(val date: LocalDate) : DayPlanEvent
    data class ToggleSection(val kindKey: String) : DayPlanEvent
    data object PrimaryAction : DayPlanEvent
    data object OpenedSession : DayPlanEvent
    data object OpenedSlotEditor : DayPlanEvent
    data object Refresh : DayPlanEvent
    data class OpenDayEditor(val dayId: Long) : DayPlanEvent
    data class OpenSlotEditor(val slotId: Long) : DayPlanEvent
    data class AddSlotInCategory(val kindKey: String) : DayPlanEvent
    data class MoveSlot(val slotId: Long, val delta: Int) : DayPlanEvent
    data class RemoveSlot(val slotId: Long) : DayPlanEvent
}
