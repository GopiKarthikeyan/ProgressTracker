package com.forge.hypertrophy.ui.screens.cardio

import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.cardio.CardioServiceController
import com.forge.hypertrophy.cardio.CardioTracker
import com.forge.hypertrophy.data.dao.CompletedSessionDay
import com.forge.hypertrophy.data.dao.CompletedSetRow
import com.forge.hypertrophy.data.dao.GearMileage
import com.forge.hypertrophy.data.entity.CardioLogEntity
import com.forge.hypertrophy.data.entity.GearEntity
import com.forge.hypertrophy.data.entity.SessionSlotEntity
import com.forge.hypertrophy.data.entity.SetEntryEntity
import com.forge.hypertrophy.data.entity.TrackPointEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.CardioRepository
import com.forge.hypertrophy.data.repository.GearRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.data.weather.WeatherReading
import com.forge.hypertrophy.data.weather.WeatherRepository
import com.forge.hypertrophy.domain.model.CardioSource
import com.forge.hypertrophy.domain.model.CardioStyle
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.ui.screens.routine.awaitUntil
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardioViewModelTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val sessions = FakeSessionRepository()
    private val cardio = FakeCardioRepository()
    private val shoes = FakeGearRepository()
    private val activeViewModels = mutableListOf<CardioViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        activeViewModels.forEach { it.viewModelScope.cancel() }
        activeViewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun manualLogStoresDistanceDurationTypeAndShoe() = runBlocking {
        val shoe = shoes.insert(GearEntity(name = "daily", mileageLimitM = 700_000, archivedAt = null))
        val viewModel = cardio()
        awaitUntil { viewModel.uiState.value.gear.size == 1 }

        viewModel.onEvent(CardioEvent.Distance("5.2"))
        viewModel.onEvent(CardioEvent.Minutes("30"))
        viewModel.onEvent(CardioEvent.Seconds("15"))
        viewModel.onEvent(CardioEvent.StyleChosen(CardioStyle.WALK))
        viewModel.onEvent(CardioEvent.GearChosen(shoe))
        viewModel.onEvent(CardioEvent.SaveManual)

        awaitUntil { viewModel.uiState.value.logs.size == 1 }
        val log = viewModel.uiState.value.logs.single()
        assertEquals(CardioSource.MANUAL, log.source)
        assertEquals(CardioStyle.WALK, log.style)
        assertEquals(5_200.0, log.distanceM, 0.001)
        assertEquals(1_815, log.durationSec)
        assertEquals("daily", log.gearName)
        assertEquals(5_200.0, viewModel.uiState.value.gear.single().usedM, 0.001)
    }

    @Test
    fun retirementAlertFiresAtTheConfiguredLimit() = runBlocking {
        val shoe = shoes.insert(GearEntity(name = "race", mileageLimitM = 1_000, archivedAt = null))
        val sessionId = sessions.insert(
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
        cardio.insert(
            CardioLogEntity(
                sessionId = sessionId,
                distanceM = 1_000.0,
                durationSec = 300,
                source = CardioSource.MANUAL,
                gearId = shoe,
                tempC = null,
                uvIndex = null,
                type = CardioStyle.JOG,
            ),
        )
        val viewModel = cardio()
        awaitUntil { viewModel.uiState.value.gear.singleOrNull()?.retired == true }
        assertEquals(1_000.0, viewModel.uiState.value.gear.single().usedM, 0.001)
    }

    @Test
    fun aGpsLogStaysGpsAfterAHandEdit() = runBlocking {
        val sessionId = sessions.insert(
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
        val logId = cardio.insert(
            CardioLogEntity(
                sessionId = sessionId,
                distanceM = 3_000.0,
                durationSec = 900,
                source = CardioSource.GPS,
                gearId = null,
                tempC = 18.0,
                uvIndex = 2.0,
                type = CardioStyle.JOG,
            ),
        )
        val viewModel = cardio()
        awaitUntil { viewModel.uiState.value.logs.size == 1 }

        viewModel.onEvent(CardioEvent.Edit(logId))
        awaitUntil { viewModel.uiState.value.editingId == logId }
        viewModel.onEvent(CardioEvent.Distance("3.5"))
        viewModel.onEvent(CardioEvent.StyleChosen(CardioStyle.INTERVALS))
        viewModel.onEvent(CardioEvent.SaveEdit)

        awaitUntil { viewModel.uiState.value.logs.single().distanceM == 3_500.0 }
        val stored = viewModel.uiState.value.logs.single()
        assertEquals(CardioSource.GPS, stored.source)
        assertEquals(CardioStyle.INTERVALS, stored.style)
        assertEquals(900, stored.durationSec)
        assertNull(viewModel.uiState.value.editingId)

        val row = cardio.getForSession(sessionId)
        assertEquals(18.0, row!!.tempC!!, 0.001)
        assertEquals(2.0, row.uvIndex!!, 0.001)
    }

    private fun cardio(): CardioViewModel {
        val vm = CardioViewModel(
            sessions = sessions,
            cardio = cardio,
            shoes = shoes,
            tracker = CardioTracker(sessions, cardio, QuietWeather(), clock),
            service = object : CardioServiceController {
                override fun start() = Unit
                override fun stop() = Unit
            },
            clock = clock,
        )
        activeViewModels.add(vm)
        return vm
    }

    private class QuietWeather : WeatherRepository {
        override suspend fun current(latitude: Double, longitude: Double): WeatherReading? = null
    }

    private class FakeSessionRepository : SessionRepository {
        val sessions = mutableMapOf<Long, WorkoutSessionEntity>()
        private val flow = MutableStateFlow(emptyList<WorkoutSessionEntity>())

        override fun observe(id: Long): Flow<WorkoutSessionEntity?> = flow.map { it.find { s -> s.id == id } }
        override fun observeInProgress(): Flow<List<WorkoutSessionEntity>> = flow.map { it.filter { s -> s.status == SessionStatus.IN_PROGRESS } }
        override suspend fun get(id: Long): WorkoutSessionEntity? = sessions[id]
        override suspend fun insert(session: WorkoutSessionEntity): Long {
            val id = (sessions.keys.maxOrNull() ?: 0L) + 1
            sessions[id] = session.copy(id = id)
            flow.value = sessions.values.toList()
            return id
        }
        override suspend fun update(session: WorkoutSessionEntity) {
            sessions[session.id] = session
            flow.value = sessions.values.toList()
        }
        override suspend fun delete(id: Long) {
            sessions.remove(id)
            flow.value = sessions.values.toList()
        }

        override fun observeSlots(sessionId: Long): Flow<List<SessionSlotEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertSlot(slot: SessionSlotEntity): Long = 0L
        override suspend fun updateSlot(slot: SessionSlotEntity) {}
        override fun observeSets(sessionSlotId: Long): Flow<List<SetEntryEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertSet(entry: SetEntryEntity): Long = 0L
        override suspend fun updateSet(entry: SetEntryEntity) {}
        override suspend fun deleteSet(id: Long) {}
        override suspend fun allSlots(): List<SessionSlotEntity> = emptyList()
        override suspend fun sets(sessionSlotId: Long): List<SetEntryEntity> = emptyList()
        override suspend fun completedDays(): List<CompletedSessionDay> = emptyList()
        override suspend fun completedSets(): List<CompletedSetRow> = emptyList()
        override suspend fun getSet(id: Long): SetEntryEntity? = null
        override suspend fun getSlot(id: Long): SessionSlotEntity? = null
        override suspend fun recentSetsForExercise(exerciseId: Long, limit: Int): List<SetEntryEntity> = emptyList()
        override suspend fun earliestCompletedDate(): LocalDate? = null
        override suspend fun history(): List<WorkoutSessionEntity> = sessions.values.toList()
    }

    private class FakeCardioRepository : CardioRepository {
        val logs = mutableMapOf<Long, CardioLogEntity>()
        private val flow = MutableStateFlow(emptyList<CardioLogEntity>())

        override fun observeLog(sessionId: Long): Flow<CardioLogEntity?> = flow.map { it.find { l -> l.sessionId == sessionId } }
        override suspend fun insert(log: CardioLogEntity): Long {
            val id = (logs.keys.maxOrNull() ?: 0L) + 1
            logs[id] = log.copy(id = id)
            flow.value = logs.values.toList()
            return id
        }
        override suspend fun update(log: CardioLogEntity) {
            logs[log.id] = log
            flow.value = logs.values.toList()
        }
        override suspend fun delete(id: Long) {
            logs.remove(id)
            flow.value = logs.values.toList()
        }
        override fun observeTrackPoints(cardioLogId: Long): Flow<List<TrackPointEntity>> = MutableStateFlow(emptyList())
        override suspend fun insertTrackPoints(points: List<TrackPointEntity>): List<Long> = emptyList()
        override fun observeAll(): Flow<List<CardioLogEntity>> = flow
        override fun observeMileage(): Flow<List<GearMileage>> = flow.map { list ->
            list.filter { it.gearId != null }
                .groupBy { it.gearId!! }
                .map { (gearId, logs) -> GearMileage(gearId, logs.sumOf { it.distanceM }) }
        }

        fun getForSession(sessionId: Long): CardioLogEntity? = logs.values.find { it.sessionId == sessionId }
    }

    private class FakeGearRepository : GearRepository {
        val gear = mutableMapOf<Long, GearEntity>()
        private val flow = MutableStateFlow(emptyList<GearEntity>())

        override fun observeActive(): Flow<List<GearEntity>> = flow.map { it.filter { g -> g.archivedAt == null } }
        override suspend fun get(id: Long): GearEntity? = gear[id]
        override suspend fun insert(entity: GearEntity): Long {
            val id = (gear.keys.maxOrNull() ?: 0L) + 1
            gear[id] = entity.copy(id = id)
            flow.value = gear.values.toList()
            return id
        }
        override suspend fun update(entity: GearEntity) {
            gear[entity.id] = entity
            flow.value = gear.values.toList()
        }
        override suspend fun archive(id: Long) {
            gear[id]?.let { update(it.copy(archivedAt = Instant.now())) }
        }
        override suspend fun delete(id: Long) {
            gear.remove(id)
            flow.value = gear.values.toList()
        }
    }
}
