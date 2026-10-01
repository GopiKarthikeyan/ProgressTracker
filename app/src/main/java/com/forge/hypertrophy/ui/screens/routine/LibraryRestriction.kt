package com.forge.hypertrophy.ui.screens.routine

import android.database.sqlite.SQLiteConstraintException

internal fun Throwable.isForeignKeyRestriction(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is SQLiteConstraintException) return true
        current = current.cause
    }
    return false
}
