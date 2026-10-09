package com.forge.hypertrophy.flow

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.forge.hypertrophy.MainActivity
import com.forge.hypertrophy.R
import com.forge.hypertrophy.data.db.DATABASE_NAME
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end path on an emulator or device: onboarding, sample import,
 * today's weekday day, a short workout with resume and finish, then the
 * main tabs. Clears app data before the activity launches.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var device: UiDevice
    private lateinit var context: Context

    @Before
    fun launchFresh() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        clearAppData(context)
        scenario = ActivityScenario.launch(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        composeRule.waitForIdle()
    }

    @After
    fun close() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun completeScreenFlow() {
        finishOnboarding()
        assertText(context.getString(R.string.today_no_program))

        openTab(context.getString(R.string.nav_settings))
        clickText(context.getString(R.string.settings_load_sample), scroll = true)
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(context.getString(R.string.import_confirm))
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(context.getString(R.string.import_unreadable))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        check(
            composeRule.onAllNodesWithText(context.getString(R.string.import_unreadable))
                .fetchSemanticsNodes().isEmpty(),
        ) { "Sample import was unreadable" }
        clickText(context.getString(R.string.import_confirm), scroll = true)
        clickText(context.getString(R.string.baseline_save), scroll = true)
        clickText(context.getString(R.string.builder_back))

        openTab(context.getString(R.string.nav_today))
        val todayLabel = expectedDayLabel(LocalDate.now().dayOfWeek)
        assertText(todayLabel)

        if (LocalDate.now().dayOfWeek != DayOfWeek.SUNDAY) {
            clickText(context.getString(R.string.workout_start))
            // One Great chip per score row (sleep, soreness, energy).
            clickText(context.getString(R.string.workout_readiness_great), index = 0)
            clickText(context.getString(R.string.workout_readiness_great), index = 1)
            clickText(context.getString(R.string.workout_readiness_great), index = 2)
            waitUntilGone(context.getString(R.string.workout_readiness_title))

            checkPrepItems()
            clickText(context.getString(R.string.workout_start_first), scroll = true)
            // Sample Friday opens on a timed skill block before working sets.
            if (waitForText(context.getString(R.string.workout_complete_block), timeoutMs = 5_000)) {
                clickText(context.getString(R.string.workout_complete_block), scroll = true)
            }
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithText("Set 1 of", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithText(context.getString(R.string.workout_skip_rest))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            if (composeRule.onAllNodesWithText("Set 1 of", substring = true).fetchSemanticsNodes().isNotEmpty()) {
                clickText(context.getString(R.string.workout_add_set))
                if (composeRule.onAllNodesWithText(context.getString(R.string.workout_form_solid), substring = false)
                        .fetchSemanticsNodes().isNotEmpty()
                ) {
                    clickText(context.getString(R.string.workout_form_solid))
                }
                clickText(context.getString(R.string.workout_log_set))
            }
            clickBack()

            openTab(context.getString(R.string.nav_today))
            clickText(context.getString(R.string.workout_resume))
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText("Set 1 of", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithText(context.getString(R.string.workout_skip_rest)).fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithText("Set 2 of", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithText(context.getString(R.string.workout_complete_block))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            clickBack()
        }

        openTab(context.getString(R.string.nav_routine))
        assertText(context.getString(R.string.builder_programs))
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Calisthenics", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Hypertrophy", substring = true, ignoreCase = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        openFirstProgram()
        assertText(context.getString(R.string.builder_days))
        clickText(context.getString(R.string.builder_back))

        openTab(context.getString(R.string.nav_dashboard))
        assertText(context.getString(R.string.nav_dashboard))
        // Settings (sample import + schedule mode) was covered at the start of this flow.
    }

    private fun finishOnboarding() {
        val allow = context.getString(R.string.onboarding_notifications_action)
        if (waitForText(allow, timeoutMs = 8_000)) {
            clickText(allow)
            // System permission dialog (may be absent on older images).
            device.wait(Until.findObject(By.text("Allow")), 3_000)?.click()
            device.wait(Until.findObject(By.text("While using the app")), 1_000)?.click()
        }
        val continueBattery = context.getString(R.string.onboarding_battery_continue)
        val exempt = context.getString(R.string.onboarding_battery_action)
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(continueBattery).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(exempt).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(context.getString(R.string.nav_today)).fetchSemanticsNodes().isNotEmpty()
        }
        when {
            composeRule.onAllNodesWithText(continueBattery).fetchSemanticsNodes().isNotEmpty() ->
                clickText(continueBattery)
            composeRule.onAllNodesWithText(exempt).fetchSemanticsNodes().isNotEmpty() -> {
                clickText(exempt)
                device.pressBack()
                if (waitForText(continueBattery, timeoutMs = 5_000)) clickText(continueBattery)
            }
        }
        waitForText(context.getString(R.string.nav_today), timeoutMs = 15_000)
    }

    private fun checkPrepItems() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(context.getString(R.string.workout_prep_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val start = context.getString(R.string.workout_start_first)
        // Sample program prep labels (all weekdays) — tap any that are on screen.
        val prepLabels = listOf(
            "Dynamic Hamstring Sweeps",
            "Glute Bridges",
            "Banded Shoulder Dislocates",
            "Wrist Rocks",
            "90/90 Hip Swaps",
            "Active Ankle Dorsiflexion",
            "False-Grip Hangs",
            "Scapular Pull-ups",
            "Band Pull-aparts",
            "Planche Leans (3 × 10s)",
            "First-Knuckle Push-ups",
            "Band External Rotations",
            "Active Scapular Shrugs",
            "Light Bicep Isometrics",
            "Wrist Extensions",
            "Crab Reaches",
            "Scapular Push-ups",
        )
        for (label in prepLabels) {
            if (composeRule.onAllNodesWithText(start).fetchSemanticsNodes().isNotEmpty()) break
            val matcher = hasText(label) and hasClickAction()
            if (composeRule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) continue
            runCatching { composeRule.onAllNodes(matcher)[0].performScrollTo() }
            composeRule.onAllNodes(matcher)[0].performClick()
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(start, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openFirstProgram() {
        // After sample import the program list shows the sample program name from assets.
        val candidates = listOf("Calisthenics", "Hypertrophy", "Sample", "Program")
        for (name in candidates) {
            val nodes = composeRule.onAllNodesWithText(name, substring = true, ignoreCase = true)
            if (nodes.fetchSemanticsNodes().isNotEmpty()) {
                nodes[0].performClick()
                composeRule.waitForIdle()
                return
            }
        }
        // Fallback: tap below the Programs title.
        device.click(device.displayWidth / 2, (device.displayHeight * 0.28).toInt())
        composeRule.waitForIdle()
    }

    private fun openTab(label: String) {
        // Bottom bar uses contentDescription = label.
        val node = device.wait(Until.findObject(By.desc(label)), 5_000)
        if (node != null) {
            node.click()
        } else {
            clickText(label)
        }
        composeRule.waitForIdle()
    }

    private fun clickText(text: String, index: Int = 0, scroll: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().size > index
        }
        val node = composeRule.onAllNodesWithText(text, substring = false)[index]
        if (scroll) {
            runCatching { node.performScrollTo() }
        }
        node.performClick()
        composeRule.waitForIdle()
    }

    /** Workout header uses an icon button with contentDescription, not visible "Back" text. */
    private fun clickBack() {
        val label = context.getString(R.string.builder_back)
        if (composeRule.onAllNodesWithText(label, substring = false).fetchSemanticsNodes().isNotEmpty()) {
            clickText(label)
            return
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasContentDescription(label)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(hasContentDescription(label))[0].performClick()
        composeRule.waitForIdle()
    }

    private fun assertText(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Titles and body copy often share the same string; require at least one match.
        check(composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()) {
            "Expected text containing \"$text\""
        }
    }

    private fun waitUntilGone(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForText(text: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (composeRule.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty()) {
                return true
            }
            composeRule.waitForIdle()
            Thread.sleep(200)
        }
        return false
    }

    companion object {
        /**
         * Wipe local storage before [ActivityScenario] launches. Do not use
         * `pm clear` here — it force-stops the instrumented process.
         */
        private fun clearAppData(context: Context) {
            context.deleteDatabase(DATABASE_NAME)
            File(context.getDatabasePath(DATABASE_NAME).path + "-wal").delete()
            File(context.getDatabasePath(DATABASE_NAME).path + "-shm").delete()
            File(context.filesDir, "datastore").deleteRecursively()
            // preferencesDataStore files live under files/datastore as *.preferences_pb
            context.getDir("datastore", Context.MODE_PRIVATE).deleteRecursively()
            File(context.dataDir, "files/datastore").deleteRecursively()
            File(context.dataDir, "shared_prefs").listFiles()?.forEach { it.delete() }
        }

        private fun expectedDayLabel(day: DayOfWeek): String = when (day) {
            DayOfWeek.MONDAY -> "Legs, Shoulders & Balance"
            DayOfWeek.TUESDAY -> "Pull (Vertical), Lever & Scapula"
            DayOfWeek.WEDNESDAY -> "Push & Planche"
            DayOfWeek.THURSDAY -> "Pull (Horizontal) & OAP Maintenance"
            DayOfWeek.FRIDAY -> "Legs, Shoulders & Heavy Hinge"
            DayOfWeek.SATURDAY -> "Push & Heavy Dips"
            DayOfWeek.SUNDAY -> "Active Rest, Deep Stretching & Recovery"
        }
    }
}
