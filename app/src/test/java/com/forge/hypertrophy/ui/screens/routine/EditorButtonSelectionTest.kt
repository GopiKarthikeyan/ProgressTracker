package com.forge.hypertrophy.ui.screens.routine

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.forge.hypertrophy.ui.theme.HypertrophyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Choice groups used to mark the chosen option by DISABLING its button, so
 * TalkBack announced "disabled" instead of "selected" and the selected option
 * looked unavailable. These assertions encode that bug: a selected option must
 * report `selected` and must stay enabled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class EditorButtonSelectionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectedOptionIsSelectedAndStillEnabled() {
        compose.setContent {
            HypertrophyTheme {
                EditorButton(label = "Barbell", onClick = {}, selected = true)
            }
        }

        compose.onNodeWithText("Barbell").assertIsSelected()
        compose.onNodeWithText("Barbell").assertIsEnabled()
    }

    @Test
    fun unselectedOptionIsNotSelected() {
        compose.setContent {
            HypertrophyTheme {
                EditorButton(label = "Dumbbell", onClick = {}, selected = false)
            }
        }

        compose.onNodeWithText("Dumbbell").assertIsNotSelected()
        compose.onNodeWithText("Dumbbell").assertIsEnabled()
    }

    /** `enabled` still means unavailable, and is independent of selection. */
    @Test
    fun disabledRemainsDistinctFromSelected() {
        compose.setContent {
            HypertrophyTheme {
                EditorButton(
                    label = "Cable",
                    onClick = {},
                    enabled = false,
                    selected = false,
                )
            }
        }

        compose.onNodeWithText("Cable").assertIsNotEnabled()
        compose.onNodeWithText("Cable").assertIsNotSelected()
    }
}
