package com.forge.hypertrophy.domain.workout

import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription

data class RecordedSet(
    val id: Long,
    val setNumber: Int,
    val side: SetSide,
    val weightKg: Double?,
    val reps: Int?,
    val holdSec: Int?,
    val rpe: Double?,
    val jointFlags: List<String>,
    val entryMethod: EntryMethod,
)

data class ExerciseChoice(
    val id: Long,
    val name: String,
)

/** Starting load chosen before the slot has any of its own history. */
data class BaselineHint(
    val weightKg: Double?,
    val reps: Int?,
    val awaitingCalibration: Boolean,
)

data class WorkoutSlot(
    val sessionSlotId: Long,
    val routineSlotId: Long? = null,
    val sortOrder: Int,
    val prescription: SlotPrescription,
    val exerciseName: String,
    val setupNotes: String,
    val unilateral: Boolean,
    val equipment: Equipment = Equipment.BARBELL,
    val barWeightKg: Double? = null,
    val alternatives: List<ExerciseChoice> = emptyList(),
    val skipped: Boolean = false,
    val skipReason: String? = null,
    val chosenAlternativeExerciseId: Long? = null,
    val formConfirmed: Boolean = false,
    val sets: List<RecordedSet> = emptyList(),
    val skillHold: SkillHoldHint? = null,
) {
    val activeExerciseId: Long
        get() = chosenAlternativeExerciseId ?: prescription.exerciseId
}

data class ChecklistStep(
    val id: Long,
    val text: String,
    val done: Boolean = false,
)

data class SetSuggestion(
    val weightKg: Double?,
    val reps: Int?,
    val holdSec: Int?,
    val fromPreviousSession: Boolean,
)

data class WorkoutMachineState(
    val slots: List<WorkoutSlot> = emptyList(),
    val prep: List<ChecklistStep> = emptyList(),
    val cooldown: List<ChecklistStep> = emptyList(),
    val started: Boolean = false,
    /** False until the athlete confirms prep (Start First) or prep was already skipped on resume. */
    val leftPrep: Boolean = false,
    /** False until Finish is pressed on cooldown (or the session was already completed). */
    val leftCooldown: Boolean = false,
    val transitionRestSeconds: Int = 120,
    val draft: SetSuggestion? = null,
    val dismissedRests: Set<String> = emptySet(),
    val previousByExercise: Map<Long, RecordedSet> = emptyMap(),
    /** Last working set logged on this routine slot, excluding the open session. */
    val ownHistoryBySessionSlot: Map<Long, RecordedSet> = emptyMap(),
    val baselineBySessionSlot: Map<Long, BaselineHint> = emptyMap(),
    val sessionJoints: Set<String> = emptySet(),
    val dismissedRegulationSetIds: Set<Long> = emptySet(),
    val acceptedRegulationSetIds: Set<Long> = emptySet(),
)

sealed interface WorkoutPosition {
    data object Readiness : WorkoutPosition

    data class Prep(val items: List<ChecklistStep>) : WorkoutPosition

    data class PracticeBlock(val slot: WorkoutSlot) : WorkoutPosition

    data class WorkingSet(
        val slot: WorkoutSlot,
        val setNumber: Int,
        val setCount: Int,
        val side: SetSide,
        val suggestion: SetSuggestion,
        val suggestSkip: Boolean,
        val needsFormCheck: Boolean,
        val slotIndex: Int = 0,
        val slotCount: Int = 1,
        val requiredPlates: List<Double> = emptyList(),
        val regulation: RegulationPrompt? = null,
    ) : WorkoutPosition

    data class Resting(
        val slot: WorkoutSlot,
        val kind: RestKind,
        val round: Int,
        val next: WorkoutSlot?,
    ) : WorkoutPosition

    data class Cooldown(val items: List<ChecklistStep>) : WorkoutPosition

    data object Summary : WorkoutPosition
}

data class RegulationPrompt(
    val sourceSetId: Long,
    val lastRpe: Double,
    val suggestedWeightKg: Double,
)

enum class RestKind {
    BETWEEN_SETS,
    TRANSITION,
    AS_NEEDED,
}

