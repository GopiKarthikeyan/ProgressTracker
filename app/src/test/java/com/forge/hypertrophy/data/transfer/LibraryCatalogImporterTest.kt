package com.forge.hypertrophy.data.transfer

import com.forge.hypertrophy.data.dao.DaoFixture
import com.forge.hypertrophy.data.dao.DaoTest
import com.forge.hypertrophy.data.entity.ExerciseEntity
import com.forge.hypertrophy.domain.model.Equipment
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogImporterTest : DaoTest() {
    @Test
    fun emptyDatabaseReceivesFullCatalog() = runBlocking {
        val catalog = loadCatalog()
        val result = RoomLibraryCatalogImporter(db).import(catalog)

        assertEquals(catalog.exercises.size, result.addedExercises)
        assertEquals(catalog.skills.size, result.addedSkills)
        assertEquals(catalog.exercises.size, db.exerciseDao().all().size)
        assertEquals(catalog.skills.size, db.skillDao().all().size)
        assertTrue(db.programDao().all().isEmpty())
    }

    @Test
    fun existingAndArchivedRowsStayUntouched() = runBlocking {
        val catalog = loadCatalog()
        val deadlift = catalog.exercises.first { it.name == "Barbell Deadlift" }
        db.exerciseDao().insert(
            ExerciseEntity(
                name = "Barbell Deadlift",
                equipment = Equipment.BARBELL,
                barWeightKg = 25.0,
                loadIncrementKg = 5.0,
                isUnilateral = false,
                skillId = null,
                primaryMuscleGroups = listOf("CUSTOM"),
                secondaryMuscleGroups = emptyList(),
                setupNotes = "mine",
                archivedAt = null,
            ),
        )
        val fixture = DaoFixture(db)
        val plancheId = fixture.skill("Planche", archivedAt = Instant.parse("2026-01-01T00:00:00Z"))
        fixture.step(plancheId, sortOrder = 0)

        val result = RoomLibraryCatalogImporter(db).import(catalog)

        assertEquals(catalog.exercises.size - 1, result.addedExercises)
        assertEquals(catalog.skills.size - 1, result.addedSkills)

        val stored = db.exerciseDao().all().first { it.name == "Barbell Deadlift" }
        assertEquals(25.0, stored.barWeightKg!!, 0.0)
        assertEquals(5.0, stored.loadIncrementKg, 0.0)
        assertEquals(listOf("CUSTOM"), stored.primaryMuscleGroups)
        assertEquals("mine", stored.setupNotes)

        val planche = db.skillDao().get(plancheId)!!
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), planche.archivedAt)
        assertEquals(1, db.skillDao().getSteps(plancheId).size)
        assertNull(db.exerciseDao().all().firstOrNull { it.name == deadlift.name && it.id != stored.id })
    }

    @Test
    fun secondRunAddsNothing() = runBlocking {
        val catalog = loadCatalog()
        val importer = RoomLibraryCatalogImporter(db)
        importer.import(catalog)
        val second = importer.import(catalog)

        assertEquals(0, second.addedExercises)
        assertEquals(0, second.addedSkills)
        assertEquals(catalog.exercises.size, db.exerciseDao().all().size)
        assertEquals(catalog.skills.size, db.skillDao().all().size)
    }

    @Test
    fun invalidDocumentWritesNothing() = runBlocking {
        val bad = loadCatalog().copy(schemaVersion = 2)
        val result = RoomLibraryCatalogImporter(db).import(bad)

        assertEquals(0, result.addedExercises)
        assertEquals(0, result.addedSkills)
        assertTrue(db.exerciseDao().all().isEmpty())
        assertTrue(db.skillDao().all().isEmpty())
    }

    private fun loadCatalog(): ProgramJson {
        var dir: File? = File(System.getProperty("user.dir")!!)
        while (dir != null) {
            val candidate = File(dir, "app/src/main/assets/library.json")
            if (candidate.isFile) {
                return AssetLibraryCatalogProvider.parse(candidate.readText())
            }
            dir = dir.parentFile
        }
        error("library.json not found")
    }
}
