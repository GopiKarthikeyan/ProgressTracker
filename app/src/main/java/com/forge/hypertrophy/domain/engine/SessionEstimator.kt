package com.forge.hypertrophy.domain.engine

import com.forge.hypertrophy.domain.model.TrainingSlot

/**
 * Session length in seconds.
 *
 * Each slot contributes `setsMax` times its rest, plus [com.forge.hypertrophy.domain.model.SlotPrescription.blockDurationSec]
 * when the slot is a timed block. Rest is [com.forge.hypertrophy.domain.model.SlotPrescription.restMinSec],
 * because the timer counts down to the minimum of the range.
 *
 * An "as needed" rest is a stopwatch with no planned duration, so it
 * contributes 0 seconds. The block duration is still included.
 *
 * [transitionRestSeconds] is added once after every slot group except the
 * last. Slots that share a superset group are one group, so the transition
 * is counted once for the group. The caller passes the transition; this
 * class has no default.
 */
class SessionEstimator {
    fun estimate(slots: List<TrainingSlot>, transitionRestSeconds: Int): Int {
        if (slots.isEmpty()) return 0
        val groups = groupsOf(slots)
        val work = groups.sumOf { group -> group.sumOf(::slotSeconds) }
        val transitions = (groups.size - 1).coerceAtLeast(0) * transitionRestSeconds.coerceAtLeast(0)
        return work + transitions
    }

    private fun groupsOf(slots: List<TrainingSlot>): List<List<TrainingSlot>> {
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

    private fun slotSeconds(slot: TrainingSlot): Int {
        val prescription = slot.prescription
        val rest = if (prescription.restAsNeeded) 0 else prescription.restMinSec ?: 0
        val block = prescription.blockDurationSec ?: 0
        return prescription.setsMax.coerceAtLeast(0) * rest.coerceAtLeast(0) + block.coerceAtLeast(0)
    }
}
