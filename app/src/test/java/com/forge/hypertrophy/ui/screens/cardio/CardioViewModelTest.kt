package com.forge.hypertrophy.ui.screens.cardio

import com.forge.hypertrophy.cardio.CardioServiceController
import com.forge.hypertrophy.cardio.CardioTracker
import com.forge.hypertrophy.data.entity.CardioLogEntity
import com.forge.hypertrophy.data.entity.GearEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.RoomCardioRepository
import com.forge.hypertrophy.data.repository.RoomGearRepository
import com.forge.hypertrophy.data.repository.RoomSessionRepository
import com.forge.hypertrophy.data.weather.WeatherRepository
import com.forge.hypertrophy.data.weather.WeatherReading
import com.forge.hypertrophy.domain.model.CardioSource
import com.forge.hypertrophy.domain.model.CardioType
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.ui.screens.routine.ViewModelDaoTest
import com.forge.hypertrophy.ui.screens.routine.awaitUntil
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardioViewModelTest : ViewModelDaoTest() {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun manualLogStoresDistanceDurationTypeAndShoe() = runBlocking {
        val shoe = db.gearDao().insert(GearEntity(name = "daily", mileageLimitM = 700_000, archivedAt = null))
        val viewModel = cardio()
        awaitUntil { viewModel.uiState.value.gear.size == 1 }

        viewModel.onEvent(CardioEvent.Distance("5.2"))
        viewModel.onEvent(CardioEvent.Minutes("30"))
        viewModel.onEvent(CardioEvent.Seconds("15"))
        viewModel.onEvent(CardioEvent.TypeChosen(CardioType.WALK))
        viewModel.onEvent(CardioEvent.GearChosen(shoe))
        viewModel.onEvent(CardioEvent.SaveManual)

        awaitUntil { viewModel.uiState.value.logs.size == 1 }
        val log = viewModel.uiState.value.logs.single()
        assertEquals(CardioSource.MANUAL, log.source)
        assertEquals(CardioType.WALK, log.type)
        assertEquals(5_200.0, log.distanceM, 0.001)
        assertEquals(1_815, log.durationSec)
        assertEquals("daily", log.gearName)
        assertEquals(5_200.0, viewModel.uiState.value.gear.single().usedM, 0.001)
    }

    @Test
    fun retirementAlertFiresAtTheConfiguredLimit() = runBlocking {
        val shoe = db.gearDao().insert(GearEntity(name = "race", mileageLimitM = 1_000, archivedAt = null))
        val sessionId = db.sessionDao().insert(
            WorkoutSessionEntity(
                date = LocalDate.of(2026, 10, 1),
                dayId = null,
                kind = SessionKind.CARDIO,
                status = SessionStatus.COMPLETED,
                isDeload = false,
                isShortOnTime = false,
                readinessSleep = null,
                readinessSoreness = null,
                readinessEnergy = null,
                startedAt = null,
                completedAt = null,
            ),
        )
        db.cardioDao().insert(
            CardioLogEntity(
                sessionId = sessionId,
                distanceM = 1_000.0,
                durationSec = 300,
                source = CardioSource.MANUAL,
                gearId = shoe,
                tempC = null,
                uvIndex = null,
                type = CardioType.JOG,
            ),
        )
        val viewModel = cardio()
        awaitUntil { viewModel.uiState.value.gear.singleOrNull()?.retired == true }
        assertEquals(1_000.0, viewModel.uiState.value.gear.single().usedM, 0.001)
    }

    @Test
    fun aGpsLogStaysGpsAfterAHandEdit() = runBlocking {
        val sessionId = db.sessionDao().insert(
            WorkoutSessionEntity(
                date = LocalDate.of(2026, 10, 1),
                dayId = null,
                kind = SessionKind.CARDIO,
                status = SessionStatus.COMPLETED,
                isDeload = false,
                isShortOnTime = false,
                readinessSleep = null,
                readinessSoreness = null,
                readinessEnergy = null,
                startedAt = null,
                completedAt = null,
            ),
        )
        val logId = db.cardioDao().insert(
            CardioLogEntity(
                sessionId = sessionId,
                distanceM = 3_000.0,
                durationSec = 900,
                source = CardioSource.GPS,
                gearId = null,
                tempC = 18.0,
                uvIndex = 2.0,
                type = CardioType.JOG,
            ),
        )
        val viewModel = cardio()
        awaitUntil { viewModel.uiState.value.logs.size == 1 }

        viewModel.onEvent(CardioEvent.Edit(logId))
        awaitUntil { viewModel.uiState.value.editingId == logId }
        viewModel.onEvent(CardioEvent.Distance("3.5"))
        viewModel.onEvent(CardioEvent.TypeChosen(CardioType.INTERVALS))
        viewModel.onEvent(CardioEvent.SaveEdit)

        awaitUntil { viewModel.uiState.value.logs.single().distanceM == 3_500.0 }
        val stored = viewModel.uiState.value.logs.single()
        assertEquals(CardioSource.GPS, stored.source)
        assertEquals(CardioType.INTERVALS, stored.type)
        assertEquals(900, stored.durationSec)
        assertNull(viewModel.uiState.value.editingId)
        val row = first(db.cardioDao().observeLog(sessionId))
        assertEquals(18.0, row!!.tempC!!, 0.001)
        assertEquals(2.0, row.uvIndex!!, 0.001)
    }

    private fun cardio(): CardioViewModel {
        val sessions = RoomSessionRepository(db.sessionDao())
        val logs = RoomCardioRepository(db.cardioDao())
        return track(
            CardioViewModel(
                sessions = sessions,
                cardio = logs,
                shoes = RoomGearRepository(db.gearDao(), clock),
                tracker = CardioTracker(sessions, logs, QuietWeather(), clock),
                service = object : CardioServiceController {
                    override fun start() = Unit
                    override fun stop() = Unit
                },
                clock = clock,
            ),
        )
    }
}

private class QuietWeather : WeatherRepository {
    override suspend fun current(latitude: Double, longitude: Double): WeatherReading? = null
}
