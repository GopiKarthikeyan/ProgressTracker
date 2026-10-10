package com.forge.hypertrophy.data.transfer

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.ScheduleMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class LibraryCatalogTest {
    @Test
    fun catalogParsesAndValidates() {
        val json = catalogJson()
        val catalog = AssetLibraryCatalogProvider.parse(json)

        assertEquals(10, catalog.skills.size)
        assertEquals(146, catalog.exercises.size)
        assertTrue(catalog.days.isEmpty())

        val report = ProgramJsonValidator.validate(catalog, ScheduleMode.FIXED)
        assertTrue(report.errors.toString(), report.errors.isEmpty())

        val skillKeys = catalog.skills.map { it.key }.toSet()
        catalog.exercises.forEach { exercise ->
            Equipment.valueOf(exercise.equipment)
            exercise.skillKey?.let { key ->
                assertTrue("dangling skillKey $key on ${exercise.name}", key in skillKeys)
            }
            (exercise.primaryMuscleGroups + exercise.secondaryMuscleGroups).forEach { muscle ->
                assertTrue("unknown muscle $muscle on ${exercise.name}", muscle in KNOWN_MUSCLES)
            }
        }
        catalog.skills.forEach { skill ->
            assertTrue("${skill.name} needs at least two steps", skill.steps.size >= 2)
            val orders = skill.steps.map { it.order }
            assertEquals(orders.sorted(), orders)
            assertTrue(skill.initialStepKey in skill.steps.map { it.key })
        }
    }

    private fun catalogJson(): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fromAsset = runCatching {
            context.assets.open(AssetLibraryCatalogProvider.ASSET_NAME).bufferedReader().use { it.readText() }
        }
        return fromAsset.getOrElse {
            var dir: File? = File(System.getProperty("user.dir")!!)
            while (dir != null) {
                val candidate = File(dir, "app/src/main/assets/library.json")
                if (candidate.isFile) return@getOrElse candidate.readText()
                dir = dir.parentFile
            }
            error("library.json not found")
        }
    }

    private companion object {
        val KNOWN_MUSCLES = setOf(
            "CHEST",
            "BACK_LATS",
            "BACK_UPPER",
            "SHOULDERS_FRONT",
            "SHOULDERS_SIDE",
            "SHOULDERS_REAR",
            "BICEPS",
            "TRICEPS",
            "FOREARMS",
            "QUADS",
            "HAMSTRINGS",
            "GLUTES",
            "CALVES",
            "ADDUCTORS",
            "LOWER_BACK",
            "CORE",
        )
    }
}
