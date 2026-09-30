package com.forge.hypertrophy.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class TrainingPreferencesRepositoryTest {
    @Test
    fun unsetTransitionRestIs120AndEachFieldRoundTrips() = runBlocking {
        val repository = DataStoreTrainingPreferencesRepository(
            ApplicationProvider.getApplicationContext(),
        )

        assertEquals(120, repository.transitionRestSeconds.first())
        assertNull(repository.lastReconciledDate.first())
        assertEquals(emptyList<Double>(), repository.plateInventoryKg.first())
        assertNull(repository.activeTimerEndElapsedRealtime.first())

        val date = LocalDate.of(2026, 4, 3)
        repository.setLastReconciledDate(date)
        assertEquals(date, repository.lastReconciledDate.first())

        val plates = listOf(25.0, 20.0, 1.25)
        repository.setPlateInventoryKg(plates)
        assertEquals(plates, repository.plateInventoryKg.first())

        repository.setTransitionRestSeconds(90)
        assertEquals(90, repository.transitionRestSeconds.first())

        repository.setActiveTimerEndElapsedRealtime(4_200_000L)
        assertEquals(4_200_000L, repository.activeTimerEndElapsedRealtime.first())
    }
}
