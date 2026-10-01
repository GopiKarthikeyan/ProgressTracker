package com.forge.hypertrophy.data.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

enum class ProcessExitKind {
    ANR,
    LOW_MEMORY,
    OTHER,
}

data class ProcessExit(
    val kind: ProcessExitKind,
    val timestamp: Long,
    val description: String,
)

/** Records ANRs and low-memory kills. Other reasons are ignored, and a timestamp is written once. */
class ProcessExitRecorder(private val store: CrashLogStore) {
    fun record(exits: List<ProcessExit>, versionName: String, apiLevel: Int) {
        val seen = store.seenExitTimestamps()
        for (exit in exits) {
            if (exit.kind == ProcessExitKind.OTHER) continue
            if (exit.timestamp in seen) continue
            store.writeExit(exit.kind.name, exit.timestamp, exit.description, versionName, apiLevel)
            store.rememberExit(exit.timestamp)
        }
    }
}

fun readProcessExits(context: Context): List<ProcessExit> {
    if (Build.VERSION.SDK_INT < 30) return emptyList()
    return readProcessExitsApi30(context)
}

@RequiresApi(30)
private fun readProcessExitsApi30(context: Context): List<ProcessExit> {
    val manager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
    return manager.getHistoricalProcessExitReasons(null, 0, 16).map { info ->
        ProcessExit(
            kind = when (info.reason) {
                ApplicationExitInfo.REASON_ANR -> ProcessExitKind.ANR
                ApplicationExitInfo.REASON_LOW_MEMORY -> ProcessExitKind.LOW_MEMORY
                else -> ProcessExitKind.OTHER
            },
            timestamp = info.timestamp,
            description = info.description ?: info.reason.toString(),
        )
    }
}
