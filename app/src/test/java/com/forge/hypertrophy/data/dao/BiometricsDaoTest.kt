package com.forge.hypertrophy.data.dao

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricsDaoTest : DaoTest() {
    @Test
    fun upsertReplacesSameDateAndInsertsAnother() = runBlocking {
        val fixture = DaoFixture(db)
        val today = LocalDate.of(2026, 4, 1)
        val tomorrow = today.plusDays(1)
        fixture.biometrics(today, 80.0)

        fixture.biometrics(today, 81.5)
        fixture.biometrics(tomorrow, 70.0)

        val rows = first(db.biometricsDao().observeAll())
        assertEquals(2, rows.size)
        assertEquals(81.5, db.biometricsDao().get(today)!!.bodyWeightKg)
        assertEquals(70.0, db.biometricsDao().get(tomorrow)!!.bodyWeightKg)
        assertEquals(listOf(tomorrow, today), rows.map { it.date })
    }
}
