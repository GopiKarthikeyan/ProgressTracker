package com.forge.hypertrophy.data.backup

import com.forge.hypertrophy.data.db.AppDatabase

internal fun AppDatabase.checkpointWal() {
    openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
        if (!cursor.moveToFirst()) error("wal_checkpoint returned no row")
    }
}
