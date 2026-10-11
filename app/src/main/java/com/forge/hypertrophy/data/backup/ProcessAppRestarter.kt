package com.forge.hypertrophy.data.backup

import android.content.Context
import android.content.Intent
import com.forge.hypertrophy.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.system.exitProcess

class PackageAppVersion @Inject constructor() : AppVersionSource {
    override fun versionName(): String = BuildConfig.VERSION_NAME
}

class ProcessAppRestarter @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppRestarter {
    override fun restart() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        exitProcess(0)
    }
}
