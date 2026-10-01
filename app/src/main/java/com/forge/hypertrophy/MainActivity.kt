package com.forge.hypertrophy

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.forge.hypertrophy.ui.HypertrophyRoot
import com.forge.hypertrophy.ui.theme.HypertrophyTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Bumps once per [ACTION_OPEN_TODAY] intent so the nav host can jump to Today. */
    private val openTodayRequests = MutableStateFlow(0)
    val openToday: StateFlow<Int> = openTodayRequests.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        handle(intent)
        setContent {
            HypertrophyTheme {
                HypertrophyRoot(openToday = openToday)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_TODAY) openTodayRequests.value += 1
    }

    companion object {
        const val ACTION_OPEN_TODAY = "com.forge.hypertrophy.OPEN_TODAY"
    }
}