private sealed interface AgendaItem {
    data class Block(val slot: WorkoutSlot) : AgendaItem

    data class Work(val slot: WorkoutSlot, val setNumber: Int, val side: SetSide) : AgendaItem

    data class Rest(val slot: WorkoutSlot, val kind: RestKind, val round: Int) : AgendaItem
}

fun workoutPosition(state: WorkoutMachineState): WorkoutPosition {
    if (!state.started) return WorkoutPosition.Readiness
    // Stay on Prep until leavePrep — including when every item is checked — so
    // Start First Exercise is reachable. Empty prep skips this phase.
    if (state.prep.isNotEmpty() && !state.leftPrep) return WorkoutPosition.Prep(state.prep)
    val agenda = agenda(state.slots)
    val index = currentIndex(agenda, state.dismissedRests)
    if (index == null) {
        return if (state.cooldown.isNotEmpty() && !state.leftCooldown) {
            WorkoutPosition.Cooldown(state.cooldown)
        } else {
            WorkoutPosition.Summary
        }
    }
    return when (val item = agenda[index]) {
        is AgendaItem.Block -> WorkoutPosition.PracticeBlock(item.slot)
        is AgendaItem.Work -> {
            val suggestion = state.draft ?: suggestionFor(
                item.slot,
                state.previousByExercise,
                state.baselineBySessionSlot[item.slot.sessionSlotId],
                state.ownHistoryBySessionSlot[item.slot.sessionSlotId],
            )
            val ordered = state.slots.sortedBy { it.sortOrder }
            WorkoutPosition.WorkingSet(
                slot = item.slot,
                setNumber = item.setNumber,
                setCount = item.slot.prescription.setsMax.coerceAtLeast(item.setNumber),
                side = item.side,
                suggestion = suggestion,
                suggestSkip = suggestSkip(item.slot, state),
                needsFormCheck = item.slot.prescription.category == SlotCategory.SKILL && !item.slot.formConfirmed,
                slotIndex = ordered.indexOfFirst { it.sessionSlotId == item.slot.sessionSlotId }.coerceAtLeast(0),
                slotCount = ordered.size.coerceAtLeast(1),
            )
        }
        is AgendaItem.Rest -> WorkoutPosition.Resting(
            slot = item.slot,
            kind = item.kind,
            round = item.round,
            next = agenda.drop(index + 1).firstNotNullOfOrNull { upcoming ->
                when (upcoming) {
                    is AgendaItem.Work -> upcoming.slot
                    is AgendaItem.Block -> upcoming.slot
                    is AgendaItem.Rest -> null
                }
            },
        )
    }
}

fun suggestSkip(slot: WorkoutSlot, state: WorkoutMachineState): Boolean {
    val flagged = state.sessionJoints.isNotEmpty() ||
        state.slots.any { candidate -> candidate.sets.any { it.jointFlags.isNotEmpty() } }
    return flagged &&
        slot.prescription.isOptional &&
        slot.prescription.category == SlotCategory.COMPOUND &&
        slot.sets.isEmpty()
}

fun restKey(slotId: Long, round: Int, kind: RestKind): String = "$slotId:$round:$kind"

/**
 * Rest dismissals live only in memory. On resume, any rest already passed
 * (a later set or block is logged) is treated as dismissed so the session
 * continues at the unfinished exercise instead of the first rest.
 */
fun restoredDismissedRests(slots: List<WorkoutSlot>): Set<String> {
    val items = agenda(slots)
    return items.mapIndexedNotNull { index, item ->
        val rest = item as? AgendaItem.Rest ?: return@mapIndexedNotNull null
        val passed = items.drop(index + 1).any { later ->
            when (later) {
                is AgendaItem.Work -> later.slot.sets.any { it.setNumber == later.setNumber && it.side == later.side }
                is AgendaItem.Block -> later.slot.sets.isNotEmpty()
                is AgendaItem.Rest -> false
            }
        }
        if (passed) restKey(rest.slot.sessionSlotId, rest.round, rest.kind) else null
    }.toSet()
}

