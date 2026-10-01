package com.forge.hypertrophy.domain.workout

const val HANDS_FREE_DEBOUNCE_MILLIS = 500L
const val HANDS_FREE_UNDO_MILLIS = 5_000L

/**
 * Hardware triggers closer than [HANDS_FREE_DEBOUNCE_MILLIS] are ignored.
 * A logged trigger can be undone until [undoUntilElapsedRealtime].
 */
class HandsFreeGate {
    private var lastAcceptedElapsedRealtime: Long? = null
    var undoSetId: Long? = null
        private set
    var undoUntilElapsedRealtime: Long? = null
        private set

    fun accept(nowElapsedRealtime: Long): Boolean {
        val previous = lastAcceptedElapsedRealtime
        if (previous != null && nowElapsedRealtime - previous < HANDS_FREE_DEBOUNCE_MILLIS) return false
        lastAcceptedElapsedRealtime = nowElapsedRealtime
        return true
    }

    fun armUndo(setId: Long, nowElapsedRealtime: Long) {
        undoSetId = setId
        undoUntilElapsedRealtime = nowElapsedRealtime + HANDS_FREE_UNDO_MILLIS
    }

    fun takeUndo(nowElapsedRealtime: Long): Long? {
        val id = undoSetId ?: return null
        val until = undoUntilElapsedRealtime ?: return null
        if (nowElapsedRealtime > until) {
            clearUndo()
            return null
        }
        clearUndo()
        return id
    }

    fun clearUndo() {
        undoSetId = null
        undoUntilElapsedRealtime = null
    }
}
