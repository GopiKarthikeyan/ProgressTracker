package com.forge.hypertrophy.ui.theme

import android.app.Application
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.forge.hypertrophy.ui.components.SurfaceCard
import com.forge.hypertrophy.ui.screens.routine.BuilderColumn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the dark-mode bug: the builder chrome and shared cards used to
 * hardcode the light palette, so every screen rendered cream-on-cream under
 * the dark scheme. These assertions fail if a literal colour is reintroduced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class DarkModeSurfaceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "night")
    fun builderChromeDoesNotPaintTheLightBackgroundInDark() {
        var background = Color.Unspecified
        compose.setContent {
            HypertrophyTheme {
                background = MaterialTheme.colorScheme.background
                BuilderColumn(title = "Programs", onBack = null) {
                    Text("row")
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Programs").assertIsDisplayed()
        assertEquals(AmoledBlack, background)
        assertNotEquals(Cream, background)
    }

    @Test
    @Config(qualifiers = "night")
    fun surfaceCardDefaultsToTheThemedSurfaceInDark() {
        var surface = Color.Unspecified
        compose.setContent {
            HypertrophyTheme {
                surface = MaterialTheme.colorScheme.surface
                SurfaceCard { Text("card") }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("card").assertIsDisplayed()
        assertEquals(DarkSurface, surface)
        assertNotEquals(White, surface)
    }

    /**
     * The 24dp card radius used to be a top-level `CardShape` constant that
     * screens imported directly. It now comes from the theme, so a caller
     * reading `MaterialTheme.shapes.large` gets the same radius.
     */
    @Test
    fun themeSuppliesTheTwentyFourDpCardRadius() {
        var radius: CornerSize? = null
        var density: Density? = null
        compose.setContent {
            HypertrophyTheme {
                radius = MaterialTheme.shapes.large.topStart
                density = LocalDensity.current
            }
        }
        compose.waitForIdle()

        val px = radius!!.toPx(Size(1000f, 1000f), density!!)
        assertEquals(24f, with(density!!) { px.toDp().value }, 0.5f)
    }
}