fun checkOff(state: WorkoutMachineState, itemId: Long): WorkoutMachineState = state.copy(
    prep = state.prep.mark(itemId),
    cooldown = state.cooldown.mark(itemId),
    draft = null,
)

fun leavePrep(state: WorkoutMachineState): WorkoutMachineState =
    state.copy(leftPrep = true, draft = null)

fun leaveCooldown(state: WorkoutMachineState): WorkoutMachineState =
    state.copy(leftCooldown = true, draft = null)

fun dismissRest(state: WorkoutMachineState): WorkoutMachineState {
    val resting = workoutPosition(state) as? WorkoutPosition.Resting ?: return state
    return state.copy(
        dismissedRests = state.dismissedRests + restKey(resting.slot.sessionSlotId, resting.round, resting.kind),
        draft = null,
    )
}

/**
 * Adds one working set to the exercise on screen, for this session only.
 * The program template is not involved. Timed blocks stay as they are.
 */
fun addExtraSet(state: WorkoutMachineState): WorkoutMachineState {
    val slot = when (val position = workoutPosition(state)) {
        is WorkoutPosition.WorkingSet -> position.slot
        is WorkoutPosition.Resting -> position.slot
        else -> return state
    }
    if (slot.skipped || slot.prescription.metricType == MetricType.TIMED_BLOCK) return state
    return state.copy(
        slots = state.slots.replace(slot.sessionSlotId) { current ->
            current.copy(prescription = current.prescription.copy(setsMax = current.prescription.setsMax + 1))
        },
    )
}

fun logCurrentSet(state: WorkoutMachineState, entry: RecordedSet): WorkoutMachineState {
    val working = workoutPosition(state) as? WorkoutPosition.WorkingSet ?: return state
    return state.copy(
        slots = state.slots.replace(working.slot.sessionSlotId) { slot ->
            slot.copy(sets = slot.sets + entry.copy(setNumber = working.setNumber, side = working.side))
        },
        draft = null,
    )
}

fun logBlock(state: WorkoutMachineState, entry: RecordedSet): WorkoutMachineState {
    val block = workoutPosition(state) as? WorkoutPosition.PracticeBlock ?: return state
    return state.copy(
        slots = state.slots.replace(block.slot.sessionSlotId) { slot ->
            slot.copy(sets = slot.sets + entry.copy(setNumber = 1, side = SetSide.BOTH))
        },
        draft = null,
    )
}

fun skipSlot(state: WorkoutMachineState, slotId: Long, reason: String): WorkoutMachineState = state.copy(
    slots = state.slots.replace(slotId) { it.copy(skipped = true, skipReason = reason) },
    draft = null,
)

fun chooseAlternative(state: WorkoutMachineState, slotId: Long, exerciseId: Long?): WorkoutMachineState = state.copy(
    slots = state.slots.replace(slotId) { it.copy(chosenAlternativeExerciseId = exerciseId) },
    draft = null,
)

fun confirmForm(state: WorkoutMachineState, slotId: Long): WorkoutMachineState = state.copy(
    slots = state.slots.replace(slotId) { it.copy(formConfirmed = true) },
)

fun updateSet(state: WorkoutMachineState, setId: Long, transform: (RecordedSet) -> RecordedSet): WorkoutMachineState =
    state.copy(
        slots = state.slots.map { slot ->
            slot.copy(sets = slot.sets.map { set -> if (set.id == setId) transform(set) else set })
        },
    )

fun removeSet(state: WorkoutMachineState, setId: Long): WorkoutMachineState = state.copy(
    slots = state.slots.map { slot -> slot.copy(sets = slot.sets.filterNot { it.id == setId }) },
    draft = null,
)

/** Full-list permutation. Indexes follow the current sort order. Writes 0..n-1. */
fun moveSlot(state: WorkoutMachineState, from: Int, to: Int): WorkoutMachineState {
    val ordered = state.slots.sortedBy { it.sortOrder }
    if (from !in ordered.indices || to !in ordered.indices || from == to) return state
    val next = ordered.toMutableList()
    next.add(to, next.removeAt(from))
    val resorted = next.mapIndexed { index, slot -> slot.copy(sortOrder = index) }
    return state.copy(slots = resorted, draft = null)
}

