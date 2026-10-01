package com.forge.hypertrophy.data.diagnostics

import android.app.Application
import android.os.Build
import java.io.File
import java.time.Clock

const val DIAGNOSTICS_DIR = "diagnostics"

/**
 * Process-wide breadcrumbs and crash files. Installed from [Application.onCreate]
 * before any screen runs, and injected for the diagnostics screen.
 */
object DiagnosticsRegistry {
    val breadcrumbs = Breadcrumbs()
    private var store: CrashLogStore? = null

    fun store(filesDir: File, clock: Clock): CrashLogStore {
        val current = store
        if (current != null) return current
        return CrashLogStore(File(filesDir, DIAGNOSTICS_DIR), clock).also { store = it }
    }

    fun install(app: Application, clock: Clock, versionName: String) {
        val files = store(app.filesDir, clock)
        val prior = Thread.getDefaultUncaughtExceptionHandler()
        installCrashHandler(
            store = files,
            breadcrumbs = breadcrumbs,
            versionName = versionName,
            deviceModel = Build.MODEL ?: "unknown",
            apiLevel = Build.VERSION.SDK_INT,
            prior = prior,
        )
        ProcessExitRecorder(files).record(
            exits = readProcessExits(app),
            versionName = versionName,
            apiLevel = Build.VERSION.SDK_INT,
        )
    }
}
