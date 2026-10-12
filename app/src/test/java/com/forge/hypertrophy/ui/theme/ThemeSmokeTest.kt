package com.forge.hypertrophy.ui.theme

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves the Compose test harness runs under Robolectric in `testDebugUnitTest`,
 * and that the `night` qualifier really selects [DarkColorScheme].
 *
 * Later stages assert dark-mode correctness by rendering under `night`, so this
 * test guards the mechanism those assertions depend on. If this breaks, every
 * dark-mode claim in the suite becomes unverifiable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class ThemeSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lightQualifiersUseTheWarmBackground() {
        assertEquals(Cream, backgroundInTheme())
    }

    @Test
    @Config(qualifiers = "night")
    fun nightQualifiersUseTheAmoledBackground() {
        assertEquals(AmoledBlack, backgroundInTheme())
    }

    /** Renders [HypertrophyTheme] and reports the resolved background role. */
    private fun backgroundInTheme(): Color {
        var background = Color.Unspecified
        compose.setContent {
            HypertrophyTheme {
                background = MaterialTheme.colorScheme.background
            }
        }
        compose.waitForIdle()
        return background
    }
}