fun adjustDraft(state: WorkoutMachineState, weightDeltaKg: Double, repDelta: Int, holdDelta: Int): WorkoutMachineState {
    val working = workoutPosition(state) as? WorkoutPosition.WorkingSet ?: return state
    val current = working.suggestion
    return state.copy(
        draft = current.copy(
            weightKg = if (weightDeltaKg == 0.0) {
                current.weightKg
            } else {
                ((current.weightKg ?: 0.0) + weightDeltaKg).coerceAtLeast(0.0)
            },
            reps = if (repDelta == 0) current.reps else ((current.reps ?: 0) + repDelta).coerceAtLeast(0),
            holdSec = if (holdDelta == 0) current.holdSec else ((current.holdSec ?: 0) + holdDelta).coerceAtLeast(0),
        ),
    )
}

fun suggestionFor(
    slot: WorkoutSlot,
    previousByExercise: Map<Long, RecordedSet>,
    baseline: BaselineHint? = null,
    ownPrevious: RecordedSet? = null,
): SetSuggestion {
    val prescription = slot.prescription
    val hold = prescription.metricType == MetricType.HOLD || prescription.metricType == MetricType.HOLD_OR_REPS
    fun holdSeconds(preferred: Int?): Int? = if (!hold) {
        null
    } else {
        preferred ?: prescription.holdTargetSec ?: slot.skillHold?.let { hint ->
            skillHoldSeconds(hint.stage, hint.targets, slot.sets.sumOf { it.holdSec ?: 0 })
        }
    }
    if (ownPrevious != null) {
        return SetSuggestion(
            weightKg = ownPrevious.weightKg,
            reps = if (hold) null else ownPrevious.reps,
            holdSec = holdSeconds(ownPrevious.holdSec),
            fromPreviousSession = !hold || ownPrevious.holdSec != null,
        )
    }
    if (baseline?.awaitingCalibration == true) {
        return SetSuggestion(
            weightKg = null,
            reps = if (hold) null else prescription.repsLow ?: prescription.repsHigh,
            holdSec = holdSeconds(null),
            fromPreviousSession = false,
        )
    }
    if (baseline != null && baseline.weightKg != null && !hold) {
        return SetSuggestion(
            weightKg = baseline.weightKg,
            reps = baseline.reps ?: prescription.repsLow ?: prescription.repsHigh,
            holdSec = null,
            fromPreviousSession = false,
        )
    }
    val previous = previousByExercise[slot.activeExerciseId]
    if (previous != null) {
        return SetSuggestion(
            weightKg = previous.weightKg,
            reps = if (hold) null else previous.reps,
            holdSec = holdSeconds(previous.holdSec),
            fromPreviousSession = !hold || previous.holdSec != null,
        )
    }
    return SetSuggestion(
        weightKg = null,
        reps = if (hold) null else prescription.repsLow ?: prescription.repsHigh,
        holdSec = holdSeconds(null),
        fromPreviousSession = false,
    )
}

fun restWindowSeconds(resting: WorkoutPosition.Resting, transitionRestSeconds: Int): Pair<Int, Int> = when (resting.kind) {
    RestKind.TRANSITION -> transitionRestSeconds to transitionRestSeconds
    RestKind.AS_NEEDED -> 0 to 0
    RestKind.BETWEEN_SETS -> {
        val prescription = resting.slot.prescription
        val minimum = prescription.restMinSec ?: 0
        val maximum = prescription.restMaxSec ?: minimum
        if (fellShortOfTarget(resting)) maximum to maximum else minimum to maximum
    }
}

private fun fellShortOfTarget(resting: WorkoutPosition.Resting): Boolean {
    val prescription = resting.slot.prescription
    val logged = resting.slot.sets
        .filter { it.setNumber == resting.round }
        .maxByOrNull { it.id }
        ?: return false
    val repsShort = prescription.repsLow != null && logged.reps != null && logged.reps < prescription.repsLow
    val holdShort = prescription.holdTargetSec != null && logged.holdSec != null && logged.holdSec < prescription.holdTargetSec
    return repsShort || holdShort
}

