package com.forge.hypertrophy.data.backup

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.system.exitProcess

class PackageAppVersion @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppVersionSource {
    @Suppress("DEPRECATION")
    override fun versionName(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
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
