package com.forge.hypertrophy

import android.app.Application
import com.forge.hypertrophy.data.diagnostics.DiagnosticsRegistry
import dagger.hilt.android.HiltAndroidApp
import java.time.Clock

@HiltAndroidApp
class HypertrophyApp : Application() {
    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
        DiagnosticsRegistry.install(this, Clock.systemDefaultZone(), version)
    }
}
