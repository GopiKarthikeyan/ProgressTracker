package com.forge.hypertrophy

import android.app.Application
import com.forge.hypertrophy.data.diagnostics.DiagnosticsRegistry
import dagger.hilt.android.HiltAndroidApp
import java.time.Clock

@HiltAndroidApp
class HypertrophyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticsRegistry.install(this, Clock.systemDefaultZone(), BuildConfig.VERSION_NAME)
    }
}