fun restTimer(
    resting: WorkoutPosition.Resting,
    nowElapsedRealtime: Long,
    transitionRestSeconds: Int,
    cue: String?,
    speak: Boolean,
): TimerSpec = when (resting.kind) {
    RestKind.AS_NEEDED -> stopwatchSpec(nowElapsedRealtime)
    else -> {
        val (minimum, maximum) = restWindowSeconds(resting, transitionRestSeconds)
        countdownSpec(nowElapsedRealtime, minimum, maximum, cue, speak)
    }
}

fun blockTimer(slot: WorkoutSlot, nowElapsedRealtime: Long): TimerSpec = countdownSpec(
    nowElapsedRealtime,
    slot.prescription.blockDurationSec ?: 0,
    slot.prescription.blockDurationSec ?: 0,
)

private fun agenda(slots: List<WorkoutSlot>): List<AgendaItem> {
    val active = slots.filter { !it.skipped }.sortedBy { it.sortOrder }
    val groups = groupSupersets(active)
    val items = mutableListOf<AgendaItem>()
    groups.forEach { group ->
        if (group.all { it.prescription.metricType == MetricType.TIMED_BLOCK }) {
            group.forEach { slot ->
                items += AgendaItem.Block(slot)
                items += AgendaItem.Rest(slot, RestKind.TRANSITION, round = 1)
            }
            return@forEach
        }
        val rounds = group.maxOf { it.prescription.setsMax.coerceAtLeast(0) }
        for (round in 1..rounds) {
            val members = group.filter { round <= it.prescription.setsMax }
            if (members.isEmpty()) continue
            members.forEach { slot ->
                sides(slot).forEach { side -> items += AgendaItem.Work(slot, round, side) }
            }
            val lastRound = members.all { round >= it.prescription.setsMax }
            val kind = if (lastRound) {
                RestKind.TRANSITION
            } else if (members.last().prescription.restAsNeeded) {
                RestKind.AS_NEEDED
            } else {
                RestKind.BETWEEN_SETS
            }
            items += AgendaItem.Rest(members.last(), kind, round)
        }
    }
    return items
}

private fun currentIndex(agenda: List<AgendaItem>, dismissed: Set<String>): Int? {
    agenda.forEachIndexed { index, item ->
        when (item) {
            is AgendaItem.Work -> if (!item.slot.sets.any { it.setNumber == item.setNumber && it.side == item.side }) {
                return index
            }
            is AgendaItem.Block -> if (item.slot.sets.isEmpty()) return index
            is AgendaItem.Rest -> {
                val key = restKey(item.slot.sessionSlotId, item.round, item.kind)
                if (key !in dismissed) return index
            }
        }
    }
    return null
}

private fun groupSupersets(slots: List<WorkoutSlot>): List<List<WorkoutSlot>> {
    val groups = mutableListOf<MutableList<WorkoutSlot>>()
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

private fun sides(slot: WorkoutSlot): List<SetSide> =
    if (slot.unilateral) listOf(SetSide.LEFT, SetSide.RIGHT) else listOf(SetSide.BOTH)

private fun List<WorkoutSlot>.replace(id: Long, transform: (WorkoutSlot) -> WorkoutSlot): List<WorkoutSlot> =
    map { if (it.sessionSlotId == id) transform(it) else it }

private fun List<ChecklistStep>.mark(id: Long): List<ChecklistStep> =
    map { if (it.id == id) it.copy(done = true) else it }

fun workoutCue(
    setNumber: Int,
    setCount: Int,
    exerciseName: String,
    weightKg: Double?,
    repsLow: Int?,
    repsHigh: Int?,
    holdSec: Int?,
): String {
    val load = weightKg?.let { "plus ${formatSpokenKg(it)} kg" }
    val target = when {
        holdSec != null -> "$holdSec seconds"
        repsLow != null && repsHigh != null && repsLow != repsHigh -> "$repsLow to $repsHigh reps"
        repsLow != null -> "$repsLow reps"
        repsHigh != null -> "$repsHigh reps"
        else -> null
    }
    return listOfNotNull("Set $setNumber of $setCount", exerciseName, load, target).joinToString(", ")
}

private fun formatSpokenKg(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
